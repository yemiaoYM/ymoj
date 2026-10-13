package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JDK 17 HTTP 客户端。关闭重定向，限制完整传输及响应大小，不传播远端响应正文。
 * 取消 HTTP/1.1 请求会关闭连接，上游将请求 Context 传递给 Worker 以回收整个进程组。
 */
@Component
public class GoJudgeClient implements AutoCloseable {

    private static final Set<String> RESULT_STATUSES = Set.of("Accepted", "Memory Limit Exceeded",
            "Time Limit Exceeded", "Output Limit Exceeded", "File Error", "Nonzero Exit Status", "Signalled");

    private final GoJudgeProperties properties;
    private final ObjectMapper mapper;
    private final URI base;
    private final HttpClient http;
    private final ExecutorService uploadReaders;
    private final Set<Future<?>> pending = ConcurrentHashMap.newKeySet();
    private final Set<InputStream> uploads = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public GoJudgeClient(GoJudgeProperties properties, ObjectMapper mapper) {
        properties.validate();
        this.properties = properties;
        this.mapper = mapper;
        this.base = validateBase(properties.getBaseUrl());
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        this.uploadReaders = Executors.newFixedThreadPool(properties.getSandboxConcurrency(), runnable -> {
            Thread thread = new Thread(runnable, "go-judge-upload-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public List<JsonNode> execute(Map<String, Object> command, long deadlineEpochMillis) throws GoJudgeException {
        long deadline = requestDeadline(deadlineEpochMillis);
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(command);
        } catch (IOException | RuntimeException e) {
            throw new GoJudgeException("go-judge 执行请求编码失败");
        }
        if (body.length > properties.getMaxRequestBytes()) {
            throw new GoJudgeException("go-judge 执行请求超过传输上限");
        }
        JsonNode result = parse(request("POST", "/run", HttpRequest.BodyPublishers.ofByteArray(body),
                "application/json", deadline, true, 200));
        Object commands = command.get("cmd");
        if (!(commands instanceof List<?> requested) || !result.isArray() || result.isEmpty()
                || result.size() != requested.size()) {
            throw new GoJudgeException("go-judge 执行响应结构无效");
        }
        List<JsonNode> results = new ArrayList<>(result.size());
        for (JsonNode entry : result) {
            if (!entry.isObject() || !RESULT_STATUSES.contains(entry.path("status").asText())) {
                throw new GoJudgeException("go-judge 返回未知状态或内部错误");
            }
            for (String field : List.of("time", "runTime", "memory")) {
                JsonNode number = entry.path(field);
                if (!number.isIntegralNumber() || !number.canConvertToLong() || number.longValue() < 0) {
                    throw new GoJudgeException("go-judge 资源统计字段无效");
                }
            }
            if (!entry.path("exitStatus").isIntegralNumber() || !entry.path("exitStatus").canConvertToInt()) {
                throw new GoJudgeException("go-judge 退出状态字段无效");
            }
            validateFiles(entry.path("files"), false);
            validateFiles(entry.path("fileIds"), true);
            results.add(entry);
        }
        return results;
    }

    public String upload(InputStream input, String name, long deadlineEpochMillis) throws GoJudgeException {
        if (input == null || name == null || !name.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new GoJudgeException("go-judge 上传文件参数无效");
        }
        long deadline = requestDeadline(deadlineEpochMillis);
        uploads.add(input);
        byte[] content;
        try {
            Future<byte[]> read = uploadReaders.submit(() -> readUpload(input, deadline));
            content = await(read, deadline);
        } catch (RuntimeException e) {
            throw new GoJudgeException("go-judge 上传读取已停止");
        } finally {
            uploads.remove(input);
            closeQuietly(input);
        }
        String boundary = "ymcloud-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        if ((long) prefix.length + content.length + suffix.length > properties.getMaxRequestBytes()) {
            throw new GoJudgeCapacityException("输入文件超过当前评测部署的传输容量");
        }
        JsonNode result = parse(request("POST", "/file", HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(prefix), HttpRequest.BodyPublishers.ofByteArray(content),
                HttpRequest.BodyPublishers.ofByteArray(suffix)), "multipart/form-data; boundary=" + boundary,
                deadline, true, 200));
        if (!result.isTextual() || !validFileId(result.textValue())) {
            throw new GoJudgeException("go-judge 上传响应文件 ID 无效");
        }
        return result.textValue();
    }

    public void delete(String fileId, long deadlineEpochMillis) throws GoJudgeException {
        if (!validFileId(fileId)) {
            throw new GoJudgeException("go-judge 文件 ID 无效");
        }
        request("DELETE", "/file/" + fileId, HttpRequest.BodyPublishers.noBody(), null,
                requestDeadline(deadlineEpochMillis), true, 200);
    }

    public JsonNode version(long deadlineEpochMillis) throws GoJudgeException {
        return getJson("/version", deadlineEpochMillis);
    }

    public JsonNode config(long deadlineEpochMillis) throws GoJudgeException {
        return getJson("/config", deadlineEpochMillis);
    }

    /**
     * /version 与 /config 在上游不受鉴权保护，因此必须对 /file 做正反两次检查。
     */
    public void verifyAuthentication(long deadlineEpochMillis) throws GoJudgeException {
        long deadline = requestDeadline(deadlineEpochMillis);
        request("GET", "/file", HttpRequest.BodyPublishers.noBody(), null, deadline, false, 401);
        JsonNode files = parse(request("GET", "/file", HttpRequest.BodyPublishers.noBody(), null,
                deadline, true, 200));
        if (!files.isObject()) {
            throw new GoJudgeException("go-judge 鉴权检查返回结构无效");
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pending.forEach(future -> future.cancel(true));
            uploads.forEach(GoJudgeClient::closeQuietly);
            uploadReaders.shutdownNow();
        }
    }

    private JsonNode getJson(String path, long deadlineEpochMillis) throws GoJudgeException {
        return parse(request("GET", path, HttpRequest.BodyPublishers.noBody(), null,
                requestDeadline(deadlineEpochMillis), true, 200));
    }

    private byte[] request(String method, String path, HttpRequest.BodyPublisher body, String contentType,
                           long deadline, boolean authenticated, int expectedStatus) throws GoJudgeException {
        checkActive(deadline);
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())))
                .header("Accept", "application/json")
                .method(method, body);
        if (authenticated) {
            builder.header("Authorization", "Bearer " + properties.getToken());
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        HttpResponse<byte[]> response;
        try {
            response = await(http.sendAsync(builder.build(), info -> new LimitedBodySubscriber(
                    properties.getMaxResponseBytes())), deadline);
        } catch (RuntimeException e) {
            throw new GoJudgeException("go-judge HTTP 请求未能发送");
        }
        if (response.statusCode() == 404) {
            throw new GoJudgeFileMissingException();
        }
        if (response.statusCode() != expectedStatus) {
            throw new GoJudgeException("go-judge HTTP 状态异常：" + response.statusCode());
        }
        return response.body();
    }

    private <T> T await(Future<T> future, long deadline) throws GoJudgeException {
        pending.add(future);
        try {
            while (true) {
                checkActive(deadline);
                try {
                    return future.get(Math.max(1, Math.min(TimeUnit.MILLISECONDS.toNanos(100),
                            deadline - System.nanoTime())), TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    // 在完整响应尚未结束时继续检查租约中断和任务截止，而非仅限制响应头。
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoJudgeException("go-judge 请求已被任务中断");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GoJudgeCapacityException capacity) {
                throw capacity;
            }
            // 不透传底层消息，JSON 解析和远端异常可能包含源码、数据或凭据。
            throw new GoJudgeException("go-judge 请求传输失败或超过响应上限");
        } catch (CancellationException e) {
            throw new GoJudgeException("go-judge 请求已被任务中断");
        } finally {
            pending.remove(future);
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private byte[] readUpload(InputStream input, long deadline) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            checkActive(deadline);
            if (output.size() > properties.getMaxFileBytes() - read) {
                throw new GoJudgeCapacityException("输入文件超过当前评测部署的单文件容量（上限 "
                        + properties.getMaxFileBytes() / 1024 + " KiB）");
            }
            output.write(buffer, 0, read);
        }
        checkActive(deadline);
        return output.toByteArray();
    }

    private JsonNode parse(byte[] content) throws GoJudgeException {
        try {
            JsonNode result = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content);
            if (result == null) {
                throw new GoJudgeException("go-judge 响应正文为空");
            }
            return result;
        } catch (IOException | RuntimeException e) {
            throw new GoJudgeException("go-judge 响应 JSON 无效");
        }
    }

    private long requestDeadline(long deadlineEpochMillis) throws GoJudgeException {
        long now = System.currentTimeMillis();
        if (deadlineEpochMillis <= now) {
            throw new GoJudgeException("go-judge 请求已超过任务截止时间");
        }
        long available = Math.min(properties.getRequestTimeout().toMillis(), deadlineEpochMillis - now);
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(available);
    }

    private void checkActive(long deadline) throws GoJudgeException {
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new GoJudgeException("go-judge 请求已被任务中断");
        }
        if (System.nanoTime() >= deadline) {
            throw new GoJudgeException("go-judge 请求已超过任务或传输截止时间");
        }
    }

    private static void validateFiles(JsonNode files, boolean identifiers) throws GoJudgeException {
        if (files.isMissingNode() || files.isNull()) {
            return;
        }
        if (!files.isObject()) {
            throw new GoJudgeException("go-judge 文件映射结构无效");
        }
        for (JsonNode value : files) {
            if (!value.isTextual() || (identifiers && !validFileId(value.textValue()))) {
                throw new GoJudgeException("go-judge 文件映射内容无效");
            }
        }
    }

    private static boolean validFileId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}");
    }

    private static URI validateBase(String configured) {
        try {
            URI uri = new URI(configured == null ? "" : configured);
            String host = uri.getHost();
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                    || "[::1]".equals(host) || "::1".equals(host);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme()) && loopback))
                    || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() > 65535 || (!uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("go-judge 地址必须使用 HTTPS，本机回环地址可使用 HTTP");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("go-judge 服务地址无效");
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // 取消时优先释放 HTTP 请求与任务，缓存 TTL 负责异常断连后的最后兜底。
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximum - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new GoJudgeException("go-judge 响应超过传输上限"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(new GoJudgeException("go-judge 响应传输失败"));
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}

package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.http.ServiceEndpoint;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: JDK 17 HTTP 客户端。关闭重定向，限制完整传输及响应大小，不传播远端响应正文。
 * 取消 HTTP/1.1 请求会关闭连接，上游将请求 Context 传递给 Worker 以回收整个进程组。
 */
@Slf4j
@Component
public class GoJudgeClient implements AutoCloseable {

    private static final Set<String> RESULT_STATUSES = Set.of("Accepted",
            "Memory Limit Exceeded",
            "Time Limit Exceeded",
            "Output Limit Exceeded",
            "File Error",
            "Nonzero Exit Status",
            "Signalled");

    private final GoJudgeProperties properties;
    private final ObjectMapper mapper;
    private final ServiceEndpoint endpoint;
    private final HttpClient http;
    private final Set<Future<?>> pending = ConcurrentHashMap.newKeySet();
    private final Set<InputStream> uploads = ConcurrentHashMap.newKeySet();
    private final Set<InputStream> downloads = ConcurrentHashMap.newKeySet();
    private final ScheduledThreadPoolExecutor deadlines;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 构造时校验部署边界并创建 JDK HTTP 客户端，因此保留构造器注入。
     * @param properties 沙箱地址、身份、超时及容量配置
     * @param mapper 固定协议的 JSON 映射器
     */
    public GoJudgeClient(GoJudgeProperties properties, ObjectMapper mapper) {
        properties.validate();
        this.properties = properties;
        this.mapper = mapper;
        this.endpoint = new ServiceEndpoint(properties.getBaseUrl(), properties.isAllowPrivateNetworkHttp(), false);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(null))
                .connectTimeout(properties.getConnectTimeout())
                .build();
        this.deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "go-judge-file-deadline");
            thread.setDaemon(true);
            return thread;
        });
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    /**
     * 执行固定命令，排队及运行共用任务总截止，避免传输预算中断合法排队
     * 验证结构化响应，无效响应中的沙箱文件仍须回收
     * @param command 固定命令及资源限制
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 与请求命令一一对应的已验证响应
     * @throws GoJudgeException 请求超限、中断或沙箱响应非法
     */
    public List<JsonNode> execute(Map<String, Object> command, long deadlineEpochMillis) throws GoJudgeException {
        long deadline = taskDeadline(deadlineEpochMillis);
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
                "application/json", deadline, true, HttpURLConnection.HTTP_OK));
        try {
            return validateResults(command, result);
        } catch (GoJudgeException rejectedResponse) {
            // 响应尚未交给任务会话；即使判定或统计无效，也要回收已经返回的缓存文件。
            discardResponseFiles(result);
            throw rejectedResponse;
        }
    }

    /**
     * 拒绝数量、状态、资源统计或文件映射不合法的响应。
     * @param command 已发送的命令
     * @param result 沙箱返回的 JSON
     * @return 顺序与请求一致的响应列表
     * @throws GoJudgeException 响应不符合沙箱协议
     */
    private List<JsonNode> validateResults(Map<String, Object> command, JsonNode result) throws GoJudgeException {
        Object commands = command.get("cmd");
        if (!(commands instanceof List<?> requested)
                || !result.isArray()
                || result.isEmpty()
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

    /**
     * 在独立的两秒清理预算内回收无效响应的文件，保留任务中断信号。
     * @param result 尚未交给任务会话的响应
     */
    private void discardResponseFiles(JsonNode result) {
        Set<String> fileIds = new LinkedHashSet<>();
        if (result.isArray()) {
            for (JsonNode response : result) {
                collectFileIds(response, fileIds);
            }
        } else {
            collectFileIds(result, fileIds);
        }
        long deadline = System.currentTimeMillis() + 2000L;
        boolean interrupted = Thread.interrupted();
        try {
            for (String fileId : Set.copyOf(fileIds)) {
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                if (Thread.interrupted()) {
                    interrupted = true;
                }
                try {
                    delete(fileId, deadline);
                    fileIds.remove(fileId);
                } catch (GoJudgeFileMissingException absent) {
                    fileIds.remove(fileId);
                } catch (GoJudgeException unavailable) {
                    // 未知或无法访问的文件仍由 go-judge 文件 TTL 兜底，不覆盖原始系统故障。
                }
            }
            if (!fileIds.isEmpty()) {
                log.warn("无效沙箱响应的缓存文件未能全部清理 remaining={}", fileIds.size());
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 仅提取可安全用于删除路由的文件 ID，并按集合去重。
     * @param response 单个命令响应
     * @param fileIds 接收合法文件 ID 的集合
     */
    private static void collectFileIds(JsonNode response, Set<String> fileIds) {
        JsonNode files = response.path("fileIds");
        if (!files.isObject()) {
            return;
        }
        for (JsonNode fileId : files) {
            if (fileId.isTextual() && validFileId(fileId.textValue())) {
                fileIds.add(fileId.textValue());
            }
        }
    }

    /**
     * 流式上传输入或源码，仅使用 HTTP 客户端的有界缓冲；超限取消而不截断输入。
     * @param input 受信任数据源的流，调用结束时关闭
     * @param name 固定上传文件名，不接受路径
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 沙箱文件 ID，由任务会话负责清理
     * @throws GoJudgeException 超限、传输中断、未完整读取或返回 ID 非法
     */
    public String upload(InputStream input, String name, long deadlineEpochMillis) throws GoJudgeException {
        if (input == null || name == null || !name.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new GoJudgeException("go-judge 上传文件参数无效");
        }
        long deadline = requestDeadline(deadlineEpochMillis);
        String boundary = "ymcloud-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        long maximum = Math.min(properties.getMaxFileBytes(),
                properties.getMaxRequestBytes() - (long) prefix.length - suffix.length);
        UploadStream content = new UploadStream(input, maximum, deadline);
        AtomicBoolean supplied = new AtomicBoolean();
        uploads.add(content);
        try {
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofInputStream(() -> supplied.compareAndSet(false, true) ? content : null),
                    HttpRequest.BodyPublishers.ofByteArray(suffix));
            JsonNode result = parse(request("POST", "/file", body, "multipart/form-data; boundary=" + boundary,
                    deadline, true, HttpURLConnection.HTTP_OK));
            if (!result.isTextual() || !validFileId(result.textValue())) {
                throw new GoJudgeException("go-judge 上传响应文件 ID 无效");
            }
            if (!content.complete) {
                // 远端提前响应也不能把部分输入用于判题；已创建文件按无效响应回收。
                discardResponseFiles(mapper.valueToTree(Map.of("fileIds", Map.of(name, result.textValue()))));
                throw new GoJudgeException("go-judge 上传未完整读取输入");
            }
            return result.textValue();
        } catch (GoJudgeException e) {
            if (content.failure instanceof GoJudgeCapacityException capacity) {
                throw capacity;
            }
            throw e;
        } finally {
            uploads.remove(content);
            closeQuietly(content);
        }
    }

    /**
     * 按沙箱文件 ID 删除缓存，禁止 ID 中包含路径片段。
     * @param fileId 沙箱返回的文件 ID
     * @param deadlineEpochMillis 任务或清理截止时间，UTC epoch 毫秒
     * @throws GoJudgeException ID 非法、文件不存在或请求失败
     */
    public void delete(String fileId, long deadlineEpochMillis) throws GoJudgeException {
        if (!validFileId(fileId)) {
            throw new GoJudgeException("go-judge 文件 ID 无效");
        }
        request("DELETE", "/file/" + fileId, HttpRequest.BodyPublishers.noBody(), null,
                requestDeadline(deadlineEpochMillis), true, HttpURLConnection.HTTP_OK);
    }

    /**
     * 流式读取沙箱缓存文件，文件内容不经过 JSON 或整段内存缓冲
     * @param fileId 沙箱返回的文件 ID
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 受完整传输截止和单文件容量约束的流，由调用方关闭
     * @throws GoJudgeException 文件不存在、容量超限、请求失败或已中断
     */
    public InputStream openFile(String fileId, long deadlineEpochMillis) throws GoJudgeException {
        if (!validFileId(fileId)) {
            throw new GoJudgeException("go-judge 文件 ID 无效");
        }
        long deadline = requestDeadline(deadlineEpochMillis);
        HttpRequest request = requestBuilder("GET", "/file/" + fileId, HttpRequest.BodyPublishers.noBody(),
                null, deadline, true).setHeader("Accept", "application/octet-stream").build();
        CompletableFuture<HttpResponse<InputStream>> future = http.sendAsync(request,
                HttpResponse.BodyHandlers.ofInputStream());
        boolean opened = false;
        try {
            HttpResponse<InputStream> response = await(future, deadline);
            if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new GoJudgeFileMissingException();
            }
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new GoJudgeException("go-judge HTTP 状态异常：" + response.statusCode());
            }
            if (response.headers().firstValueAsLong("Content-Length").orElse(-1) > properties.getMaxFileBytes()) {
                throw new GoJudgeException("go-judge 下载文件超过单文件容量上限");
            }
            InputStream input = new DownloadStream(response.body(), deadline);
            opened = true;
            return input;
        } finally {
            if (!opened) {
                // 响应头和取消可能同时完成，已产生的正文也必须关闭
                future.thenAccept(response -> closeQuietly(response.body()));
                future.cancel(true);
            }
        }
    }

    /**
     * 读取沙箱版本供启动检查使用。
     * @param deadlineEpochMillis 启动检查截止时间，UTC epoch 毫秒
     * @return 原始版本 JSON
     * @throws GoJudgeException 请求失败或响应格式非法
     */
    public JsonNode version(long deadlineEpochMillis) throws GoJudgeException {
        return getJson("/version", deadlineEpochMillis);
    }

    /**
     * 读取沙箱配置供隔离与容量检查使用。
     * @param deadlineEpochMillis 启动检查截止时间，UTC epoch 毫秒
     * @return 原始配置 JSON
     * @throws GoJudgeException 请求失败或响应格式非法
     */
    public JsonNode config(long deadlineEpochMillis) throws GoJudgeException {
        return getJson("/config", deadlineEpochMillis);
    }

    /**
     * /version 与 /config 在上游不受鉴权保护，因此必须对 /file 做正反两次检查。
     * @param deadlineEpochMillis 启动检查截止时间，UTC epoch 毫秒
     * @throws GoJudgeException 鉴权状态或文件接口响应异常
     */
    public void verifyAuthentication(long deadlineEpochMillis) throws GoJudgeException {
        long deadline = requestDeadline(deadlineEpochMillis);
        request("GET", "/file", HttpRequest.BodyPublishers.noBody(), null, deadline, false, HttpURLConnection.HTTP_UNAUTHORIZED);
        JsonNode files = parse(request("GET", "/file", HttpRequest.BodyPublishers.noBody(), null,
                deadline, true, HttpURLConnection.HTTP_OK));
        if (!files.isObject()) {
            throw new GoJudgeException("go-judge 鉴权检查返回结构无效");
        }
    }

    /**
     * 取消正在传输的请求并关闭上传源、下载正文及截止回调
     */
    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pending.forEach(future -> future.cancel(true));
            uploads.forEach(GoJudgeClient::closeQuietly);
            downloads.forEach(GoJudgeClient::closeQuietly);
            deadlines.shutdownNow();
        }
    }

    /**
     * 读取固定内部路径的 JSON，不接收外部任意 URL。
     * @param path 固定沙箱路径
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 非空 JSON 响应
     * @throws GoJudgeException 请求或解析失败
     */
    private JsonNode getJson(String path, long deadlineEpochMillis) throws GoJudgeException {
        return parse(request("GET", path, HttpRequest.BodyPublishers.noBody(), null,
                requestDeadline(deadlineEpochMillis), true, HttpURLConnection.HTTP_OK));
    }

    /**
     * 发送受限 HTTP 请求并核对真实状态码，不传播远端错误正文。
     * @param method HTTP 方法
     * @param path 固定沙箱路径
     * @param body 有界请求正文发布器
     * @param contentType 正文媒体类型，无正文时可为 null
     * @param deadline 基于 nanoTime 的截止时间
     * @param authenticated 是否携带独立服务令牌
     * @param expectedStatus 当前操作要求的 HTTP 状态码
     * @return 受响应容量限制的原始字节
     * @throws GoJudgeException 请求中断、超时、文件缺失或状态异常
     */
    private byte[] request(String method, String path, HttpRequest.BodyPublisher body, String contentType,
                           long deadline, boolean authenticated, int expectedStatus) throws GoJudgeException {
        HttpRequest.Builder builder = requestBuilder(method, path, body, contentType, deadline, authenticated);
        HttpResponse<byte[]> response;
        try {
            response = await(http.sendAsync(builder.build(), info -> new LimitedBodySubscriber(
                    properties.getMaxResponseBytes())), deadline);
        } catch (RuntimeException e) {
            throw new GoJudgeException("go-judge HTTP 请求未能发送");
        }
        if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new GoJudgeFileMissingException();
        }
        if (response.statusCode() != expectedStatus) {
            throw new GoJudgeException("go-judge HTTP 状态异常：" + response.statusCode());
        }
        return response.body();
    }

    /**
     * 为 JSON 与文件请求复用固定地址、鉴权及完整传输预算
     * @param method HTTP 方法
     * @param path 固定沙箱路径
     * @param body 请求正文
     * @param contentType 正文媒体类型，无正文时为 null
     * @param deadline 基于 nanoTime 的截止时间
     * @param authenticated 是否携带独立服务令牌
     * @return 已设置安全边界的请求构造器
     * @throws GoJudgeException 服务地址无效、任务中断或已到期
     */
    private HttpRequest.Builder requestBuilder(String method, String path, HttpRequest.BodyPublisher body,
                                               String contentType, long deadline, boolean authenticated)
            throws GoJudgeException {
        checkActive(deadline);
        URI target;
        try {
            target = endpoint.requestUri(path, Math.min(properties.getConnectTimeout().toNanos(),
                    deadline - System.nanoTime()));
        } catch (IOException e) {
            throw new GoJudgeException("go-judge 服务地址解析未通过私网限制");
        }
        checkActive(deadline);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())))
                .header("Accept", "application/json")
                .method(method, body);
        if (authenticated) {
            builder.header("Authorization", "Bearer " + properties.getToken());
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return builder;
    }

    /**
     * 等待异步请求并响应任务中断，退出时取消未完成的请求。
     * @param <T> 请求完成值的类型
     * @param future 当前 HTTP 请求
     * @param deadline 基于 nanoTime 的截止时间
     * @return 请求正常完成的结果
     * @throws GoJudgeException 任务中断、超时或传输失败
     */
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

    /**
     * 只接受一份非空 JSON，解析失败不附带私有正文。
     * @param content 受传输容量限制的响应字节
     * @return 非空 JSON 节点
     * @throws GoJudgeException 空响应、尾随数据或格式非法
     */
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

    /**
     * 文件和探针请求同时受任务截止及独立传输预算约束
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 基于 nanoTime 的截止时间
     * @throws GoJudgeException 任务已经到期
     */
    private long requestDeadline(long deadlineEpochMillis) throws GoJudgeException {
        return Math.min(taskDeadline(deadlineEpochMillis),
                System.nanoTime() + properties.getRequestTimeout().toNanos());
    }

    /**
     * 将任务剩余总预算转换为单调时钟截止
     * @param deadlineEpochMillis 任务截止时间，UTC epoch 毫秒
     * @return 基于 nanoTime 的截止时间
     * @throws GoJudgeException 任务已经到期
     */
    private long taskDeadline(long deadlineEpochMillis) throws GoJudgeException {
        long now = System.currentTimeMillis();
        if (deadlineEpochMillis <= now) {
            throw new GoJudgeException("go-judge 请求已超过任务截止时间");
        }
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineEpochMillis - now);
    }

    /**
     * 禁止客户端关闭、任务中断或到期后继续请求。
     * @param deadline 基于 nanoTime 的截止时间
     * @throws GoJudgeException 客户端已停止或时间预算耗尽
     */
    private void checkActive(long deadline) throws GoJudgeException {
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new GoJudgeException("go-judge 请求已被任务中断");
        }
        if (System.nanoTime() >= deadline) {
            throw new GoJudgeException("go-judge 请求已超过任务或传输截止时间");
        }
    }

    /**
     * 校验输出文本映射或缓存文件 ID 映射，不接受任意结构。
     * @param files 可缺失或为 null 的文件映射
     * @param identifiers 是否按缓存文件 ID 校验
     * @throws GoJudgeException 字段类型或文件 ID 非法
     */
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

    /**
     * 限制沙箱文件 ID 的字符和长度，防止进入任意文件路由。
     * @param value 待检查的 ID，可为 null
     * @return 是否为合法沙箱文件 ID
     */
    private static boolean validFileId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}");
    }

    /**
     * 在取消和清理边界释放输入，不用关闭异常替换原始失败。
     * @param input 需要关闭的上传源或下载正文
     */
    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // 取消时优先释放 HTTP 请求与任务，缓存 TTL 负责异常断连后的最后兜底。
        }
    }

    /**
     * 为缓存文件下载限制实际字节数，并在正文阻塞时主动关闭连接
     */
    private final class DownloadStream extends FilterInputStream {

        private final long deadline;
        private final ScheduledFuture<?> timer;
        private final AtomicBoolean released = new AtomicBoolean();
        private long count;

        /**
         * 登记下载正文及完整请求截止回调
         * @param input JDK 可关闭的响应正文
         * @param deadline 基于 nanoTime 的完整请求截止时间
         * @throws GoJudgeException 客户端已停止或截止回调无法注册
         */
        private DownloadStream(InputStream input, long deadline) throws GoJudgeException {
            super(input);
            this.deadline = deadline;
            downloads.add(input);
            try {
                this.timer = deadlines.schedule(() -> closeQuietly(input),
                        Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (RuntimeException e) {
                downloads.remove(input);
                closeQuietly(input);
                throw new GoJudgeException("go-judge 文件客户端已停止");
            }
        }

        /**
         * 单字节读取也执行相同的容量与截止检查
         * @return 无符号字节，EOF 为 -1
         * @throws IOException 正文读取失败、超限或请求已停止
         */
        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(single[0]);
        }

        /**
         * 有界读取原始字节，禁止将超限文件截断后误判为完整答案
         * @param buffer 接收正文的缓冲
         * @param offset 写入起点
         * @param length 最多读取字节数
         * @return 实际字节数，EOF 为 -1
         * @throws IOException 超限、超时、中断或网络正文不完整
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkActive(deadline);
            if (released.get()) {
                throw new GoJudgeException("go-judge 下载文件已经关闭");
            }
            int read;
            try {
                read = in.read(buffer, offset, (int) Math.min(length, properties.getMaxFileBytes() - count + 1));
            } catch (IOException e) {
                if (e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new GoJudgeException("go-judge 文件读取失败、中断或超时");
            }
            checkActive(deadline);
            if (read > 0) {
                count += read;
                if (count > properties.getMaxFileBytes()) {
                    throw new GoJudgeException("go-judge 下载文件超过单文件容量上限");
                }
            }
            return read;
        }

        /**
         * 跳过数据仍按实际字节执行容量及截止检查
         * @param amount 请求跳过的字节数
         * @return 实际跳过的字节数
         * @throws IOException 正文读取失败或请求已停止
         */
        @Override
        public long skip(long amount) throws IOException {
            byte[] buffer = new byte[8192];
            long skipped = 0;
            while (skipped < amount) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, amount - skipped));
                if (read < 0) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        /**
         * 幂等释放网络正文及截止回调
         * @throws IOException 底层正文关闭失败
         */
        @Override
        public void close() throws IOException {
            if (released.compareAndSet(false, true)) {
                timer.cancel(false);
                downloads.remove(in);
                in.close();
            }
        }
    }

    /**
     * 对上传流按实际字节计数，只有读到 EOF 并通过底层校验才算完整上传。
     */
    private final class UploadStream extends FilterInputStream {

        private final long maximum;
        private final long deadline;
        private long count;
        private volatile boolean complete;
        private volatile IOException failure;
        private final AtomicBoolean released = new AtomicBoolean();

        /**
         * 绑定一次上传的容量和传输截止时间。
         * @param input 原始输入流
         * @param maximum 文件及 multipart 总容量共同允许的字节数
         * @param deadline 基于 nanoTime 的请求截止时间
         */
        private UploadStream(InputStream input, long maximum, long deadline) {
            super(input);
            this.maximum = maximum;
            this.deadline = deadline;
        }

        /**
         * 按字节读取并执行相同的上传边界检查。
         * @return 无符号字节；EOF 返回 -1
         * @throws IOException 输入、容量或截止检查失败
         */
        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(single[0]);
        }

        /**
         * 使用发布器提供的缓冲读取，最多多读一个字节以识别超限。
         * @param buffer HTTP 发布器的缓冲
         * @param offset 写入起点
         * @param length 最大读取字节数
         * @return 实际字节数；完整校验结束后返回 -1
         * @throws IOException 输入损坏、文件超限或传输已停止
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                if (failure != null) {
                    throw failure;
                }
                checkActive(deadline);
                int read = in.read(buffer, offset, (int) Math.min(length, maximum - count + 1));
                checkActive(deadline);
                if (read < 0) {
                    complete = true;
                } else {
                    count += read;
                    if (count > maximum) {
                        throw new GoJudgeCapacityException("输入文件超过当前评测部署的上传容量（上限 "
                                + maximum / 1024 + " KiB）");
                    }
                }
                return read;
            } catch (IOException e) {
                failure = e;
                throw e;
            }
        }

        /**
         * 发布器和任务清理可能同时关闭上传，只释放一次底层流。
         * @throws IOException 底层资源关闭或完整性校验失败
         */
        @Override
        public void close() throws IOException {
            if (released.compareAndSet(false, true)) {
                in.close();
            }
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        /**
         * 创建有硬容量上限的响应订阅器。
         * @param maximum 响应最多允许的字节数
         */
        private LimitedBodySubscriber(int maximum) {
            this.maximum = maximum;
        }

        /**
         * 提供响应完成信号，正文超限或读取失败时异常完成。
         * @return 最终响应字节的异步结果
         */
        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        /**
         * 登记订阅并一次申请一批响应数据。
         * @param subscription HTTP 响应数据订阅
         */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        /**
         * 逐块累计有界响应，超限立即取消上游订阅。
         * @param buffers 本批响应缓冲区
         */
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

        /**
         * 将底层错误转换为安全传输错误，不向外暴露原始正文。
         * @param error 底层传输异常
         */
        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(new GoJudgeException("go-judge 响应传输失败"));
        }

        /**
         * 在完整响应接收后完成结果。
         */
        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}

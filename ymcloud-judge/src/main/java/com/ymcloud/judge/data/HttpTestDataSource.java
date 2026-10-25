package com.ymcloud.judge.data;

import com.ymcloud.judge.http.ServiceEndpoint;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 以独立身份流式读取测试数据，限制完整传输时间并支持租约中断。
 */
@Slf4j
@Component
public class HttpTestDataSource implements TestDataSource, AutoCloseable {

    public static final String TOKEN_HEADER = JudgeProtocol.SERVICE_TOKEN_HEADER;
    private static final String DATA_PREFIX = "/internal/judge-worker/problems/";

    private final ServiceEndpoint endpoint;
    private final String token;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxManifestBytes;
    private final HttpClient http;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final Set<InputStream> bodies = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor deadlines;

    /**
     * 构造时校验服务身份和容量并创建受限客户端，因此保留构造器注入。
     * @param properties Web 数据地址、服务令牌及超时配置
     * @param cacheProperties 清单容量配置
     */
    public HttpTestDataSource(WorkerDataProperties properties, TestDataCacheProperties cacheProperties) {
        if (properties == null || cacheProperties == null) {
            throw new IllegalArgumentException("Worker 数据接口配置缺失");
        }
        this.endpoint = new ServiceEndpoint(properties.getBaseUrl(), properties.isAllowPrivateNetworkHttp(), true);
        this.token = properties.getToken();
        if (token == null || token.length() < 32 || token.length() > 512
                || token.chars().anyMatch(character -> character <= 32 || character >= 127)) {
            throw new IllegalArgumentException("Worker 服务身份令牌必须至少为 32 个可打印字符");
        }
        this.connectTimeout = timeout(properties.getConnectTimeout(), Duration.ofMinutes(5));
        this.readTimeout = timeout(properties.getReadTimeout(), Duration.ofMinutes(30));
        this.maxManifestBytes = cacheProperties.getMaxManifestBytes();
        if (maxManifestBytes <= 0 || maxManifestBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Worker 清单大小限制无效");
        }
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(null))
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
        this.deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "worker-data-request-deadline");
            thread.setDaemon(true);
            return thread;
        });
        // 连续下载文件时及时移除取消的截止任务，不保留到原定超时时间。
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    /**
     * 读取有容量上限的原始清单，保留字节供固定版本哈希校验。
     * @param problemId 正数题目编号
     * @param versionId 正数版本编号
     * @return 原始清单字节
     * @throws IOException 请求失败、中断、超时或清单超限
     */
    @Override
    public byte[] fetchManifest(Long problemId, Long versionId) throws IOException {
        try (InputStream input = request(problemId, versionId, null)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() > maxManifestBytes - read) {
                    throw new IOException("服务端测试数据清单超过 Worker 限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /**
     * 打开固定版本的文件流，由调用方关闭并核对长度和哈希。
     * @param problemId 正数题目编号
     * @param versionId 正数版本编号
     * @param fileId 清单中的正数文件编号
     * @return 受完整传输截止时间约束的输入流
     * @throws IOException 请求失败、中断或超时
     */
    @Override
    public InputStream openFile(Long problemId, Long versionId, Long fileId) throws IOException {
        requireId(fileId);
        return request(problemId, versionId, fileId);
    }

    /**
     * 停止新请求，取消等待响应头的请求并关闭正在传输的正文。
     */
    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pending.forEach(future -> future.cancel(true));
            bodies.forEach(HttpTestDataSource::closeQuietly);
            deadlines.shutdownNow();
        }
    }

    /**
     * 仅访问固定内部路由；响应头和正文共用一次请求的总预算。
     * @param problemId 正数题目编号
     * @param versionId 正数版本编号
     * @param fileId 文件编号；null 表示读取清单
     * @return 调用方负责关闭的流
     * @throws IOException 地址、鉴权、容量或传输检查失败
     */
    private InputStream request(Long problemId, Long versionId, Long fileId) throws IOException {
        requireId(problemId);
        requireId(versionId);
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new IOException("Worker 数据客户端已经关闭");
        }
        String path = DATA_PREFIX + problemId + "/versions/" + versionId
                + (fileId == null ? "/manifest" : "/files/" + fileId);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeout);
        var target = endpoint.requestUri(path,
                TimeUnit.MILLISECONDS.toNanos(Math.min(connectTimeout, readTimeout)));
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())))
                .header(TOKEN_HEADER, token)
                .header("Accept", fileId == null ? "application/json" : "application/octet-stream")
                .GET()
                .build();
        DeadlineStream body = null;
        boolean opened = false;
        int status = -1;
        try {
            HttpResponse<InputStream> response = response(request, deadline);
            body = new DeadlineStream(response.body(), deadline);
            body.checkDeadline();
            status = response.statusCode();
            if (status != HttpURLConnection.HTTP_OK) {
                // 错误内容不写入日志或异常，避免响应中的配置及文件内容被传播。
                throw new IOException("测试数据接口返回 HTTP " + status);
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (fileId == null && contentLength > maxManifestBytes) {
                throw new IOException("服务端测试数据清单超过 Worker 限制");
            }
            opened = true;
            log.debug("Worker 测试数据接口响应 problem={} version={} file={} status={} bytes={}",
                    problemId, versionId, fileId, status, contentLength);
            return body;
        } catch (IOException e) {
            log.warn("Worker 测试数据接口请求失败 problem={} version={} file={} status={} cause={}",
                    problemId, versionId, fileId, status, e.getClass().getSimpleName());
            throw e;
        } finally {
            if (!opened && body != null) {
                body.close();
            }
        }
    }

    /**
     * 等待响应头；线程中断、客户端关闭和截止超时均取消底层 HTTP 请求。
     * @param request 已验证目标和身份的 GET 请求
     * @param deadline 基于 nanoTime 的完整请求截止时间
     * @return 流式响应；正文尚未全部读取
     * @throws IOException 请求已停止、超时或传输失败，不包含远端正文
     */
    private HttpResponse<InputStream> response(HttpRequest request, long deadline) throws IOException {
        CompletableFuture<HttpResponse<InputStream>> future = http.sendAsync(request,
                HttpResponse.BodyHandlers.ofInputStream());
        pending.add(future);
        boolean received = false;
        try {
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Worker 数据请求已经停止");
            }
            HttpResponse<InputStream> response = future.get(Math.max(1, deadline - System.nanoTime()),
                    TimeUnit.NANOSECONDS);
            received = true;
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Worker 数据请求被中断");
        } catch (TimeoutException e) {
            throw new SocketTimeoutException("读取测试数据接口超时");
        } catch (ExecutionException | CancellationException e) {
            throw new IOException("Worker 数据请求失败或已停止");
        } finally {
            pending.remove(future);
            if (!received) {
                // 中断与响应头完成可能同时发生，已产生的流也必须关闭。
                future.thenAccept(response -> closeQuietly(response.body()));
                future.cancel(true);
            }
        }
    }

    /**
     * 检查超时范围，拒绝零值和过大的阻塞预算。
     * @param duration 配置时长
     * @param maximum 该阶段允许的最长时长
     * @return 毫秒数
     */
    private static int timeout(Duration duration, Duration maximum) {
        if (duration == null || duration.isNegative() || duration.isZero()
                || duration.compareTo(maximum) > 0 || duration.toMillis() <= 0) {
            throw new IllegalArgumentException("Worker 数据接口超时配置无效");
        }
        return Math.toIntExact(duration.toMillis());
    }

    /**
     * 校验内部数据路由中的正数编号。
     * @param id 题目、版本或文件编号
     * @throws IOException 编号为空或非正数
     */
    private static void requireId(Long id) throws IOException {
        if (id == null || id <= 0) {
            throw new IOException("测试数据接口 ID 无效");
        }
    }

    /**
     * 在取消边界关闭响应，不用清理异常替换原始失败。
     * @param input 待释放的 HTTP 正文流
     */
    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // HTTP 正文不负责文件完整性校验；缓存层仅在完整下载校验后发布版本。
        }
    }

    private final class DeadlineStream extends FilterInputStream {

        private final ScheduledFuture<?> timer;
        private final AtomicBoolean expired = new AtomicBoolean();
        private final long deadline;
        private final AtomicBoolean released = new AtomicBoolean();

        /**
         * 注册正文及截止回调；无法注册时立即关闭响应。
         * @param input JDK 可取消的响应正文
         * @param deadline 完整请求截止时间
         * @throws IOException 客户端调度器已停止
         */
        private DeadlineStream(InputStream input, long deadline) throws IOException {
            super(input);
            this.deadline = deadline;
            bodies.add(input);
            try {
                this.timer = deadlines.schedule(() -> {
                    expired.set(true);
                    closeQuietly(input);
                }, Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (RuntimeException e) {
                bodies.remove(input);
                closeQuietly(input);
                throw new IOException("Worker 数据客户端已停止");
            }
        }

        /**
         * 读取单字节，并在读取前后校验截止时间。
         * @return 无符号字节；EOF 为 -1
         * @throws IOException 已停止、超时或读取失败
         */
        @Override
        public int read() throws IOException {
            checkDeadline();
            int read;
            try {
                read = in.read();
            } catch (IOException e) {
                throw readFailure(e);
            }
            checkDeadline();
            return read;
        }

        /**
         * 按调用方缓冲读取，JDK 流的中断信号向任务边界保留。
         * @param buffer 目标缓冲
         * @param offset 缓冲写入起点
         * @param length 最多读取字节数
         * @return 实际字节数；EOF 为 -1
         * @throws IOException 已停止、超时或读取失败
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkDeadline();
            int read;
            try {
                read = in.read(buffer, offset, length);
            } catch (IOException e) {
                throw readFailure(e);
            }
            checkDeadline();
            return read;
        }

        /**
         * 用受限读取跳过数据，仍执行截止与中断检查。
         * @param amount 请求跳过字节数
         * @return 实际跳过字节数
         * @throws IOException 读取失败
         */
        @Override
        public long skip(long amount) throws IOException {
            long skipped = 0;
            byte[] buffer = new byte[8192];
            while (skipped < amount) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, amount - skipped));
                if (read == -1) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        /**
         * 网络流不提供回退能力。
         * @return 始终为 false
         */
        @Override
        public boolean markSupported() {
            return false;
        }

        /**
         * 拒绝重置，避免把已消费的网络正文误当作可重复输入。
         * @throws IOException 始终拒绝该操作
         */
        @Override
        public void reset() throws IOException {
            throw new IOException("HTTP 测试数据流不支持 reset");
        }

        /**
         * 幂等取消截止回调并释放 HTTP 正文。
         * @throws IOException 关闭底层流失败
         */
        @Override
        public void close() throws IOException {
            if (released.compareAndSet(false, true)) {
                timer.cancel(false);
                bodies.remove(in);
                in.close();
            }
        }

        /**
         * 保留 JDK 流中断信号，不传播可能包含请求信息的底层异常正文。
         * @param failure 正文读取异常
         * @return 安全的读取失败异常
         */
        private IOException readFailure(IOException failure) {
            if (failure.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new IOException("Worker 测试数据读取失败、中断或超时");
        }

        /**
         * 拒绝关闭后、中断后或超过完整请求预算的读取。
         * @throws IOException 流已不可读
         */
        private void checkDeadline() throws IOException {
            if (released.get() || closed.get()) {
                throw new IOException("HTTP 测试数据流已经关闭");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("HTTP 测试数据请求被中断");
            }
            if (expired.get() || System.nanoTime() >= deadline) {
                throw new SocketTimeoutException("读取测试数据接口超时");
            }
        }
    }
}

package com.ymcloud.judge.data;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用独立服务身份读取 Web 的受限数据接口。文件流有完整请求截止时间，且从不跟随重定向。
 */
@Slf4j
@Component
public class HttpTestDataSource implements TestDataSource, AutoCloseable {

    public static final String TOKEN_HEADER = JudgeProtocol.SERVICE_TOKEN_HEADER;
    private static final String DATA_PREFIX = "/internal/judge-worker/problems/";

    private final URI base;
    private final String token;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxManifestBytes;
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor deadlines;

    public HttpTestDataSource(WorkerDataProperties properties, TestDataCacheProperties cacheProperties) {
        if (properties == null || cacheProperties == null) {
            throw new IllegalArgumentException("Worker 数据接口配置缺失");
        }
        this.base = validateBase(properties.getBaseUrl());
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
        this.deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "worker-data-request-deadline");
            thread.setDaemon(true);
            return thread;
        });
        // 连续下载文件时及时移除取消的截止任务，不保留到原定超时时间。
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

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

    @Override
    public InputStream openFile(Long problemId, Long versionId, Long fileId) throws IOException {
        requireId(fileId);
        return request(problemId, versionId, fileId);
    }

    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            connections.forEach(HttpURLConnection::disconnect);
            connections.clear();
            deadlines.shutdownNow();
        }
    }

    private InputStream request(Long problemId, Long versionId, Long fileId) throws IOException {
        requireId(problemId);
        requireId(versionId);
        if (closed.get()) {
            throw new IOException("Worker 数据客户端已经关闭");
        }
        String path = DATA_PREFIX + problemId + "/versions/" + versionId
                + (fileId == null ? "/manifest" : "/files/" + fileId);
        HttpURLConnection connection = (HttpURLConnection) URI.create(base.toString() + path)
                .toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setUseCaches(false);
        connection.setRequestProperty(TOKEN_HEADER, token);
        connection.setRequestProperty("Accept", fileId == null ? "application/json" : "application/octet-stream");
        connections.add(connection);
        if (closed.get()) {
            connections.remove(connection);
            connection.disconnect();
            throw new IOException("Worker 数据客户端已经关闭");
        }
        AtomicBoolean expired = new AtomicBoolean();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeout);
        ScheduledFuture<?> timer;
        try {
            timer = deadlines.schedule(() -> {
                expired.set(true);
                connection.disconnect();
            }, readTimeout, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            connections.remove(connection);
            connection.disconnect();
            throw new IOException("Worker 数据客户端已停止", e);
        }
        boolean opened = false;
        int status = -1;
        try {
            status = connection.getResponseCode();
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Worker 数据请求已经停止");
            }
            if (expired.get() || System.nanoTime() >= deadline) {
                throw new SocketTimeoutException("读取测试数据接口超时");
            }
            if (status != HttpURLConnection.HTTP_OK) {
                // 错误内容不写入日志或异常，避免响应中的配置及文件内容被传播。
                throw new IOException("测试数据接口返回 HTTP " + status);
            }
            if (fileId == null && connection.getContentLengthLong() > maxManifestBytes) {
                throw new IOException("服务端测试数据清单超过 Worker 限制");
            }
            InputStream body = connection.getInputStream();
            opened = true;
            log.debug("Worker 测试数据接口响应 problem={} version={} file={} status={} bytes={}",
                    problemId, versionId, fileId, status, connection.getContentLengthLong());
            return new DeadlineStream(body, connection, timer, expired, deadline);
        } catch (IOException e) {
            log.warn("Worker 测试数据接口请求失败 problem={} version={} file={} status={} cause={}",
                    problemId, versionId, fileId, status, e.getClass().getSimpleName());
            throw e;
        } finally {
            if (!opened) {
                timer.cancel(false);
                connections.remove(connection);
                connection.disconnect();
            }
        }
    }

    private static URI validateBase(String configured) {
        try {
            URI uri = new URI(configured == null ? "" : configured);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if ((!"http".equals(scheme) && !"https".equals(scheme)) || host == null || host.isBlank()
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() > 65535
                    || Objects.toString(uri.getRawPath(), "").contains("%") || !uri.normalize().equals(uri)
                    || ("http".equals(scheme) && !isLoopback(host))) {
                throw new IllegalArgumentException("Worker 数据接口必须使用 HTTPS，本机回环地址可使用 HTTP");
            }
            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return URI.create(normalized);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Worker 数据接口地址无效", e);
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    private static int timeout(Duration duration, Duration maximum) {
        if (duration == null || duration.isNegative() || duration.isZero()
                || duration.compareTo(maximum) > 0 || duration.toMillis() <= 0) {
            throw new IllegalArgumentException("Worker 数据接口超时配置无效");
        }
        return Math.toIntExact(duration.toMillis());
    }

    private static void requireId(Long id) throws IOException {
        if (id == null || id <= 0) {
            throw new IOException("测试数据接口 ID 无效");
        }
    }

    private final class DeadlineStream extends FilterInputStream {

        private final HttpURLConnection connection;
        private final ScheduledFuture<?> timer;
        private final AtomicBoolean expired;
        private final long deadline;
        private final AtomicBoolean released = new AtomicBoolean();

        private DeadlineStream(InputStream input, HttpURLConnection connection, ScheduledFuture<?> timer,
                               AtomicBoolean expired, long deadline) {
            super(input);
            this.connection = connection;
            this.timer = timer;
            this.expired = expired;
            this.deadline = deadline;
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            int read = in.read();
            checkDeadline();
            return read;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkDeadline();
            int read = in.read(buffer, offset, length);
            checkDeadline();
            return read;
        }

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

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void reset() throws IOException {
            throw new IOException("HTTP 测试数据流不支持 reset");
        }

        @Override
        public void close() throws IOException {
            if (released.compareAndSet(false, true)) {
                timer.cancel(false);
                connections.remove(connection);
                connection.disconnect();
                try {
                    in.close();
                } finally {
                    connection.disconnect();
                }
            }
        }

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

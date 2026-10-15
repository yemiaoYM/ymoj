package com.ymcloud.judge.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 服务地址默认只允许 HTTPS 或回环 HTTP；容器私网 HTTP 必须由部署显式开启。
 * HTTP 请求使用本次校验过的 IP，避免校验后再次解析域名时把服务令牌发送到其他地址。
 */
public final class ServiceEndpoint {

    // DNS 查询本身也受截止时间约束；不可中断的系统解析最多占用 32 个后台线程。
    private static final ThreadPoolExecutor RESOLVERS = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "worker-service-dns");
                thread.setDaemon(true);
                return thread;
            });
    private static final long DEFAULT_RESOLVE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final URI base;
    private final boolean allowPrivateNetworkHttp;
    private final AddressResolver resolver;

    public ServiceEndpoint(String configured, boolean allowPrivateNetworkHttp, boolean allowPathPrefix) {
        this(configured, allowPrivateNetworkHttp, allowPathPrefix, InetAddress::getAllByName);
    }

    ServiceEndpoint(String configured, boolean allowPrivateNetworkHttp, boolean allowPathPrefix,
                    AddressResolver resolver) {
        this.allowPrivateNetworkHttp = allowPrivateNetworkHttp;
        this.resolver = resolver;
        try {
            URI uri = new URI(configured == null ? "" : configured);
            String path = Objects.toString(uri.getRawPath(), "");
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || path.contains("%")
                    || !uri.normalize().equals(uri)
                    || (!allowPathPrefix && !path.isEmpty() && !"/".equals(path))) {
                throw new IllegalArgumentException("服务地址无效，禁止凭据、查询参数或不受支持的路径");
            }
            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.base = URI.create(normalized);
            if (isPlainHttp()) {
                resolveHttpAddress(DEFAULT_RESOLVE_TIMEOUT_NANOS);
            }
        } catch (URISyntaxException | IOException e) {
            // 不传播配置值及解析器异常正文，避免地址中误填的凭据出现在日志。
            throw new IllegalArgumentException("服务 HTTP 地址必须为回环地址或显式启用的 RFC1918 私网地址");
        }
    }

    public URI requestUri(String path) throws IOException {
        return requestUri(path, DEFAULT_RESOLVE_TIMEOUT_NANOS);
    }

    public URI requestUri(String path, long timeoutNanos) throws IOException {
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("服务请求路径必须从根路径开始");
        }
        URI target = URI.create(base.toString() + path);
        if (!isPlainHttp()) {
            return target;
        }
        InetAddress address = resolveHttpAddress(timeoutNanos);
        try {
            return new URI(base.getScheme(), null, address.getHostAddress(), base.getPort(),
                    target.getPath(), null, null);
        } catch (URISyntaxException e) {
            throw new IOException("服务 HTTP 请求地址无效");
        }
    }

    private InetAddress resolveHttpAddress(long timeoutNanos) throws IOException {
        String host = base.getHost();
        boolean configuredLoopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host) || "[::1]".equals(host);
        if (!allowPrivateNetworkHttp && !configuredLoopback) {
            throw new IOException("远程服务必须使用 HTTPS，私网 HTTP 需显式开启");
        }
        InetAddress[] addresses = resolveWithinDeadline(host, timeoutNanos);
        if (addresses.length == 0) {
            throw new IOException("服务域名没有可用地址");
        }
        for (InetAddress address : addresses) {
            if (!address.isLoopbackAddress() && !(allowPrivateNetworkHttp && isRfc1918(address))) {
                throw new IOException("服务域名解析包含公网、链路本地或不受支持的地址");
            }
        }
        return addresses[0];
    }

    private InetAddress[] resolveWithinDeadline(String host, long timeoutNanos) throws IOException {
        if (timeoutNanos <= 0 || Thread.currentThread().isInterrupted()) {
            throw new IOException("服务地址解析已经截止或中断");
        }
        Future<InetAddress[]> pending;
        try {
            pending = RESOLVERS.submit(() -> resolver.resolve(host));
        } catch (RejectedExecutionException e) {
            throw new IOException("服务地址解析并发已达上限");
        }
        try {
            return pending.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("服务地址解析已中断");
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("服务地址解析失败或超时");
        } finally {
            if (!pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private boolean isPlainHttp() {
        return "http".equalsIgnoreCase(base.getScheme());
    }

    private static boolean isRfc1918(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 10 || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    @FunctionalInterface
    interface AddressResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}

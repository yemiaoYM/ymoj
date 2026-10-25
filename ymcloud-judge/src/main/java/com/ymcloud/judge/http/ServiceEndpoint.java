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
 * @Author: Yemiao
 * @CreateTime: 2026-10-16
 * @Description: 服务地址默认只允许 HTTPS 或回环 HTTP；容器私网 HTTP 必须由部署显式开启。
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

    /**
     * 校验服务地址并限制 HTTP 明文连接范围。
     * @param configured 服务基础地址
     * @param allowPrivateNetworkHttp 是否允许 RFC1918 私网 HTTP
     * @param allowPathPrefix 是否允许固定路径前缀
     */
    public ServiceEndpoint(String configured, boolean allowPrivateNetworkHttp, boolean allowPathPrefix) {
        this(configured, allowPrivateNetworkHttp, allowPathPrefix, InetAddress::getAllByName);
    }

    /**
     * 使用给定解析器校验服务地址，供隔离测试控制 DNS。
     * @param configured 服务基础地址
     * @param allowPrivateNetworkHttp 是否允许 RFC1918 私网 HTTP
     * @param allowPathPrefix 是否允许固定路径前缀
     * @param resolver 受控 DNS 解析器
     */
    ServiceEndpoint(String configured, boolean allowPrivateNetworkHttp, boolean allowPathPrefix,
                    AddressResolver resolver) {
        this.allowPrivateNetworkHttp = allowPrivateNetworkHttp;
        this.resolver = resolver;
        try {
            URI uri = new URI(configured == null ? "" : configured);
            String path = Objects.toString(uri.getRawPath(), "");
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535
                    || path.contains("%")
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

    /**
     * 以默认 DNS 预算生成固定服务请求地址。
     * @param path 内部固定路径，必须以单个斜线开头
     * @return 校验过的请求地址
     * @throws IOException 域名解析或目标地址无效
     */
    public URI requestUri(String path) throws IOException {
        return requestUri(path, DEFAULT_RESOLVE_TIMEOUT_NANOS);
    }

    /**
     * 按预算解析 HTTP 地址并固定本次请求 IP。
     * @param path 内部固定路径
     * @param timeoutNanos DNS 解析剩余纳秒数
     * @return HTTPS 地址或固定 HTTP IP 的请求地址
     * @throws IOException 解析超时、中断或目标不允许
     */
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
            return new URI(base.getScheme(),
                    null,
                    address.getHostAddress(),
                    base.getPort(),
                    target.getPath(),
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new IOException("服务 HTTP 请求地址无效");
        }
    }

    /**
     * 检查所有 DNS 结果，不能混入公网或链路本地地址。
     * @param timeoutNanos 剩余解析预算，纳秒
     * @return 首个已验证地址
     * @throws IOException 地址不被允许或解析失败
     */
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

    /**
     * 使用受限线程池解析 DNS，超时取消等待。
     * @param host 服务主机名
     * @param timeoutNanos 剩余解析预算，纳秒
     * @return 解析得到的地址数组
     * @throws IOException 并发耗尽、中断或解析失败
     */
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

    /**
     * 判断当前基础地址是否使用明文 HTTP。
     * @return 是否为 HTTP
     */
    private boolean isPlainHttp() {
        return "http".equalsIgnoreCase(base.getScheme());
    }

    /**
     * 检查 IPv4 是否属于 RFC1918 私网。
     * @param address 待检查地址
     * @return 是否属于允许的私网区间
     */
    private static boolean isRfc1918(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    @FunctionalInterface
    interface AddressResolver {

        /**
         * 解析受控服务主机名。
         * @param host 主机名
         * @return 解析结果数组
         * @throws UnknownHostException 主机无法解析
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}

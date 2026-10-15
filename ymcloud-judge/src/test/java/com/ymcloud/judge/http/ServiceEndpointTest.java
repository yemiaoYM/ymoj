package com.ymcloud.judge.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 私网模式要求整个 DNS 结果集合法，并把请求固定到已验证地址，避免解析变化泄露令牌。
 */
class ServiceEndpointTest {

    @Test
    void defaultModeStillRejectsPrivateHttpAndAllowsLoopbackOrHttps() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> endpoint("http://web:9091", false, "172.18.0.3"));
        ServiceEndpoint local = endpoint("http://localhost:9091", false, "127.0.0.1", "::1");
        assertEquals("http://127.0.0.1:9091/health", local.requestUri("/health").toString());
        ServiceEndpoint remote = endpoint("https://web.example.test/prefix/", false, "8.8.8.8");
        assertEquals("https://web.example.test/prefix/health", remote.requestUri("/health").toString());
    }

    @Test
    void explicitPrivateModeAcceptsOnlyRfc1918AndLoopback() throws Exception {
        for (String address : List.of("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.1.2", "127.0.0.1")) {
            ServiceEndpoint endpoint = endpoint("http://go-judge:5050", true, address);
            assertEquals(address, endpoint.requestUri("/version").getHost());
        }
        for (String address : List.of("8.8.8.8", "169.254.169.254", "0.0.0.0", "172.15.255.254",
                "172.32.0.1", "192.169.1.2", "100.64.0.1", "224.0.0.1", "fd00::1", "fe80::1")) {
            assertThrows(IllegalArgumentException.class,
                    () -> endpoint("http://web:9091", true, address), address);
        }
    }

    @Test
    void mixedAddressResultsAndResolutionChangesFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> endpoint("http://web", true, "172.18.0.3", "8.8.8.8"));
        AtomicReference<InetAddress[]> resolved = new AtomicReference<>(addresses("172.18.0.3"));
        ServiceEndpoint endpoint = new ServiceEndpoint("http://web:9091/api", true, true, host -> resolved.get());
        assertEquals("http://172.18.0.3:9091/api/health", endpoint.requestUri("/health").toString());
        resolved.set(addresses("172.18.0.4"));
        assertEquals("172.18.0.4", endpoint.requestUri("/health").getHost());
        resolved.set(addresses("169.254.169.254"));
        assertThrows(IOException.class, () -> endpoint.requestUri("/health"));
    }

    @Test
    void invalidConfiguredAddressesCannotCarrySecretsOrEscapeTheBasePath() {
        for (String address : List.of("http://user:password@web", "https://web?token=secret", "https://web#fragment",
                "file:///tmp/data", "http://web:0", "http://web:65536", "http://web/a/../b", "http://web/%2e%2e")) {
            assertThrows(IllegalArgumentException.class, () -> endpoint(address, true, "172.18.0.3"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceEndpoint("https://web/path", true, false));
    }

    @Test
    void slowDnsResolutionIsBoundedByRequestDeadline() throws Exception {
        AtomicBoolean slow = new AtomicBoolean();
        ServiceEndpoint endpoint = new ServiceEndpoint("http://web", true, true, host -> {
            if (slow.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new UnknownHostException("interrupted");
                }
            }
            return addresses("172.18.0.3");
        });
        slow.set(true);
        long started = System.nanoTime();
        assertThrows(IOException.class, () -> endpoint.requestUri("/health", TimeUnit.MILLISECONDS.toNanos(50)));
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1));
    }

    private static ServiceEndpoint endpoint(String base, boolean allowPrivate, String... addresses) throws Exception {
        InetAddress[] values = addresses(addresses);
        return new ServiceEndpoint(base, allowPrivate, true, host -> values);
    }

    private static InetAddress[] addresses(String... addresses) throws UnknownHostException {
        InetAddress[] result = new InetAddress[addresses.length];
        for (int index = 0; index < addresses.length; index++) {
            result[index] = InetAddress.getByName(addresses[index]);
        }
        return result;
    }
}

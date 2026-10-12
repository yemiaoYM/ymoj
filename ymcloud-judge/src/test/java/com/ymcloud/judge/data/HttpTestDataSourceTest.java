package com.ymcloud.judge.data;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地真实 HTTP 验证服务身份、受限地址、流式读取、重定向拒绝及总请求超时。
 */
class HttpTestDataSourceTest {

    private static final String TEST_TOKEN = "test-worker-token-00000000000000000000";

    private HttpServer server;
    private ExecutorService executor;
    private WorkerDataProperties properties;
    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            handler.get().handle(exchange);
        });
        server.start();
        properties = new WorkerDataProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken(TEST_TOKEN);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        handler.set(exchange -> respond(exchange, 200, bytes("default")));
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop(0);
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void manifestAndFilesUseOnlyFixedRoutesAndServiceHeader() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> userAuth = new AtomicReference<>();
        byte[] original = bytes(" \tinput\r\n\n");
        handler.set(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            token.set(exchange.getRequestHeaders().getFirst(HttpTestDataSource.TOKEN_HEADER));
            userAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, original);
        });
        try (HttpTestDataSource source = source()) {
            assertArrayEquals(original, source.fetchManifest(10L, 77L));
            assertEquals("/internal/judge-worker/problems/10/versions/77/manifest", path.get());
            assertEquals(TEST_TOKEN, token.get());
            assertNull(userAuth.get());
            try (InputStream input = source.openFile(10L, 77L, 101L)) {
                assertArrayEquals(original, input.readAllBytes());
            }
            assertEquals("/internal/judge-worker/problems/10/versions/77/files/101", path.get());
        }
    }

    @Test
    void redirectsAndErrorResponsesNeverForwardTokenOrErrorBody() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        handler.set(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/escape")) {
                redirected.incrementAndGet();
                respond(exchange, 200, bytes("private"));
            } else {
                exchange.getResponseHeaders().set("Location", properties.getBaseUrl() + "/escape");
                respond(exchange, 302, bytes("do not follow"));
            }
        });
        try (HttpTestDataSource source = source()) {
            IOException redirect = assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
            assertTrue(redirect.getMessage().contains("302"));
            assertEquals(0, redirected.get());
            handler.set(exchange -> respond(exchange, 403, bytes("sensitive service error " + TEST_TOKEN)));
            IOException denied = assertThrows(IOException.class, () -> source.openFile(10L, 77L, 101L));
            assertTrue(denied.getMessage().contains("403"));
            assertFalse(denied.getMessage().contains(TEST_TOKEN));
            assertFalse(denied.getMessage().contains("sensitive service error"));
        }
    }

    @Test
    void remotePlainHttpAndUnsafeBaseUrlsAreRejected() {
        for (String invalid : List.of("http://10.0.0.8:9091", "http://example.com", "file:///tmp/a",
                "https://user:secret@example.com", "https://example.com?token=a", "https://example.com#fragment",
                "https://example.com/a/../", "https://example.com/%2e%2e/", "https://example.com:65536")) {
            properties.setBaseUrl(invalid);
            assertThrows(IllegalArgumentException.class, this::source, invalid);
        }
        properties.setBaseUrl("https://worker-data.example.com/context/");
        try (HttpTestDataSource ignored = source()) {
            assertEquals(0, requests.get());
        }
    }

    @Test
    void weakInvalidTokensAndInvalidTimeoutsFailWithoutNetworkRequests() {
        for (String invalid : List.of("short", TEST_TOKEN + "\n", TEST_TOKEN + "\r", TEST_TOKEN + " ")) {
            properties.setToken(invalid);
            assertThrows(IllegalArgumentException.class, this::source);
        }
        properties.setToken(TEST_TOKEN);
        properties.setConnectTimeout(Duration.ZERO);
        assertThrows(IllegalArgumentException.class, this::source);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofHours(1));
        assertThrows(IllegalArgumentException.class, this::source);
        assertEquals(0, requests.get());
    }

    @Test
    void invalidIdsAndClosedClientNeverContactServer() throws Exception {
        try (HttpTestDataSource source = source()) {
            assertThrows(IOException.class, () -> source.fetchManifest(null, 77L));
            assertThrows(IOException.class, () -> source.fetchManifest(10L, -1L));
            assertThrows(IOException.class, () -> source.openFile(10L, 77L, 0L));
            source.close();
            assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
            assertEquals(0, requests.get());
        }
    }

    @Test
    void closingClientInvalidatesAnAlreadyOpenedResponse() throws Exception {
        try (HttpTestDataSource source = source();
             InputStream input = source.openFile(10L, 77L, 101L)) {
            assertEquals('d', input.read());
            source.close();
            assertThrows(IOException.class, input::read);
            assertThrows(IOException.class, () -> input.skip(1));
            assertEquals(1, requests.get());
        }
    }

    @Test
    void declaredAndChunkedOversizedManifestsAreBothLimited() throws Exception {
        TestDataCacheProperties cacheProperties = new TestDataCacheProperties();
        cacheProperties.setMaxManifestBytes(8);
        handler.set(exchange -> respond(exchange, 200, bytes("larger than eight bytes")));
        try (HttpTestDataSource source = new HttpTestDataSource(properties, cacheProperties)) {
            assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
            handler.set(exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (exchange; var output = exchange.getResponseBody()) {
                    output.write(bytes("larger than eight bytes"));
                }
            });
            assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
        }
    }

    @Test
    void slowBodyHasTotalDeadlineEvenWhenBytesKeepArriving() throws Exception {
        properties.setReadTimeout(Duration.ofMillis(350));
        CountDownLatch started = new CountDownLatch(1);
        handler.set(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            started.countDown();
            try (exchange; var output = exchange.getResponseBody()) {
                for (int index = 0; index < 30; index++) {
                    output.write('x');
                    output.flush();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException ignored) {
                // 测试客户端按总时长主动断开连接。
            }
        });
        long startedAt = System.nanoTime();
        try (HttpTestDataSource source = source()) {
            assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
            assertTrue(started.await(1, TimeUnit.SECONDS));
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue(elapsed < 1400, "完整请求应在持续到达的小块数据下仍按截止时间退出，实际 " + elapsed + "ms");
    }

    @Test
    void stalledResponseDoesNotLeaveWorkerReadingForever() throws Exception {
        properties.setReadTimeout(Duration.ofMillis(250));
        handler.set(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (exchange; var output = exchange.getResponseBody()) {
                output.write('x');
                output.flush();
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (IOException ignored) {
                // 测试客户端断开已超时的连接。
            }
        });
        long startedAt = System.nanoTime();
        try (HttpTestDataSource source = source()) {
            assertThrows(IOException.class, () -> source.fetchManifest(10L, 77L));
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue(elapsed < 1200, "停滞的响应应按超时退出，实际 " + elapsed + "ms");
    }

    private HttpTestDataSource source() {
        return new HttpTestDataSource(properties, new TestDataCacheProperties());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

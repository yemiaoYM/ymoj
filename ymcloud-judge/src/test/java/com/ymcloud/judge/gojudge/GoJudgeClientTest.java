package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地真实 HTTP 覆盖鉴权、文件接口、结构化状态、完整传输边界和中断后的断连。
 */
class GoJudgeClientTest {

    private static final String TOKEN = "test-go-judge-token-0000000000000000000";
    private static final String ACCEPTED = "[{\"status\":\"Accepted\",\"exitStatus\":0,"
            + "\"time\":1000000,\"runTime\":2000000,\"memory\":1024,\"files\":{\"stdout\":\"你好\\n\"}}]";

    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private ExecutorService serverExecutor;
    private GoJudgeProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            handler.get().handle(exchange);
        });
        server.start();
        properties = new GoJudgeProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken(TOKEN);
        handler.set(exchange -> respond(exchange, 200, ACCEPTED));
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.stop(0);
        serverExecutor.shutdownNow();
        assertTrue(serverExecutor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void executeUsesBearerAndPreservesStructuredTimesAndUnicode() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> request = new AtomicReference<>();
        handler.set(exchange -> {
            assertEquals("/run", exchange.getRequestURI().getPath());
            assertEquals("POST", exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, ACCEPTED);
        });
        try (GoJudgeClient client = client()) {
            var result = client.execute(command(), deadline()).get(0);
            assertEquals("Bearer " + TOKEN, authorization.get());
            assertEquals("你好\n", result.path("files").path("stdout").asText());
            assertEquals(2000000, result.path("runTime").longValue());
            assertTrue(request.get().contains("/bin/true"));
        }
    }

    @Test
    void authenticationRequiresUnauthorizedAnonymousAndValidAuthenticatedFileRoute() throws Exception {
        handler.set(exchange -> {
            assertEquals("/file", exchange.getRequestURI().getPath());
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            respond(exchange, authorization == null ? 401 : 200, "{}");
        });
        try (GoJudgeClient client = client()) {
            client.verifyAuthentication(deadline());
            assertEquals(2, requests.get());
            handler.set(exchange -> respond(exchange, 200, "{}"));
            assertThrows(GoJudgeException.class, () -> client.verifyAuthentication(deadline()));
        }
    }

    @Test
    void multipartUploadPreservesBytesAndDeletionUsesReturnedId() throws Exception {
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        handler.set(exchange -> {
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                uploaded.set(exchange.getRequestBody().readAllBytes());
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                respond(exchange, 200, "\"file-001\"");
            } else {
                assertEquals("DELETE", exchange.getRequestMethod());
                assertEquals("/file/file-001", exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });
        try (GoJudgeClient client = client()) {
            String id = client.upload(new ByteArrayInputStream(" \n你好\t\n".getBytes(StandardCharsets.UTF_8)),
                    "stdin", deadline());
            assertEquals("file-001", id);
            assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
            String multipart = new String(uploaded.get(), StandardCharsets.UTF_8);
            assertTrue(multipart.contains("name=\"file\"; filename=\"stdin\""));
            assertTrue(multipart.contains("\r\n\r\n \n你好\t\n\r\n--"));
            client.delete(id, deadline());
        }
    }

    @Test
    void missingFilesUnknownStatusesAndInternalErrorsAreSystemFailures() {
        try (GoJudgeClient client = client()) {
            handler.set(exchange -> respond(exchange, 404, "private file path"));
            assertThrows(GoJudgeFileMissingException.class, () -> client.execute(command(), deadline()));
            assertThrows(GoJudgeFileMissingException.class, () -> client.delete("lost-file", deadline()));
            for (String status : List.of("Internal Error", "new unexpected status", "")) {
                handler.set(exchange -> respond(exchange, 200, ACCEPTED.replace("Accepted", status)));
                assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
            }
        }
    }

    @Test
    void malformedStatsOrCommandCountsCannotBeMistakenForAccepted() {
        try (GoJudgeClient client = client()) {
            for (String invalid : List.of("[]", "{}", ACCEPTED.replace("2000000", "-1"),
                    ACCEPTED.replace("2000000", "null"), ACCEPTED.replace("2000000", "1.1"),
                    ACCEPTED.replace("2000000", "9223372036854775808"))) {
                handler.set(exchange -> respond(exchange, 200, invalid));
                assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
            }
        }
    }

    @Test
    void rejectedResponsesDeleteAllValidCachedFilesBeforeReturningTheSystemFailure() {
        String cached = ACCEPTED.replace("\"files\":", "\"fileIds\":{\"program\":\"artifact-one\","
                + "\"extra\":\"artifact-two\"},\"files\":");
        List<String> deleted = new CopyOnWriteArrayList<>();
        try (GoJudgeClient client = client()) {
            for (String invalid : List.of(
                    cached.replace("Accepted", "Internal Error"),
                    cached.replace("2000000", "-1"),
                    cached.replace("\"exitStatus\":0", "\"exitStatus\":null"),
                    cached.replace("\"files\":", "\"files\":123,\"ignored\":"),
                    cached.replace("\"extra\":\"artifact-two\"",
                            "\"unsafe\":\"../escape\",\"invalid\":17,\"extra\":\"artifact-two\""),
                    cached.substring(0, cached.length() - 1) + "," + cached.substring(1),
                    cached.substring(1, cached.length() - 1))) {
                deleted.clear();
                handler.set(exchange -> {
                    if ("DELETE".equals(exchange.getRequestMethod())) {
                        deleted.add(exchange.getRequestURI().getPath());
                        respond(exchange, 200, "{}");
                    } else {
                        respond(exchange, 200, invalid);
                    }
                });
                assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
                assertEquals(Set.of("/file/artifact-one", "/file/artifact-two"), Set.copyOf(deleted));
                assertEquals(2, deleted.size(), "重复 ID 只清理一次，无效 ID 不得进入删除请求");
            }
        }
    }

    @Test
    void rejectedResponseCleanupTreatsMissingFilesAsReleasedAndPreservesTheOriginalFailure() {
        String cached = ACCEPTED.replace("Accepted", "Internal Error")
                .replace("\"files\":", "\"fileIds\":{\"program\":\"artifact-one\"},\"files\":");
        AtomicInteger deleted = new AtomicInteger();
        try (GoJudgeClient client = client()) {
            for (int status : List.of(404, 503)) {
                handler.set(exchange -> {
                    if ("DELETE".equals(exchange.getRequestMethod())) {
                        deleted.incrementAndGet();
                        respond(exchange, status, "private content");
                    } else {
                        respond(exchange, 200, cached);
                    }
                });
                GoJudgeException error = assertThrows(GoJudgeException.class,
                        () -> client.execute(command(), deadline()));
                assertEquals("go-judge 返回未知状态或内部错误", error.getMessage());
            }
        }
        assertEquals(2, deleted.get());
    }

    @Test
    void rejectedResponseCleanupHasAnIndependentDeadlineAndPreservesInterrupts() throws Exception {
        String cached = ACCEPTED.replace("Accepted", "Internal Error")
                .replace("\"files\":", "\"fileIds\":{\"program\":\"artifact-one\"},\"files\":");
        CountDownLatch deleting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        handler.set(exchange -> {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                deleting.countDown();
                exchange.sendResponseHeaders(200, 0);
                try (exchange; var body = exchange.getResponseBody()) {
                    for (int index = 0; index < 100; index++) {
                        body.write(' ');
                        body.flush();
                        Thread.sleep(25);
                    }
                } catch (IOException ignored) {
                    // 客户端达到清理截止或被租约中断时关闭连接。
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                respond(exchange, 200, cached);
            }
        });
        try (GoJudgeClient client = client()) {
            Thread worker = new Thread(() -> {
                try {
                    client.execute(command(), deadline());
                } catch (Throwable error) {
                    failure.set(error);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            worker.start();
            assertTrue(deleting.await(3, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(1500);
            assertFalse(worker.isAlive());
            assertTrue(failure.get() instanceof GoJudgeException);
            assertEquals("go-judge 返回未知状态或内部错误", failure.get().getMessage());
            assertEquals(Boolean.TRUE, interrupted.get());
            long started = System.nanoTime();
            assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3500,
                    "清理不得占满任务原本的请求预算");
        }
    }

    @Test
    void redirectsDoNotForwardCredentialsAndErrorsDoNotExposeBodies() {
        handler.set(exchange -> {
            exchange.getResponseHeaders().set("Location", properties.getBaseUrl() + "/escape");
            respond(exchange, 302, "sensitive-source " + TOKEN);
        });
        try (GoJudgeClient client = client()) {
            GoJudgeException error = assertThrows(GoJudgeException.class,
                    () -> client.execute(command(), deadline()));
            assertTrue(error.getMessage().contains("302"));
            assertFalse(error.toString().contains(TOKEN));
            assertFalse(error.toString().contains("sensitive-source"));
            assertEquals(1, requests.get());
        }
    }

    @Test
    void bothDeclaredAndChunkedResponseBodiesHaveHardSizeLimit() {
        properties.setMaxResponseBytes(1024);
        try (GoJudgeClient client = client()) {
            handler.set(exchange -> respond(exchange, 200, "x".repeat(2048)));
            assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
            handler.set(exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (exchange; var body = exchange.getResponseBody()) {
                    body.write("x".repeat(2048).getBytes(StandardCharsets.UTF_8));
                }
            });
            assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
        }
    }

    @Test
    void slowResponseIsCancelledAtTaskDeadlineEvenWhileBytesArrive() throws Exception {
        CountDownLatch disconnected = streamingResponse();
        try (GoJudgeClient client = client()) {
            long started = System.nanoTime();
            assertThrows(GoJudgeException.class,
                    () -> client.execute(command(), System.currentTimeMillis() + 300));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1500);
            assertTrue(disconnected.await(3, TimeUnit.SECONDS), "任务截止必须关闭 HTTP 连接");
        }
    }

    @Test
    void interruptedWorkerClosesRequestAndPreservesInterruptFlag() throws Exception {
        CountDownLatch disconnected = streamingResponse();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        try (GoJudgeClient client = client()) {
            Thread worker = new Thread(() -> {
                try {
                    client.execute(command(), deadline());
                } catch (Throwable e) {
                    failure.set(e);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (requests.get() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            worker.interrupt();
            worker.join(1500);
            assertFalse(worker.isAlive());
            assertTrue(failure.get() instanceof GoJudgeException);
            assertEquals(Boolean.TRUE, interrupted.get());
            assertTrue(disconnected.await(3, TimeUnit.SECONDS), "租约中断必须关闭 HTTP 连接");
        }
    }

    @Test
    void oversizedUploadAndUnsafeParametersNeverReachServer() {
        properties.setMaxFileBytes(1024);
        try (GoJudgeClient client = client()) {
            assertThrows(GoJudgeCapacityException.class,
                    () -> client.upload(new ByteArrayInputStream(new byte[1025]), "stdin", deadline()));
            assertThrows(GoJudgeException.class, () -> client.delete("../escape", deadline()));
            assertThrows(GoJudgeException.class, () -> client.execute(command(), 0));
            client.close();
            assertThrows(GoJudgeException.class, () -> client.execute(command(), deadline()));
            assertEquals(0, requests.get());
        }
    }

    @Test
    void endpointValidationRejectsPlainRemoteHttpAndCredentialUrls() {
        for (String address : List.of("http://example.com", "http://127.0.0.1/a", "https://a:password@example.com",
                "https://example.com?token=a", "https://example.com:65536", "file:///tmp/go-judge")) {
            properties.setBaseUrl(address);
            assertThrows(IllegalArgumentException.class, this::client, address);
        }
        assertEquals(0, requests.get());
    }

    @Test
    void privateHttpRequiresExplicitDeploymentOptInAndStillRejectsPublicDestinations() {
        properties.setBaseUrl("http://172.18.0.3:5050");
        assertThrows(IllegalArgumentException.class, this::client);
        properties.setAllowPrivateNetworkHttp(true);
        try (GoJudgeClient ignored = client()) {
            assertEquals(0, requests.get());
        }
        properties.setBaseUrl("http://169.254.169.254");
        assertThrows(IllegalArgumentException.class, this::client);
        properties.setBaseUrl("http://8.8.8.8");
        assertThrows(IllegalArgumentException.class, this::client);
    }

    private CountDownLatch streamingResponse() {
        CountDownLatch disconnected = new CountDownLatch(1);
        handler.set(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (exchange; var body = exchange.getResponseBody()) {
                for (int index = 0; index < 100; index++) {
                    body.write(' ');
                    body.flush();
                    Thread.sleep(25);
                }
            } catch (IOException e) {
                disconnected.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return disconnected;
    }

    private GoJudgeClient client() {
        return new GoJudgeClient(properties, new ObjectMapper());
    }

    private static Map<String, Object> command() {
        return Map.of("cmd", List.of(Map.of("args", List.of("/bin/true"))));
    }

    private static long deadline() {
        return System.currentTimeMillis() + 5000;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(encoded);
        }
    }
}

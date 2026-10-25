package com.ymcloud.judge.data;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 用停滞的本地 HTTP 响应验证任务中断能提前释放下载线程。
 */
class HttpTestDataSourceCancellationTest {

    /**
     * 响应头尚未返回时，失去任务租约也必须停止网络等待。
     * @throws Exception 本地 HTTP 服务或线程等待失败
     */
    @Test
    void interruptStopsWaitingForHeaders() throws Exception {
        verifyCancellation(false);
    }

    /**
     * 已收到部分正文时，中断不应继续等待配置的长下载超时。
     * @throws Exception 本地 HTTP 服务或线程等待失败
     */
    @Test
    void interruptStopsWaitingForBody() throws Exception {
        verifyCancellation(true);
    }

    /**
     * 在固定请求阶段暂停服务端，直接中断客户端并验证异常及中断标志。
     * @param sendHeaders 是否先发送响应头和一个字节的正文
     * @throws Exception 本地网络请求或测试线程未按时退出
     */
    private void verifyCancellation(boolean sendHeaders) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var handlers = Executors.newSingleThreadExecutor();
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        CountDownLatch readingBody = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        server.setExecutor(handlers);
        server.createContext("/", exchange -> {
            try (exchange) {
                if (sendHeaders) {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                }
                arrived.countDown();
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        WorkerDataProperties properties = new WorkerDataProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("test-cancellation-token-00000000000000");
        properties.setReadTimeout(Duration.ofSeconds(10));
        Thread request = null;
        try (HttpTestDataSource source = new HttpTestDataSource(properties, new TestDataCacheProperties())) {
            request = new Thread(() -> {
                try {
                    if (sendHeaders) {
                        try (var input = source.openFile(1L, 1L, 1L)) {
                            input.read();
                            readingBody.countDown();
                            input.readAllBytes();
                        }
                    } else {
                        source.fetchManifest(1L, 1L);
                    }
                } catch (Throwable e) {
                    failure.set(e);
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    completed.countDown();
                }
            }, "test-cancel-download");
            request.start();
            try {
                assertTrue(arrived.await(3, TimeUnit.SECONDS));
                if (sendHeaders) {
                    assertTrue(readingBody.await(3, TimeUnit.SECONDS));
                }
                request.interrupt();
                assertTrue(completed.await(2, TimeUnit.SECONDS), "中断后应提前结束，不能等待十秒下载超时");
                assertInstanceOf(IOException.class, failure.get());
                assertTrue(interrupted.get(), "中断信号须保留给任务租约边界");
            } finally {
                release.countDown();
                request.join(3000);
            }
        } finally {
            release.countDown();
            server.stop(0);
            handlers.shutdownNow();
            assertTrue(handlers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}

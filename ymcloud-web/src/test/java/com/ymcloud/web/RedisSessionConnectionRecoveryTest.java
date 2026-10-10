package com.ymcloud.web;

import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.config.RedisConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.RedisSessionService;
import com.ymcloud.pojo.entity.User;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实Lettuce连接和本地故障服务验证无响应连接被替换，不依赖外部Redis
 */
public class RedisSessionConnectionRecoveryTest {

    @Test
    void silentConnectionIsReplacedAndCachedSessionIsRecovered() throws Exception {
        User user = new User();
        user.setId(9L);
        user.setUsername("editor");
        LoginUser loginUser = new LoginUser(user, List.of("EDITOR"), List.of("admin:access"));
        loginUser.setToken("test-session");
        RedisConfig redisConfig = new RedisConfig();
        var clientBuilder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .shutdownQuietPeriod(Duration.ZERO)
                .clientOptions(ClientOptions.builder().protocolVersion(ProtocolVersion.RESP2).build());
        redisConfig.lettuceSocketCustomizer().customize(clientBuilder);
        AtomicInteger connections = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("127.0.0.1", server.getLocalPort()), clientBuilder.build());
            factory.afterPropertiesSet();
            RedisTemplate<Object, Object> template = redisConfig.redisTemplate(factory);
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
            byte[] cachedUser = serializer.serialize(loginUser);
            var serverTask = executor.submit(() -> {
                for (int i = 0; i < 2; i++) {
                    try (Socket socket = server.accept()) {
                        connections.incrementAndGet();
                        serveConnection(socket, i == 0, cachedUser, reads);
                    }
                }
                return null;
            });
            try {
                RedisCache redisCache = new RedisCache();
                redisCache.redisTemplate = template;
                RedisSessionService service = new RedisSessionService();
                ReflectionTestUtils.setField(service, "redisCache", redisCache);
                ReflectionTestUtils.setField(service, "redisConnectionFactory", factory);
                long started = System.nanoTime();
                LoginUser recovered = service.getLoginUser("login_tokens:test-session");
                assertNotNull(recovered);
                assertEquals(9L, recovered.getUser().getId());
                assertEquals("test-session", recovered.getToken());
                assertEquals(2, connections.get());
                assertEquals(2, reads.get());
                assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(5)) < 0);
            } finally {
                factory.destroy();
            }
            serverTask.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 第一条连接故意不回应GET，第二条连接正常返回会话
     */
    private void serveConnection(Socket socket, boolean silent, byte[] cachedUser, AtomicInteger reads) throws IOException {
        socket.setSoTimeout(5000);
        InputStream input = new BufferedInputStream(socket.getInputStream());
        while (true) {
            List<String> command = readCommand(input);
            if (command.isEmpty()) {
                return;
            }
            var output = socket.getOutputStream();
            if ("GET".equalsIgnoreCase(command.get(0))) {
                reads.incrementAndGet();
                if (silent) {
                    continue;
                }
                output.write(("$" + cachedUser.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(cachedUser);
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                String result = "PING".equalsIgnoreCase(command.get(0)) ? "+PONG\r\n" : "+OK\r\n";
                output.write(result.getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
        }
    }

    private List<String> readCommand(InputStream input) throws IOException {
        String header = readLine(input);
        if (header == null) {
            return List.of();
        }
        int count = Integer.parseInt(header.substring(1));
        List<String> command = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int length = Integer.parseInt(readLine(input).substring(1));
            command.add(new String(input.readNBytes(length), StandardCharsets.UTF_8));
            input.readNBytes(2);
        }
        return command;
    }

    private String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\r') {
                input.read();
                return line.toString(StandardCharsets.UTF_8);
            }
            line.write(value);
        }
        return null;
    }
}

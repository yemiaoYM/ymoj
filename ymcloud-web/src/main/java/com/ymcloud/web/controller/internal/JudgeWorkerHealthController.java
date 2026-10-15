package com.ymcloud.web.controller.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 部署就绪探针复用 Worker 专用服务身份，只返回状态，不公开连接信息与异常正文。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JudgeWorkerHealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final ConnectionFactory rabbit;

    @GetMapping("/internal/judge-worker/health")
    public ResponseEntity<Void> health() {
        try (Connection database = dataSource.getConnection()) {
            if (!database.isValid(2)) {
                return ResponseEntity.status(503).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=mysql cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(503).build();
        }
        try (var connection = redis.getRequiredConnectionFactory().getConnection()) {
            if (!"PONG".equals(connection.ping())) {
                return ResponseEntity.status(503).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=redis cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(503).build();
        }
        try (var connection = rabbit.createConnection()) {
            if (!connection.isOpen()) {
                return ResponseEntity.status(503).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=rabbitmq cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok().build();
    }
}

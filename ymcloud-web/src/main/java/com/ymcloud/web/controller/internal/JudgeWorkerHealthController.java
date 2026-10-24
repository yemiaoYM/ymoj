package com.ymcloud.web.controller.internal;

import com.ymcloud.common.constant.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-16
 * @Description: 部署就绪探针，仅返回状态，不公开连接信息与异常正文
 */
@Slf4j
@RestController
public class JudgeWorkerHealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ConnectionFactory rabbit;

    /**
     * 检查数据库、Redis 和消息连接是否就绪；正常探针不重复记录日志。
     * @return 全部就绪返回 200，任一不可用返回 503，均不返回响应体
     */
    @GetMapping("/internal/judge-worker/health")
    public ResponseEntity<Void> health() {
        try (Connection database = dataSource.getConnection()) {
            if (!database.isValid(2)) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=mysql cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try (var connection = redis.getRequiredConnectionFactory().getConnection()) {
            if (!"PONG".equals(connection.ping())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=redis cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try (var connection = rabbit.createConnection()) {
            if (!connection.isOpen()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        } catch (Exception e) {
            log.debug("Web 就绪检查失败 dependency=rabbitmq cause={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().build();
    }
}

package com.ymcloud.core.config;


import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.TokenService;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.List;


/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: WebSocketConfig
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  21:09
 * @Description: websocket配置类
 */
@Slf4j
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        log.info("开始创建WebSocket中...");
        return new ServerEndpointExporter();
    }

}

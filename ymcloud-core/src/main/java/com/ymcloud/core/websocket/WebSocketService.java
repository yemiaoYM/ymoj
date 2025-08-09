package com.ymcloud.core.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

/**
 * @PackageName: com.ymcloud.core.websocket
 * @ClassName: WebSocketService
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  23:31
 * @Description: WebSocket服务类
 */
@ServerEndpoint("/judge")
@Component
public class WebSocketService {

    @OnOpen
    public void OnOpen(Session session) {

    }

    @OnMessage
    public void OnMessage(String message, Session session) {

    }

    @OnClose
    public void OnClose(Session session) {

    }

    @OnError
    public void OnError(Session session, Throwable throwable) {

    }
}

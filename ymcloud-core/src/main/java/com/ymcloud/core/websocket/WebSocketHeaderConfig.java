package com.ymcloud.core.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.websocket
 * @ClassName: WebSocketHeaderConfig
 * @Author: Yemiao
 * @CreateTime: 2025-08-11  02:19
 * @Description: WebSocket握手配置
 */
public class WebSocketHeaderConfig extends ServerEndpointConfig.Configurator {

    /**
     * 设置信息头
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        String header = "Sec-WebSocket-Protocol";
        List<String> protocol = request.getHeaders().get(header);
        response.getHeaders().put(header,protocol);
    }


}

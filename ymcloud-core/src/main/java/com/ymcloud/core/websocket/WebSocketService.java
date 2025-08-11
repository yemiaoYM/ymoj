package com.ymcloud.core.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @PackageName: com.ymcloud.core.websocket
 * @ClassName: WebSocketService
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  23:31
 * @Description: WebSocket服务类
 */
@ServerEndpoint(value = "/ws/{sid}",configurator = WebSocketHeaderConfig.class)
@Component
@Slf4j
public class WebSocketService {

    /**
     * 存放所有在线的客户端
     * key: username
     */
    private static final Map<Long,Session> onlineSessionClientMap = new ConcurrentHashMap<>();

    /**
     * 建立连接
     */
    @OnOpen
    public void OnOpen(Session session, @PathParam("sid") Long sid) {
        log.info("客户端：{} 建立连接", sid);
        onlineSessionClientMap.put(sid,session);
    }

    /**
     * 接受消息
     */
    @OnMessage
    public void OnMessage(String message, @PathParam("sid") Long sid) {
        log.info("收到来自客户端：{} 的信息：{}", sid, message);


    }

    /**
     * 关闭连接
     */
    @OnClose
    public void OnClose(@PathParam("sid") Long sid) {
        Session session = onlineSessionClientMap.remove(sid);
        if(session==null){
            return;
        }
        try {
            session.close();
        }catch (Exception e){
            log.error("session断开错误",e);
        }
        log.info("客户端：{} 成功断开连接", sid);
    }

    /**
     * 发生错误
     */
    @OnError
    public void OnError(@PathParam("sid") Long sid, Throwable throwable) {
        log.error("客户端：{} websocket连接出现错误：", sid, throwable);
    }

    /**
     * 指定发送消息
     */
    public void sendToOne(Long sid,String message){
        Session session = onlineSessionClientMap.get(sid);
        if(session==null){
            return;
        }
        //异步发送
        log.info("服务端给客户端:{}发送消息:{}", sid, message);
        session.getAsyncRemote().sendText(message);
    }

    /**
     * 广播发送消息
     */
    public void sendToAll(String message){
        //异步发送
        log.info("服务端给所有客户端发送消息:{}", message);
        for(Session session : onlineSessionClientMap.values()){
            session.getAsyncRemote().sendText(message);
        }
    }


}

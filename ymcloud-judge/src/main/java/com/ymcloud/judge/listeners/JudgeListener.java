package com.ymcloud.judge.listeners;

import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.judge.strategy.JudgeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @PackageName: com.ymcloud.judge.listeners
 * @ClassName: JudgeListener
 * @Author: Yemiao
 * @CreateTime: 2025-07-27  13:23
 * @Description: 评测监听
 */
@Slf4j
@Component
public class JudgeListener {

    @Autowired
    private JudgeExecutor judgeExecutor;

    /**
     * 评测消息队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_QUEUE,durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_ROUTING_KEY
    ))
    public void listen(JudgeMessage msg) {
        log.info("评测机开始评测：{}",msg.getSubmissionId());
        judgeExecutor.execute(msg);
        //业务中尽量不要抛异常，不然会启动重试机制，发送到error队列中
    }



}

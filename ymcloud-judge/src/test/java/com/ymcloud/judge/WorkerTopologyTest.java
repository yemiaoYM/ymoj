package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerTopologyTest {

    @Test
    void retryUsesTtlAndDeadLetterAndDoesNotNeedDelayedExchangePlugin() {
        var topology = new WorkerAmqpConfig().workerTopology(JudgeProtocol.EXCHANGE,
                JudgeProtocol.TASK_QUEUE, JudgeProtocol.RESULT_QUEUE, "judge", "judge.result");
        TopicExchange exchange = topology.getDeclarablesByType(TopicExchange.class).get(0);
        assertFalse(exchange.isDelayed());
        assertTrue(exchange.isDurable());
        var queues = topology.getDeclarablesByType(Queue.class);
        Queue task = queues.stream().filter(queue -> queue.getName().equals(JudgeProtocol.TASK_QUEUE)).findFirst().orElseThrow();
        Queue retry = queues.stream().filter(queue -> queue.getName().endsWith(".retry")).findFirst().orElseThrow();
        assertEquals("judge.dead", task.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(3000, retry.getArguments().get("x-message-ttl"));
        assertEquals("judge", retry.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void workerClasspathHasNoDatabaseOrWebSecurityImplementation() {
        for (String forbidden : new String[]{"com.ymcloud.core.security.config.SecurityConfig",
                "com.ymcloud.core.web.dao.JudgeEntityService", "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                "com.mysql.cj.jdbc.Driver"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(forbidden));
        }
    }
}

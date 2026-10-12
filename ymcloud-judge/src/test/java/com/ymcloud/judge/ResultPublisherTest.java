package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.ResultPublisher;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ResultPublisherTest {

    @Test
    void publisherRejectsNackOrReturnedMessageEvenWhenConfirmIsAck() {
        for (boolean returned : new boolean[]{false, true}) {
            RabbitTemplate rabbit = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                CorrelationData correlation = invocation.getArgument(3);
                if (returned) {
                    correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312,
                            "NO_ROUTE", "exchange", "judge.result"));
                }
                correlation.getFuture().complete(new CorrelationData.Confirm(returned, null));
                return null;
            }).when(rabbit).send(eq("exchange"), eq("judge.result"), any(Message.class), any(CorrelationData.class));
            ResultPublisher publisher = new ResultPublisher(rabbit, new WorkerAmqpConfig().workerObjectMapper(),
                    new WorkerProperties(), "exchange", "judge.result");
            assertThrows(IllegalStateException.class, () -> publisher.publish(
                    JudgeResult.builder().terminal(true).status(JudgeVerdict.STATUS_ACCEPTED).build()));
        }
    }
}

package com.ymcloud.judge.listeners;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.judge.domain.JudgeResultMessage;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.enums.JudgeStatus;
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
    //业务中尽量不要抛异常，不然会启动重试机制，发送到error队列中

    @Autowired
    private JudgeRun judgeRun;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    /**
     * 评测消息队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_QUEUE, durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_ROUTING_KEY
    ))
    public void listenJudge(JudgeMessage msg) {
        log.info("评测机开始评测：{}",msg.getSubmissionId());
        judgeRun.execute(msg);
    }

    /**
     * 评测结束文件清理队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_FILE_CLEAN_QUEUE, durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_RESULT_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY
    ))
    public void listenJudgeFileClean(JudgeResultMessage msg) {
        log.info("评测结束文件清理:{}",msg.getSubmissionId());
        //如果是系统错误则不删除，等待重测
        if (msg.getStatus() == JudgeStatus.STATUS_SYSTEM_ERROR) {
            return;
        }
        FileUtils.deleteFile(msg.getSubmissionDir());
    }

    /**
     * 评测结束更新用户ac表队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_AC_UPDATE_QUEUE, durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_RESULT_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY
    ))
    public void listenJudgeAcUpdate(JudgeResultMessage msg) {
        log.info("评测结束更新用户ac表:{}",msg.getSubmissionId());
        //忽略自测模式
        if(JudgeConstants.TEST_JUDGE.equals(msg.getStrategy())){
            return;
        }
        //只更新状态为ac的记录
        if(msg.getStatus() != JudgeStatus.STATUS_ACCEPTED){
           return;
        }
        //查询是否已经通过了改题目
        boolean exists = userAcproblemEntityService.lambdaQuery()
                .eq(UserAcproblem::getUserId, msg.getUserId())
                .eq(UserAcproblem::getProblemId, msg.getProblemId())
                .exists();
        if(!exists){
            //首次通过则插入信息
            UserAcproblem userAcproblem = BeanUtil.copyProperties(msg,UserAcproblem.class);
            userAcproblemEntityService.save(userAcproblem);
        }
    }

    /**
     * 评测结束通知前端队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_WS_NOTIFY_QUEUE, durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_RESULT_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY
    ))
    public void listenJudgeNotify(JudgeResultMessage msg) {
        log.info("评测结束通知前端:{}",msg.getSubmissionId());
        //websocket

    }

}

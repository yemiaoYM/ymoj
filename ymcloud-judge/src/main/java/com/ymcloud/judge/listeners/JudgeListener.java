package com.ymcloud.judge.listeners;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.entity.UserAcproblem;
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
            value = @Queue(name = RabbitMQConstants.JUDGE_QUEUE,
                    durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_ROUTING_KEY
    ))
    public void listenJudge(JudgeMessage msg) {
        log.info("[评测消息队列]=>评测机收到评测消息：{}",msg.getSubmissionId());
        judgeRun.execute(msg);
    }

    /**
     * 评测结束更新用户ac表队列
     * @param msg 评测消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.JUDGE_AC_UPDATE_QUEUE,
                    durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.JUDGE_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.JUDGE_AC_UPDATE_ROUTING_KEY
    ))
    public void listenJudgeAcUpdate(JudgeMessage msg) {
        log.info("[评测结束更新用户ac表队列]=>收到提交id为:{}的消息",msg.getSubmissionId());
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
     * 文件清理队列
     * @param filename 文件名
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConstants.FILE_CLEAN_QUEUE,
                    durable = "true"),
            exchange = @Exchange(value = RabbitMQConstants.FILE_EXCHANGE,
                    delayed = "true",
                    type = ExchangeTypes.TOPIC),
            key = RabbitMQConstants.FILE_CLEAN_ROUTING_KEY
    ))
    public void listenJudgeFileClean(String filename) {
        log.info("[文件清理队列]=>收到文件:{}",filename);
        if(FileUtils.exists(filename)) {
            FileUtils.deleteFile(filename);
        }
        log.info("[文件清理队列]=>文件清理成功");
    }

}

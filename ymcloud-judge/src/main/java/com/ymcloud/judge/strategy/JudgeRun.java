package com.ymcloud.judge.strategy;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.judge.domain.JudgeResponse;
import com.ymcloud.judge.domain.SandBoxRequest;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @PackageName: com.ymcloud.judge.strategy
 * @ClassName: JudgeRun
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  18:29
 * @Description: 评测逻辑处理
 */
@Component
@Slf4j
public class JudgeRun {

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    @Autowired
    private JudgeStrategy judgeStrategy;

    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;

    /**
     * 执行判题
     * @param message 判题信息
     */
    public void execute(JudgeMessage message){
        log.info("JudgeRun评测逻辑处理...");
        //判断评测策略
        if (message.getStrategy().equals(JudgeConstants.TEST_JUDGE)){
            //自测
            log.warn("自测逻辑待完善...");
        }else if(message.getStrategy().equals(JudgeConstants.DEFAULT_JUDGE)){
            //默认评测都会更新数据库 查询当前评测信息
            //不放在try/catch中为了 连评测记录都拿不到，说明评测记录中途删除或者数据库加载失败
            //进行回滚 重试3次失败后到发送到error.queue，人工介入
            Judge judge = judgeEntityService.getById(message.getSubmissionId());
            if(judge == null){
                throw new JudgeException("评测记录不存在，评测失败");
            }
            //提交文件路径
            String submissionDir = BASE_DIR + "/" +
                    FileConstants.DIR_TMP + "/"
                    + "submission_" + message.getSubmissionId();
            try{
                //先更新评测状态
                judge.setStatus(JudgeStatus.STATUS_JUDGING);
                judgeEntityService.updateById(judge);
                //获取语言配置
                Language language = message.getLanguage();
                //创建用户代码文件并写入
                //files/tmp/submission_1/main.cpp
                String userCodeFile = submissionDir + "/" + language.getFileName();
                FileUtils.writeFile(userCodeFile,message.getCode());
                //构造沙箱请求体
                SandBoxRequest request = new SandBoxRequest();
                request.setSubmissionId(message.getSubmissionId());
                request.setProblemId(message.getProblemId());
                request.setTimeLimit(message.getTimeLimit());
                request.setMemoryLimit(message.getMemoryLimit());
                request.setCompileCmd(language.getCompileCmd());
                request.setRunCmd(language.getRunCmd());
                request.setEnv(language.getEnv());
                //判断是否需要编译
                if(language.getType().equals(LanguageConstants.COMPILE)){
                    //需要编译
                    JudgeResponse response=judgeStrategy.compile(request);
                    if(StrUtil.isNotBlank(response.getErrorMessage())){
                        //有错误信息表示编译失败
                        judge.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
                        judge.setErrorMessage(response.getErrorMessage());
                        judgeEntityService.updateById(judge);
                        return;
                    }
                }
                //c,c++为默认一倍时间和空间，其他语言两倍
                if(!language.getFileName().endsWith(".cpp")||!language.getFileName().endsWith(".c")){
                    request.setTimeLimit(message.getTimeLimit()*2);
                    request.setMemoryLimit(message.getMemoryLimit()*2);
                }
                //进行执行代码
                JudgeResponse response=judgeStrategy.defaultJudge(request);
                //设置运行时间和内存
                judge.setTime(response.getTime());
                judge.setMemory(response.getMemory());
                //根据评测模式进行评测结果设置
                List<JudgeCase> judgeCases = response.getJudgeCases();
                //目前处除了io其他都按照acm模式评测
                if(message.getMode().equals(JudgeConstants.OI)){
                    int total = judgeCases.size();
                    long ac = judgeCases.stream()
                            .filter(c -> c.getStatus() == JudgeStatus.STATUS_ACCEPTED)
                            .count();
                    //分数=(ac/total)*100 向上取整
                    int score = (int) Math.ceil(ac * 100.0 / total);
                    judge.setScore(score);
                    //根据结果判断最终状态
                    if (ac == total) {
                        //全部测试点正确，评测正确ac
                        judge.setStatus(JudgeStatus.STATUS_ACCEPTED);
                    } else if (ac == 0) {
                        //全部测试点错误，评测按照第一个测试点的状态设置
                        judge.setStatus(judgeCases.get(0).getStatus());
                    } else {
                        //否则部分正确，评测部分正确pa
                        judge.setStatus(JudgeStatus.STATUS_PARTIAL_ACCEPTED);
                    }
                }else{
                    //只有全部测试点正确，评测才算正确
                    judge.setStatus(JudgeStatus.STATUS_ACCEPTED);
                    for(JudgeCase judgeCase : judgeCases){
                        if(judgeCase.getStatus() != JudgeStatus.STATUS_ACCEPTED){
                            judge.setStatus(judgeCase.getStatus());
                            break;
                        }
                    }
                }
                //更新评测结果
                judgeCaseEntityService.saveBatch(judgeCases);
                judgeEntityService.updateById(judge);
            }catch (JudgeException | FileException e){
                log.warn("评测业务异常：",e);
                //逻辑异常就回滚
                throw e;
            }catch (Exception e){
                log.error("评测系统异常",e);
                judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
                judge.setErrorMessage("评测失败，请联系管理员");
                judgeEntityService.updateById(judge);
            }
        }
    }

}

package com.ymcloud.judge.domain;

import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.util.List;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: JudgeResponse
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  17:19
 * @Description: 沙盒相应体
 */
@Data
public class JudgeResponse {
    /**
     * 正式评测id
     */
    private Long submissionId;
    /**
     * 自测评测id
     */
    private String requestId;
    /**
     * 题目id
     */
    private Long problemId;
    /**
     * 评测状态
     */
    private JudgeStatus status;
    /**
     * 运行时间
     */
    private Long time;
    /**
     * 运行内存
     */
    private Long memory;
    /**
     * 程序输出
     */
    private String output;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 各个评测点结果
     */
    private List<JudgeCase> judgeCases;

}

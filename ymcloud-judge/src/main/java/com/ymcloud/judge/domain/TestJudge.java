package com.ymcloud.judge.domain;

import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: TestJudge
 * @Author: Yemiao
 * @CreateTime: 2025-08-10  16:22
 * @Description: 自测评测记录（保存在redis中）
 */
@Data
public class TestJudge {
    /**
     * 评测提交id
     */
    private Long submissionId;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 评测状态
     */
    private JudgeStatus status;
    /**
     * 用户输入
     */
    private String input;
    /**
     * 用户代码
     */
    private String code;
    /**
     * 评测语言
     */
    private Language language;
    /**
     * 运行时间
     */
    private Long time;
    /**
     * 运行内存
     */
    private Long memory;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 程序输出
     */
    private String output;
}

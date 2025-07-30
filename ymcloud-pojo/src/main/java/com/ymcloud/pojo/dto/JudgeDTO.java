package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: JudgeDTO
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  13:50
 * @Description: 判题请求数据传输对象
 */
@Data
public class JudgeDTO {
    /**
     * 题目唯一标识
     */
    @NotBlank(message = "题目编号不能为空")
    private String problemId;
    /**
     * 语言唯一标识
     */
    @NotBlank(message = "编程语言不能为空")
    private String language;
    /**
     * 用户代码
     */
    @NotBlank(message = "代码不能为空")
    @Size(min = 5, max = 65535, message = "代码长度必须在5到65535个字符之间")
    private String code;
    /**
     * 是否为比赛提交
     */
    @NotNull(message = "需指定是否为比赛提交")
    private Boolean contestSubmission = false;
    /**
     * 所属比赛id
     */
    private Long contestId;
    /**
     * 比赛中该题的题号索引
     */
    private String contestProblemIndex;
}

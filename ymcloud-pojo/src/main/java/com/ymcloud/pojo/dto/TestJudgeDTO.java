package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: TestJudgeDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  16:59
 * @Description: 自测请求数据传输对象
 */
@Data
public class TestJudgeDTO {

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
     * 用户输入
     */
    @Size(max = 1000, message = "用户输入最大为500长度")
    private String input;

    /**
     * 期望输出
     */
    @Size(max = 1000, message = "期望输出最大为500长度")
    private String expectedOutput;

}

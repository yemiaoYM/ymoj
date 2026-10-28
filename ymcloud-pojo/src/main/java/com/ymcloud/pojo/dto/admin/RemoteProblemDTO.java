package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 单道远程题面复制请求，不接受任意网址
 */
@Data
public class RemoteProblemDTO {

    /**
     * 平台标识，由已注册的平台适配器处理。
     */
    @NotBlank(message = "请选择题目平台")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,31}", message = "平台标识格式不正确")
    private String platform;

    /**
     * 平台题号或 AtCoder 比赛/题目标识；具体格式由对应适配器校验。
     */
    @NotBlank(message = "请输入远程题号")
    @Pattern(regexp = "[A-Za-z0-9_/-]{1,128}", message = "请输入题目标识，不支持完整网址")
    private String problemId;
}

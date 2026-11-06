package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 管理员提交的 SPJ 编译检查参数
 */
@Data
public class SpjCompileDTO {

    @NotBlank(message = "请选择 SPJ 语言")
    @Pattern(regexp = "C\\+\\+", message = "testlib SPJ 仅支持 C++")
    private String language;

    @NotBlank(message = "请输入 SPJ 源码")
    @Size(max = 65535, message = "SPJ 源码不能超过65535个字符")
    @ToString.Exclude
    private String code;
}

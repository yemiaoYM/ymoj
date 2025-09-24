package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: TagDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  16:47
 * @Description: 添加或更改标签请求体
 */
@Data
public class TagDTO {

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1, max = 20, message = "标签名称长度必须在1-20个字符之间")
    private String name;

    /**
     * 标签分类ID
     */
    @Positive
    private Long parentId;

    /**
     * 标签颜色（如 #409eff）
     */
    @NotBlank(message = "标签颜色不能为空")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "标签颜色格式不正确，应为#RRGGBB格式")
    private String color;

}

package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: TagClassificationDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  16:50
 * @Description: 新增或更新标签分类请求体
 */
@Data
public class TagClassificationDTO {

    /**
     * 标签分类名称
     */
    @NotBlank(message = "标签分类名称不能为空")
    @Size(min = 1, max = 30, message = "标签分类名称长度必须在1-30个字符之间")
    private String name;

    /**
     * 分类排序权重（数值越小排序越靠前）
     */
    @NotNull(message = "排序权重不能为空")
    @Min(value = 1, message = "排序权重不能小于0")
    @Max(value = 999, message = "排序权重不能大于999")
    private Integer priority;

}

package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: AnnouncementDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-27
 * @Description: 管理端公告DTO
 */
@Data
public class AnnouncementDTO {

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 1, max = 100, message = "公告标题长度必须在1-100个字符之间")
    private String title;

    /**
     * 公告内容（支持HTML/Markdown）
     */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 10000, message = "公告内容长度不能超过10000个字符")
    private String content;

    /**
     * 公告权重（值越大越靠前）
     */
    @NotNull(message = "公告权重不能为空")
    @Min(value = 0, message = "公告权重不能小于0")
    @Max(value = 999, message = "公告权重不能大于999")
    private Integer priority;

    /**
     * 是否置顶：true=置顶，false=不置顶
     */
    @NotNull(message = "置顶状态不能为空")
    private Boolean pinned;

    /**
     * 是否可见：true=可见，false=不可见
     */
    @NotNull(message = "可见状态不能为空")
    private Boolean status;
}

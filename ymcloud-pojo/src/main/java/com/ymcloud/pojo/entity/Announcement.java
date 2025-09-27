package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="公告表")
public class Announcement {
    @TableId(type = IdType.AUTO)
    @Schema(description="公告ID")
    private Long id;
    @Schema(description="公告标题")
    private String title;
    @Schema(description="公告内容（支持HTML/Markdown）")
    private String content;
    @Schema(description="公告权重（值越大越靠前）")
    private Integer priority;
    @Schema(description="是否置顶：1=置顶，0=不置顶")
    private Boolean pinned;
    @Schema(description="1可见，0不可见")
    private Boolean status;
    @Schema(description="发布人ID")
    private Long authorId;
    @Schema(description="发布人用户名")
    private String authorUsername;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

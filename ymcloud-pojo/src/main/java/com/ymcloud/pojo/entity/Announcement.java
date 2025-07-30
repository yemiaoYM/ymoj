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
    @Schema(description="公告类型：0=系统公告，1=比赛公告，2=其他")
    private Integer type;
    @Schema(description="所属比赛ID")
    private Long contestId;
    @Schema(description="公告权重（值越大越靠前）")
    private Integer priority;
    @Schema(description="是否置顶：1=置顶，0=不置顶")
    private Integer isPinned;
    @Schema(description="是否可见：1=可见，0=隐藏")
    private Integer isVisible;
    @Schema(description="发布时间（支持定时发布）")
    private LocalDateTime publishTime;
    @Schema(description="过期时间（为空则长期有效）")
    private LocalDateTime expireTime;
    @Schema(description="跳转链接（可选）")
    private String linkUrl;
    @Schema(description="发布人ID（user.id）")
    private Long authorId;
    @Schema(description="发布人用户名")
    private String authorUsername;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

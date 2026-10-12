package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 公告表。 */
@Data
public class Announcement {

    /** 公告ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告内容（支持HTML/Markdown） */
    private String content;

    /** 公告权重（值越大越靠前） */
    private Integer priority;

    /** 是否置顶：1=置顶，0=不置顶 */
    private Boolean pinned;

    /** 1可见，0不可见 */
    private Boolean status;

    /** 发布人ID */
    private Long authorId;

    /** 发布人用户名 */
    private String authorUsername;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}

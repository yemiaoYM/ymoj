package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: AnnouncementVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  15:16
 * @Description: 公告展示类
 */
@Data
public class AnnouncementVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 公告ID，用于列表身份和展开状态。 */
    private Long id;

    /**
     * 公告标题
     */
    private String title;
    /**
     * 公告内容（支持HTML/Markdown）
     */
    private String content;
    /**
     * 是否置顶：1=置顶，0=不置顶
     */
    private Boolean pinned;
    /**
     * 发布人用户名
     */
    private String authorUsername;
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

}

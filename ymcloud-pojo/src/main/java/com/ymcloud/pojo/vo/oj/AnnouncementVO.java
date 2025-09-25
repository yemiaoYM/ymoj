package com.ymcloud.pojo.vo.oj;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private Integer isPinned;
    /**
     * 发布时间（支持定时发布）
     */
    private LocalDateTime publishTime;
    /**
     * 过期时间（为空则长期有效）
     */
    private LocalDateTime expireTime;
    /**
     * 跳转链接（可选）
     */
    private String linkUrl;
    /**
     * 发布人用户名
     */
    private String authorUsername;

}

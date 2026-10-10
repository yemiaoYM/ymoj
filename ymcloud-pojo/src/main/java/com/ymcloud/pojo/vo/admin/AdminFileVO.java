package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件管理只返回安全业务元数据，不暴露对象或本地文件定位信息。
 */
@Data
public class AdminFileVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer bizType;
    private Long bizId;
    private Integer isPublic;
    private String storageProfile;
    private String uploaderUsername;
    private LocalDateTime createdTime;
    private String url;
    private String sha256;
    private Boolean canDownload;
    private Boolean canDelete;
}

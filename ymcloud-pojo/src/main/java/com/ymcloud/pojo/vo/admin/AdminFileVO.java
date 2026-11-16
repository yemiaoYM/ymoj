package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件管理只返回安全业务元数据，不暴露对象或本地文件定位信息
 */
@Data
public class AdminFileVO {

    /**
     * 对外 UUIDv4 文件标识，不返回内部主键
     */
    private String id;

    /**
     * 原始展示文件名
     */
    private String fileName;

    /**
     * 媒体类型
     */
    private String fileType;

    /**
     * 文件大小，单位字节
     */
    private Long fileSize;

    /**
     * FileBizType 对应业务类型编码
     */
    private Integer bizType;

    /**
     * 所属业务资源的内部编号，未关联时为空
     */
    private Long bizId;

    /**
     * 访问范围，0 为私有、1 为公开
     */
    private Integer isPublic;

    /**
     * 文件绑定的存储配置标识
     */
    private String storageKey;

    /**
     * 上传者用户名，系统导入可为空
     */
    private String uploaderUsername;

    /**
     * 文件创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 公开文件访问地址，私有文件为空
     */
    private String url;

    /**
     * 文件内容的 SHA-256 摘要
     */
    private String sha256;

    /**
     * 当前用户是否具有下载权限
     */
    private Boolean canDownload;

    /**
     * 当前用户是否具有删除权限
     */
    private Boolean canDelete;

}

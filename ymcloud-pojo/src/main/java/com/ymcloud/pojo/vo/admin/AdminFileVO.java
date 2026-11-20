package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.enums.FileBizType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 后台文件元数据及当前用户的操作权限
 */
@Data
public class AdminFileVO {

    /**
     * 文件主键
     */
    private Long id;

    /**
     * 存储配置下的对象键
     */
    private String fileName;

    /**
     * 上传时的原文件名
     */
    private String originalName;

    /**
     * 入库的文件后缀，包含前导点
     */
    private String fileSuffix;

    /**
     * 媒体类型
     */
    private String fileType;

    /**
     * 文件大小，单位字节
     */
    private Long fileSize;

    /**
     * 文件所属业务类型
     */
    private FileBizType bizType;

    /**
     * 所属业务资源的内部编号，未关联时为空
     */
    private Long bizId;

    /**
     * 文件绑定的存储配置标识
     */
    private String service;

    /**
     * 文件创建者的账号编号
     */
    private Long createdBy;

    /**
     * 文件创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 文件表登记的对象存储地址
     */
    private String url;

    /**
     * 当前用户是否具有下载权限
     */
    private Boolean canDownload;

    /**
     * 当前用户是否具有删除权限
     */
    private Boolean canDelete;

}

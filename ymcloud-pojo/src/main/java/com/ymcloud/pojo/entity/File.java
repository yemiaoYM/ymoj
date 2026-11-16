package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.FileBizType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-02
 * @Description: 文件对象元数据，内部主键用于业务关联，对外标识独立于对象位置
 */
@Data
public class File {

    /**
     * 文件ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对外 UUIDv4，32 位小写十六进制，与物理对象键相互独立
     */
    private String publicId;

    /**
     * 原始文件名，例如 data.zip
     */
    private String fileName;

    /**
     * 文件类型/MIME类型，例如 application/zip、image/png
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件扩展名，例如 zip、png、pdf
     */
    private String fileExt;

    /**
     * 存储配置名，切换默认配置不改变已有文件的位置
     */
    private String storageKey;

    /**
     * 对象存储桶
     */
    private String bucket;

    /**
     * 不可变对象键
     */
    private String objectKey;

    /**
     * 文件内容 SHA-256
     */
    private String sha256;

    /**
     * 上传者用户ID
     */
    private Long uploaderId;

    /**
     * 上传者用户名
     */
    private String uploaderUsername;

    /**
     * 业务类型：0=通用，1=题目附件，2=比赛附件，3=公告附件，4=图片，5=测试数据
     */
    private FileBizType bizType;

    /**
     * 业务ID，例如题目ID、比赛ID
     */
    private Long bizId;

    /**
     * 是否公开访问：0=私有，1=公开
     */
    private Integer isPublic;

    /**
     * 上传时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}

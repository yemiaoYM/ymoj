package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.FileBizType;
import lombok.Data;

import java.time.LocalDateTime;

/** 文件表。 */
@Data
public class File {

    /** 文件ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名，例如 data.zip */
    private String fileName;

    /** 文件类型/MIME类型，例如 application/zip、image/png */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件扩展名，例如 zip、png、pdf */
    private String fileExt;

    /** 文件存储相对路径，例如 public/problem/1001/data.zip */
    private String storagePath;

    /** 存储方式：0=本地，1=OSS，2=历史MinIO，3=S3兼容存储 */
    private Integer storageType;

    /** 存储配置名，切换默认配置不改变已有文件的位置 */
    private String storageProfile;

    /** 对象存储桶 */
    private String bucket;

    /** 不可变对象键 */
    private String objectKey;

    /** 文件内容 SHA-256 */
    private String sha256;

    /** 迁移前本地路径，仅供回退与核对 */
    private String legacyPath;

    /** 公开文件的稳定代理URL，私有文件为空 */
    private String url;

    /** 上传者用户ID */
    private Long uploaderId;

    /** 上传者用户名 */
    private String uploaderUsername;

    /** 业务类型：0=通用，1=题目附件，2=比赛附件，3=公告附件，4=图片，5=测试数据 */
    private FileBizType bizType;

    /** 业务ID，例如题目ID、比赛ID */
    private Long bizId;

    /** 是否公开访问：0=私有，1=公开 */
    private Integer isPublic;

    /** 上传时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}

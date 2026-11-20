package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ymcloud.pojo.enums.FileBizType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-02
 * @Description: 文件对象元数据，主键同时用于业务关联与文件访问
 */
@Data
@TableName("file")
public class FileInfo {

    /**
     * 文件ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 原始文件名，例如 data.zip
     */
    private String originalName;

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
    private String fileSuffix;

    /**
     * 存储配置名，切换默认配置不改变已有文件的位置
     */
    private String service;

    /**
     * 不可变对象键
     */
    private String fileName;

    /**
     * 文件访问地址
     */
    private String url;

    /**
     * 创建者用户 ID
     */
    private Long createdBy;

    /**
     * 最后修改文件元数据的用户 ID
     */
    private Long updatedBy;

    /**
     * 业务类型：0=通用，1=题目附件，2=比赛附件，3=公告附件，4=图片，5=测试数据
     */
    private FileBizType bizType;

    /**
     * 业务ID，例如题目ID、比赛ID
     */
    private Long bizId;

    /**
     * 上传时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}

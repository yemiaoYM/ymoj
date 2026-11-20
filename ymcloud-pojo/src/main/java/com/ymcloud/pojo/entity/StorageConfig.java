package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 持久化存储配置，凭据仅用于创建存储客户端
 */
@Getter
@Setter
public class StorageConfig {

    /**
     * 存储配置唯一标识
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 客户端配置标识 */
    private String configKey;

    /**
     * 是否为新上传使用的默认配置，数据库限制最多一条默认记录
     */
    private String status;

    /**
     * 对象存储访问端点
     */
    private String endpoint;

    /**
     * 签名所用地域标识
     */
    private String region;

    /** 对象存储桶 */
    private String bucketName;

    /** 对象键前缀 */
    private String prefix;

    /** 自定义访问域名 */
    private String domain;

    /** 是否使用 HTTPS，Y 或 N */
    private String isHttps;

    /** 桶访问策略，0 private、1 public、2 custom */
    private String accessPolicy;

    /**
     * 存储服务的访问标识，不得对外返回
     */
    private String accessKey;

    /**
     * 存储服务的访问密钥，不得对外返回
     */
    private String secretKey;

    /**
     * 管理备注，可为空
     */
    private String remark;

    /** 创建配置的用户 ID */
    private Long createdBy;

    /** 最后修改配置的用户 ID */
    private Long updatedBy;

    /**
     * 配置创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 配置最近更新时间
     */
    private LocalDateTime updatedTime;
}

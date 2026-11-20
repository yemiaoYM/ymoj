package com.ymcloud.pojo.vo.common;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 后台存储配置视图，不返回访问凭据
 */
@Getter
@Setter
public class StorageConfigVO {

    /** 十进制配置主键，避免前端丢失精度 */
    private String id;

    /**
     * 存储配置唯一标识
     */
    private String configKey;

    /**
     * 对象存储访问端点
     */
    private String endpoint;

    /** 是否使用 HTTPS，Y 为启用 */
    private String isHttps;

    /**
     * 签名所用地域标识
     */
    private String region;

    /** 对象存储目录前缀 */
    private String prefix;

    /** 自定义访问域名 */
    private String domain;

    /** 桶策略，0 private、1 public、2 custom */
    private String accessPolicy = "0";

    /** 对象存储桶 */
    private String bucketName;

    /**
     * 管理备注，可为空
     */
    private String remark;

    /**
     * 是否用于新上传的默认配置
     */
    private String status;

    /**
     * 是否被已有文件引用
     */
    private Boolean referenced;

    /**
     * 是否已配置完整凭据，不返回凭据本身
     */
    private Boolean credentialConfigured;

    /**
     * 当前配置是否满足删除条件
     */
    private Boolean canDelete;
}

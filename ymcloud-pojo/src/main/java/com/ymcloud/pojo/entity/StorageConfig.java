package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.StorageDriverType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 持久化存储配置，仅保存凭据密文且不生成 toString
 */
@Getter
@Setter
public class StorageConfig {

    /**
     * 存储配置唯一标识
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 管理端展示名称
     */
    private String name;

    /**
     * 是否为新上传使用的默认配置，数据库限制最多一条默认记录
     */
    private Boolean isDefault;

    /**
     * 服务商描述，不参与驱动选择
     */
    private String provider;

    /**
     * 显式指定的访问协议
     */
    private StorageDriverType driverType;

    /**
     * 对象存储访问端点，本地驱动不使用
     */
    private String endpoint;

    /**
     * 签名所用地域标识
     */
    private String region;

    /**
     * 公开业务文件所在桶，桶自身保持私有
     */
    private String publicBucket;

    /**
     * 私有业务文件所在桶
     */
    private String privateBucket;

    /**
     * S3 是否使用路径式桶寻址
     */
    private Boolean pathStyle;

    /**
     * 认证加密后的访问标识，不得对外返回
     */
    private String encryptedAccessKey;

    /**
     * 认证加密后的访问密钥，不得对外返回
     */
    private String encryptedSecretKey;

    /**
     * 管理备注，可为空
     */
    private String remark;

    /**
     * 当前配置修订号
     */
    private Long revision;

    /**
     * 最近通过连接测试的修订号，未通过时为空
     */
    private Long testedRevision;

    /**
     * 最近通过连接测试的时间，未通过时为空
     */
    private LocalDateTime testedAt;

    /**
     * 配置创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 配置最近更新时间
     */
    private LocalDateTime updatedTime;
}

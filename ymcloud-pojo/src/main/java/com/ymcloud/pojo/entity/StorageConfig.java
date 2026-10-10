package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 数据库存储配置；凭据仅保存认证加密后的密文，不生成 toString。 */
@Getter
@Setter
public class StorageConfig {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String provider;
    private String endpoint;
    private String region;
    private String publicBucket;
    private String privateBucket;
    private Boolean pathStyle;
    private String encryptedAccessKey;
    private String encryptedSecretKey;
    private String remark;
    private Long revision;
    private Long testedRevision;
    private LocalDateTime testedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

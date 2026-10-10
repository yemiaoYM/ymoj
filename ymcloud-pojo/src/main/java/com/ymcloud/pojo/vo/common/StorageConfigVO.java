package com.ymcloud.pojo.vo.common;

import lombok.Getter;
import lombok.Setter;

/** 后台配置展示，接口始终不返回任何明文或密文凭据。 */
@Getter
@Setter
public class StorageConfigVO {

    private String key;
    private String name;
    private String provider;
    private String endpoint;
    private String region;
    private String publicBucket;
    private String privateBucket;
    private Boolean pathStyle;
    private String remark;
    private Boolean active;
    private String source;
    private Boolean referenced;
    private Boolean credentialConfigured;
    private Boolean canDelete;
}

package com.ymcloud.pojo.dto.admin;

import lombok.Getter;
import lombok.Setter;

/** 编辑请求；空白凭据沿用已有值，不参与 toString。 */
@Getter
@Setter
public class StorageConfigDTO {

    private String key;
    private String name;
    private String provider;
    private String endpoint;
    private String region;
    private String publicBucket;
    private String privateBucket;
    private Boolean pathStyle;
    private String accessKey;
    private String secretKey;
    private String remark;
}

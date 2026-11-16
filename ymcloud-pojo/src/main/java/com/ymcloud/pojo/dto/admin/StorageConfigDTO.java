package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.enums.StorageDriverType;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置编辑请求，空白凭据保留原值且不参与 toString
 */
@Getter
@Setter
public class StorageConfigDTO {

    /**
     * 存储配置唯一标识，编辑时不可修改
     */
    private String key;

    /**
     * 管理端展示名称
     */
    private String name;

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
     * 新访问标识，编辑时空白表示保留原值
     */
    private String accessKey;

    /**
     * 新访问密钥，编辑时空白表示保留原值
     */
    private String secretKey;

    /**
     * 管理备注，可为空
     */
    private String remark;
}

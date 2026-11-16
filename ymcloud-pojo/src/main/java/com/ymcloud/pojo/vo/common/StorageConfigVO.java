package com.ymcloud.pojo.vo.common;

import com.ymcloud.pojo.enums.StorageDriverType;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 后台存储配置视图，不返回明文或密文凭据
 */
@Getter
@Setter
public class StorageConfigVO {

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
     * 管理备注，可为空
     */
    private String remark;

    /**
     * 是否用于新上传的默认配置
     */
    private Boolean active;

    /**
     * 配置来源：config 或 database
     */
    private String source;

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

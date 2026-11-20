package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** 修改配置或切换默认时使用的主键 */
    private Long id;

    /**
     * 存储配置唯一标识
     */
    @NotBlank(message = "请输入配置标识")
    @Size(max = 64)
    private String configKey;

    /**
     * 对象存储访问端点
     */
    @NotBlank(message = "请输入存储端点")
    @Size(max = 512)
    private String endpoint;

    /** 是否使用 HTTPS，Y 为启用 */
    @NotBlank(message = "请选择存储协议")
    @Pattern(regexp = "[YN]", message = "存储协议标记错误")
    private String isHttps;

    /**
     * 签名所用地域标识
     */
    @Size(max = 64)
    private String region;

    /** 对象存储目录前缀 */
    @Size(max = 255)
    private String prefix;

    /** 自定义访问域名 */
    @Size(max = 255)
    private String domain;

    /** 桶策略，0 private、1 public、2 custom */
    @NotBlank(message = "请输入桶策略")
    @Pattern(regexp = "[012]", message = "桶策略编码错误")
    private String accessPolicy = "0";

    /** 对象存储桶 */
    @NotBlank(message = "请输入存储桶")
    @Size(max = 128)
    private String bucketName;

    /**
     * 新访问标识，编辑时空白表示保留原值
     */
    @Size(max = 255)
    private String accessKey;

    /**
     * 新访问密钥，编辑时空白表示保留原值
     */
    @Size(max = 255)
    private String secretKey;

    /**
     * 管理备注，可为空
     */
    @Size(max = 500)
    private String remark;
}

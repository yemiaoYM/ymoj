package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 已实现的存储访问协议，与服务商和配置标识独立
 */
@Getter
public enum StorageDriverType {

    LOCAL("LOCAL"),
    S3("S3"),
    ALIYUN_OSS("ALIYUN_OSS");

    /**
     * 数据库及接口使用的固定驱动编码
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 定义驱动的持久化及传输编码
     * @param code 明确的访问协议标识
     */
    StorageDriverType(String code) {
        this.code = code;
    }
}

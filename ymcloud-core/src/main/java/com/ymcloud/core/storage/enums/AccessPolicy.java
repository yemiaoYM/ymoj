package com.ymcloud.core.storage.enums;

import com.ymcloud.core.storage.exception.S3StorageException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 存储服务的桶访问策略编码
 */
@Getter
@AllArgsConstructor
public enum AccessPolicy {

    PRIVATE("0"),
    PUBLIC("1"),
    CUSTOM("2");

    private final String code;

    /**
     * 将存储配置中的权限编码转换为访问策略
     * @param code 桶权限编码
     * @return 对应的访问策略
     */
    public static AccessPolicy fromCode(String code) {
        for (AccessPolicy policy : values()) {
            if (policy.code.equals(code)) {
                return policy;
            }
        }
        throw new S3StorageException("未知的存储访问策略");
    }
}

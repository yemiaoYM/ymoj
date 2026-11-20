package com.ymcloud.core.storage.config;

import com.ymcloud.core.storage.enums.AccessPolicy;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 桶访问策略与 ACL 开关
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public final class AccessControlPolicyConfig
        implements Config<AccessControlPolicyConfig, AccessControlPolicyConfig.AccessControlPolicyConfigBuilder> {

    public static final AccessControlPolicyConfig DEFAULT = AccessControlPolicyConfig.builder().build();

    private final boolean enabled;

    @Builder.Default
    private final AccessPolicy accessPolicy = AccessPolicy.PUBLIC;

    /**
     * 创建独立的配置副本
     * @return 当前配置的副本
     */
    @Override
    public AccessControlPolicyConfig copy() {
        return toBuilder().build();
    }
}

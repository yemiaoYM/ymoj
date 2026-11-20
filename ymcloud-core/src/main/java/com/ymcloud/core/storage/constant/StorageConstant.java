package com.ymcloud.core.storage.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 对象存储云端点寻址标识
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageConstant {

    public static final List<String> CLOUD_SERVICE = List.of("aliyun", "qcloud", "qiniu", "obs", "amazonaws");
}

package com.ymcloud.core.storage.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 为 SDK 端点、自定义域名和桶生成访问地址
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BucketUrlUtil {

    /**
     * 使用配置的协议生成端点地址
     * @param useHttps 是否使用 HTTPS
     * @param address 主机和可选端口，可以带协议头
     * @return SDK 端点地址
     */
    public static URI endpoint(boolean useHttps, String address) {
        String host = address.trim().replaceFirst("(?i)^https?://", "").replaceFirst("/+$", "");
        return URI.create((useHttps ? "https://" : "http://") + host);
    }

    /**
     * 根据寻址方式将桶名加入主机或路径
     * @param base 服务地址
     * @param bucket 目标桶名
     * @param pathStyle 是否采用路径式访问
     * @return 对象键之前的桶地址
     */
    public static String bucketUrl(URI base, String bucket, boolean pathStyle) {
        if (pathStyle) {
            return base + "/" + bucket;
        }
        return base.getScheme() + "://" + bucket + "." + base.getRawAuthority() + base.getRawPath();
    }
}

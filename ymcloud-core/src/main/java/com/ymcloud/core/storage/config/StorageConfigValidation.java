package com.ymcloud.core.storage.config;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.pojo.enums.StorageDriverType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储端点允许 HTTPS，HTTP 仅用于明确的回环开发地址
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageConfigValidation {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final Set<String> METADATA = Set.of("169.254.169.254", "100.100.100.200",
            "fd00:ec2::254", "[fd00:ec2::254]", "metadata.google.internal", "instance-data");
    private static final String IPV6_METADATA = "fd000ec2000000000000000000000254";

    /**
     * 校验存储配置标识，仅允许 1～64 位字母、数字、下划线和连字符
     * @param key 待保存或读取的配置标识
     * @throws FileException 标识为空或格式不合法
     */
    public static void validateKey(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new FileException("非法存储配置名", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 校验存储驱动参数与端点访问边界，本地驱动不需要远端连接参数
     * @param profile 待校验的客户端配置
     * @param resolveDns 是否解析域名并校验解析地址，仅连通测试启用
     * @throws FileException 远端配置不完整、端点不允许访问或域名解析失败
     */
    public static void validate(StorageProperties.Profile profile, boolean resolveDns) {
        if (profile.getDriverType() == null) {
            throw new FileException("存储驱动类型缺失或不受支持", HttpStatus.BAD_REQUEST);
        }
        if (profile.getDriverType() == StorageDriverType.LOCAL) {
            return;
        }
        try {
            URI endpoint = URI.create(profile.getEndpoint());
            String host = endpoint.getHost();
            if (host == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null || endpoint.getPort() == 0 || endpoint.getPort() > 65535
                    || (endpoint.getPath() != null && !endpoint.getPath().isEmpty() && !"/".equals(endpoint.getPath()))
                    || !Set.of("http", "https").contains(endpoint.getScheme())) {
                throw new IllegalArgumentException();
            }
            host = host.toLowerCase(Locale.ROOT);
            if (METADATA.contains(host) || ("http".equals(endpoint.getScheme()) && !LOOPBACK.contains(host))) {
                throw new IllegalArgumentException();
            }
            // 数字地址在保存时也校验
            // 域名解析只在连接测试执行，启动不联网
            if (resolveDns || host.matches("[0-9.]+") || host.contains(":")) {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()
                            || METADATA.contains(address.getHostAddress())
                            || IPV6_METADATA.equals(HexFormat.of().formatHex(address.getAddress()))) {
                        throw new IllegalArgumentException();
                    }
                    if (address.isLoopbackAddress() && !LOOPBACK.contains(host)) {
                        throw new IllegalArgumentException();
                    }
                }
            }
            if (!validCredential(profile.getAccessKey()) || !validCredential(profile.getSecretKey())
                    || profile.getRegion() == null || !profile.getRegion().matches("[a-zA-Z0-9_-]{1,64}")
                    || profile.getTimeout() == null || profile.getTimeout().isZero() || profile.getTimeout().isNegative()
                    || !validBucket(profile.getPublicBucket()) || !validBucket(profile.getPrivateBucket())
                    || profile.getPublicBucket().equals(profile.getPrivateBucket())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException | UnknownHostException e) {
            throw new FileException("对象存储配置不完整或不合法", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 检查凭据是否非空、长度不超过 1024 且不含控制字符
     * @param value 待检查的访问标识或密钥，可为空
     * @return 是否满足凭据格式要求
     */
    private static boolean validCredential(String value) {
        return value != null && !value.isBlank() && value.length() <= 1024
                && value.chars().noneMatch(Character::isISOControl);
    }

    /**
     * 检查存储桶命名规则，排除连续点与 IPv4 地址形式
     * @param value 待检查的存储桶名称，可为空
     * @return 是否满足 3～63 位存储桶命名要求
     */
    private static boolean validBucket(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                && !value.contains("..") && !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
    }
}

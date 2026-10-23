package com.ymcloud.core.storage;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-11
 * @Description: 存储端点允许 HTTPS，HTTP 仅用于明确的回环开发地址。
 */
public final class StorageConfigValidation {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final Set<String> METADATA = Set.of("169.254.169.254", "100.100.100.200",
            "fd00:ec2::254", "[fd00:ec2::254]", "metadata.google.internal", "instance-data");
    private static final String IPV6_METADATA = "fd000ec2000000000000000000000254";

    private StorageConfigValidation() {
    }

    public static void validateKey(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new FileException("非法存储配置名", HttpStatus.BAD_REQUEST);
        }
    }

    public static void validate(StorageProperties.Profile profile, boolean resolveDns) {
        if ("local".equals(profile.getType())) {
            return;
        }
        try {
            if (!Set.of("s3", "oss").contains(profile.getType())) {
                throw new IllegalArgumentException();
            }
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
            // 数字地址在保存时也校验；域名解析只在连接测试执行，启动不联网。
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
            throw new FileException("对象存储配置不完整或不合法，请检查地址、凭据、区域与独立存储桶", HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean validCredential(String value) {
        return value != null && !value.isBlank() && value.length() <= 1024
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validBucket(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                && !value.contains("..") && !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
    }
}

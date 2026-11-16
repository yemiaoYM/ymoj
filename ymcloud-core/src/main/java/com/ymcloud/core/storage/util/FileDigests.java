package com.ymcloud.core.storage.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 流式计算文件 SHA-256 摘要，统一使用小写十六进制表示
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileDigests {

    /**
     * 创建独立的 SHA-256 摘要计算器供分块上传累计内容
     * @return 无共享状态的 SHA-256 计算器
     */
    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    /**
     * 读取到流末尾并计算 SHA-256，不关闭调用方提供的流
     * @param input 待计算摘要的内容流
     * @return 小写十六进制 SHA-256 摘要
     * @throws IOException 读取内容失败
     */
    public static String sha256(InputStream input) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}

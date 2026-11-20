package com.ymcloud.pojo.bo.judge;

import lombok.Data;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: testlib 校验器的语言、源码和编译摘要
 */
@Data
public class SpjProgram {

    /**
     * 参与源码摘要计算的 testlib 修订标识
     */
    private static final String TESTLIB_REVISION = "125ca845718ad9e18ae8d3dfac8f0a9f09033e1e";

    /**
     * 校验器源码的最大字符数
     */
    private static final int MAX_CODE_LENGTH = 65535;

    /**
     * 校验器编译语言
     */
    private String language;

    /**
     * 用于编译的校验器源码
     */
    @ToString.Exclude
    private String code;

    /**
     * 固定工具链、语言和源码共同确定的 SHA-256 摘要
     */
    private String digest;

    /**
     * 使用固定编译协议、语言和完整源码标识同一个校验程序版本
     * @return 小写十六进制 SHA-256
     */
    public String sourceDigest() {
        try {
            byte[] source = ("spj-v1:cpp17:O2:testlib:" + TESTLIB_REVISION + "\n" + language + "\n" + code)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    /**
     * 在独立消息入口核对支持语言、有界源码及其版本摘要
     * @throws IllegalArgumentException 校验程序快照无效
     */
    public void validate() {
        if (!"C++".equals(language)
                || code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH
                || !sourceDigest().equals(digest)) {
            throw new IllegalArgumentException("SPJ 源码、语言或版本摘要无效");
        }
    }
}

package com.ymcloud.pojo.contract.judge;

import lombok.Data;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: testlib 校验器源码快照及固定编译、文件参数协议
 */
@Data
public class SpjProgram {

    public static final String COMPILE_QUEUE = "judge.spj.compile.queue";
    public static final String TESTLIB_REVISION = "125ca845718ad9e18ae8d3dfac8f0a9f09033e1e";
    public static final int MAX_CODE_LENGTH = 65535;
    public static final long COMPILE_TIMEOUT_MILLIS = 45000;
    public static final long TIME_LIMIT_MILLIS = 2000;
    public static final long MEMORY_LIMIT_BYTES = 256L * 1024 * 1024;
    public static final int ACCEPTED = 0;
    public static final int WRONG_ANSWER = 1;
    public static final int PRESENTATION_ERROR = 2;
    public static final int SYSTEM_ERROR = 3;

    private String language;

    @ToString.Exclude
    private String code;

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

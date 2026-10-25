package com.ymcloud.judge.strategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 分块比较标准答案，保留既有空白规则并完整读取以验证文件哈希。
 */
public final class OutputComparator {

    /**
     * 答案比较工具不创建实例。
     */
    private OutputComparator() {}

    /**
     * 流式比对标准答案，不因提前发现不同而跳过文件完整性校验。
     * @param expected UTF-8 标准答案流，调用结束时关闭
     * @param actual 已受沙箱输出上限约束的程序输出
     * @return 忽略行末空白及末尾空行后是否相等
     * @throws IOException 文件读取、哈希校验或线程中断失败
     */
    public static boolean matches(InputStream expected, String actual) throws IOException {
        String normalized = normalize(actual);
        try (InputStreamReader reader = new InputStreamReader(expected, StandardCharsets.UTF_8)) {
            StringBuilder whitespace = new StringBuilder();
            long newlines = 0;
            long position = 0;
            boolean equal = true;
            boolean previousCr = false;
            boolean whitespaceOverflow = false;
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("标准答案比对被中断");
                }
                for (int offset = 0; offset < count; offset++) {
                    char character = buffer[offset];
                    if (character == '\n' && previousCr) {
                        previousCr = false;
                        continue;
                    }
                    previousCr = character == '\r';
                    if (character == '\r' || character == '\n') {
                        newlines++;
                        whitespace.setLength(0);
                        whitespaceOverflow = false;
                    } else if (isBlankChar(character)) {
                        if (equal && whitespace.length() <= normalized.length()) {
                            whitespace.append(character);
                        } else {
                            whitespaceOverflow = true;
                        }
                    } else {
                        if (equal) {
                            if (newlines > normalized.length() - position || whitespaceOverflow) {
                                equal = false;
                            } else {
                                while (newlines-- > 0) {
                                    equal &= at(normalized, position++, '\n');
                                }
                                for (int index = 0; index < whitespace.length(); index++) {
                                    equal &= at(normalized, position++, whitespace.charAt(index));
                                }
                                equal &= at(normalized, position++, character);
                            }
                        }
                        newlines = 0;
                        whitespace.setLength(0);
                        whitespaceOverflow = false;
                    }
                }
            }
            return equal && position == normalized.length();
        }
    }

    /**
     * 单次遍历统一换行并去除行末空白，不按行创建数组和大量临时字符串。
     * @param value 程序输出；null 按空串处理
     * @return 保留行首和行内空白的归一化文本
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        int lineEnd = 0;
        int contentEnd = 0;
        boolean previousCr = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' && previousCr) {
                previousCr = false;
                continue;
            }
            previousCr = character == '\r';
            if (character == '\r' || character == '\n') {
                normalized.setLength(lineEnd);
                normalized.append('\n');
                lineEnd = normalized.length();
            } else {
                normalized.append(character);
                if (!isBlankChar(character)) {
                    lineEnd = normalized.length();
                    contentEnd = lineEnd;
                }
            }
        }
        return normalized.substring(0, contentEnd);
    }

    /**
     * 在有界的实际输出中比较一个字符。
     * @param value 实际输出
     * @param position 标准答案归一化后的字符位置
     * @param character 当前标准字符
     * @return 位置有效且字符相等
     */
    private static boolean at(String value, long position, char character) {
        return position >= 0 && position < value.length() && value.charAt((int) position) == character;
    }

    /**
     * 对齐原 Hutool CharUtil.isBlankChar，避免既有题目因 Unicode 空白规则变化产生 WA。
     * @param character 待检查的 UTF-16 字符
     * @return 是否属于项目已有的可忽略空白
     */
    private static boolean isBlankChar(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || character == 0xFEFF || character == 0x202A || character == 0
                || character == 0x3164 || character == 0x2800 || character == 0x200C || character == 0x180E;
    }
}

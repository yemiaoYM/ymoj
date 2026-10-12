package com.ymcloud.judge.strategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 比较期望文件时只保留有界的待确认行末空白，完整读到 EOF 触发缓存校验。
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(InputStream expected, String actual) throws IOException {
        String normalized = normalize(actual);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(expected, StandardCharsets.UTF_8))) {
            StringBuilder whitespace = new StringBuilder();
            long newlines = 0;
            long position = 0;
            boolean equal = true;
            boolean previousCr = false;
            boolean whitespaceOverflow = false;
            int character;
            while ((character = reader.read()) != -1) {
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
                        whitespace.append((char) character);
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
                            equal &= at(normalized, position++, (char) character);
                        }
                    }
                    newlines = 0;
                    whitespace.setLength(0);
                    whitespaceOverflow = false;
                }
            }
            return equal && position == normalized.length();
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int count = lines.length;
        for (int index = 0; index < count; index++) {
            int end = lines[index].length();
            while (end > 0 && isBlankChar(lines[index].charAt(end - 1))) {
                end--;
            }
            lines[index] = lines[index].substring(0, end);
        }
        while (count > 0 && lines[count - 1].chars().allMatch(OutputComparator::isBlankChar)) {
            count--;
        }
        return String.join("\n", Arrays.copyOf(lines, count));
    }

    private static boolean at(String value, long position, char character) {
        return position >= 0 && position < value.length() && value.charAt((int) position) == character;
    }

    /**
     * 对齐原 Hutool CharUtil.isBlankChar，避免既有题目因 Unicode 空白规则变化产生 WA。
     */
    private static boolean isBlankChar(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || character == 0xFEFF || character == 0x202A || character == 0
                || character == 0x3164 || character == 0x2800 || character == 0x200C || character == 0x180E;
    }
}

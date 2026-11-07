package com.ymcloud.judge.strategy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 分块比较标准答案，保留既有空白规则并完整读取以验证文件哈希
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutputComparator {

    /**
     * 同时顺序读取标准答案及原始输出，内存占用不随输出或空白行长度增长
     * @param expected UTF-8 标准答案流，调用结束时关闭
     * @param actual UTF-8 原始输出流，调用结束时关闭
     * @return 忽略行末空白及末尾空行后是否相等
     * @throws IOException 文件读取、哈希校验或线程中断失败
     */
    public static boolean matches(InputStream expected, InputStream actual) throws IOException {
        try (CharacterStream left = new CharacterStream(expected); CharacterStream right = new CharacterStream(actual)) {
            int expectedChar = left.read();
            int actualChar = right.read();
            boolean equal = true;
            boolean whitespaceEqual = true;
            while (expectedChar != -1 || actualChar != -1) {
                boolean expectedEnd = expectedChar == -1 || expectedChar == '\n';
                boolean actualEnd = actualChar == -1 || actualChar == '\n';
                if (expectedEnd && actualEnd) {
                    if (expectedChar == -1 || actualChar == -1) {
                        equal = left.onlyBlankRemaining() && right.onlyBlankRemaining();
                        break;
                    }
                    whitespaceEqual = true;
                } else if (expectedEnd && isBlankChar(actualChar)) {
                    actualChar = right.read();
                    continue;
                } else if (actualEnd && isBlankChar(expectedChar)) {
                    expectedChar = left.read();
                    continue;
                } else if (isBlankChar(expectedChar) && isBlankChar(actualChar)) {
                    // 暂不确认空白差异，只有后续还有正文时这些空白才参与比较
                    whitespaceEqual &= expectedChar == actualChar;
                } else if (!whitespaceEqual || expectedChar != actualChar) {
                    equal = false;
                    break;
                }
                expectedChar = left.read();
                actualChar = right.read();
            }
            // 即使答案不同也完整消费标准答案，让固定版本的长度及哈希校验生效
            left.drain();
            right.drain();
            return equal;
        }
    }

    /**
     * 对齐原 Hutool CharUtil.isBlankChar，避免既有题目因 Unicode 空白规则变化产生 WA
     * @param character 待检查的 UTF-16 字符
     * @return 是否属于项目已有的可忽略空白
     */
    private static boolean isBlankChar(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || character == 0xFEFF || character == 0x202A || character == 0
                || character == 0x3164 || character == 0x2800 || character == 0x200C || character == 0x180E;
    }

    /**
     * 使用固定字符缓冲统一换行，不为超长行或连续空白创建字符串
     */
    private static final class CharacterStream implements AutoCloseable {

        private final InputStreamReader reader;
        private final char[] buffer = new char[8192];
        private int position;
        private int length;
        private boolean previousCr;

        /**
         * 绑定 UTF-8 原始文件流
         * @param input 由本字符流负责关闭的输入
         */
        private CharacterStream(InputStream input) {
            reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        }

        /**
         * 读取统一换行后的字符，每次补充缓冲时检查任务中断
         * @return UTF-16 字符或 EOF 标记 -1
         * @throws IOException 读取、完整性校验或中断失败
         */
        private int read() throws IOException {
            while (true) {
                if (position == length) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("标准答案比对被中断");
                    }
                    length = reader.read(buffer);
                    position = 0;
                    if (length == -1) {
                        length = 0;
                        return -1;
                    }
                }
                char character = buffer[position++];
                if (character == '\n' && previousCr) {
                    previousCr = false;
                    continue;
                }
                previousCr = character == '\r';
                return previousCr ? '\n' : character;
            }
        }

        /**
         * 检查剩余文件是否仅含可忽略的末尾空白
         * @return 后续没有正文时为 true
         * @throws IOException 读取或中断失败
         */
        private boolean onlyBlankRemaining() throws IOException {
            boolean blank = true;
            int character;
            while ((character = read()) != -1) {
                blank &= isBlankChar(character);
            }
            return blank;
        }

        /**
         * 消费尚未读取的内容，以完成文件容量和哈希校验
         * @throws IOException 读取、校验或中断失败
         */
        private void drain() throws IOException {
            while (read() != -1) {
                // 读取边界执行完整性校验
            }
        }

        /**
         * 关闭底层文件及可能的网络响应
         * @throws IOException 底层资源关闭失败
         */
        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}

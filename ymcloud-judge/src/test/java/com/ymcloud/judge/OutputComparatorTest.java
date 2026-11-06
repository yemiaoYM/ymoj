package com.ymcloud.judge;

import com.ymcloud.judge.strategy.OutputComparator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 验证空白兼容性、大文件比对及完整性校验边界。
 */
class OutputComparatorTest {

    /**
     * 固定期望值独立验证空行、跨缓冲区 CRLF 和行内空白，避免自比较掩盖语义回归。
     * @throws Exception 文件比对失败
     */
    @Test
    void normalizationKeepsLeadingAndInternalWhitespaceAcrossChunks() throws Exception {
        assertEquals("\n a\n\nb c", normalize("\r\n a \r\n \t\r\nb c\t\n \n"));
        String prefix = "x".repeat(8191);
        assertTrue(OutputComparator.matches(stream(prefix + "\r\nnext \r\n"), stream(prefix + "\nnext")));
        assertFalse(OutputComparator.matches(stream("a b"), stream("ab")));
        assertEquals("", normalize(" \r\n".repeat(300000)));
    }

    /**
     * 生成 32 MiB 标准答案尾部空白，验证大文件按小块读取而不是整份加载。
     * @throws Exception 标准答案读取或比对失败
     */
    @Test
    void largeExpectedFileUsesBoundedReadsAndCanBeInterrupted() throws Exception {
        InputStream spaces = new InputStream() {
            private long remaining = 32L * 1024 * 1024;

            @Override
            public int read() {
                return remaining-- > 0 ? ' ' : -1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                assertTrue(length <= 8192, "标准答案不能使用与文件等大的读取缓冲");
                if (remaining == 0) {
                    return -1;
                }
                int count = (int) Math.min(length, remaining);
                Arrays.fill(buffer, offset, offset + count, (byte) ' ');
                remaining -= count;
                return count;
            }
        };
        assertTrue(OutputComparator.matches(new SequenceInputStream(stream("answer"), spaces), stream("answer\n")));
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> OutputComparator.matches(stream("answer"), stream("answer")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 用原字符串算法逐项验证换行、Unicode 空白与行内空白的组合
     * @throws Exception 流式比对失败
     */
    @Test
    void streamingComparisonMatchesEstablishedRulesIncludingLeadingWhitespaceAndEmptyLines() throws Exception {
        String[] values = {"", " ", "\r\n\n", "a", "a\n", "a  \r\n\r\n", " a\n", "\na\n", "a\nb",
                "a\n\nb", "a b", "中文😀 \r\n尾部\t\n\n", "answer\u00a0\n\ufeff\n",
                "answer\u202a\u0000\u3164\u2800\u200c\u180e\n"};
        for (String expected : values) {
            for (String actual : values) {
                assertEquals(normalize(expected).equals(normalize(actual)),
                        OutputComparator.matches(stream(expected), stream(actual)), expected + " / " + actual);
            }
        }
    }

    /**
     * 用固定随机种子覆盖不同换行及空白，保持原有归一化行为
     * @throws Exception 流式比对失败
     */
    @Test
    void randomWhitespaceCasesAndVeryLongTrailingWhitespaceStayCompatible() throws Exception {
        Random random = new Random(19L);
        String alphabet = "ab \t\r\n界\u00a0\ufeff\u202a\u0000\u3164\u2800\u200c\u180e";
        for (int trial = 0; trial < 1000; trial++) {
            StringBuilder expected = new StringBuilder();
            StringBuilder actual = new StringBuilder();
            int length = random.nextInt(50);
            for (int index = 0; index < length; index++) {
                expected.append(alphabet.charAt(random.nextInt(alphabet.length())));
                actual.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertEquals(normalize(expected.toString()).equals(normalize(actual.toString())),
                    OutputComparator.matches(stream(expected.toString()), stream(actual.toString())));
            String equivalent = expected.toString().replace("\r\n", "\n").replace('\r', '\n')
                    .replace("\n", " \t\r\n") + " \u00a0\r\n";
            assertTrue(OutputComparator.matches(stream(expected.toString()), stream(equivalent)));
        }
        assertTrue(OutputComparator.matches(stream("answer" + " ".repeat(2 * 1024 * 1024)), stream("answer\n")));
    }

    @Test
    void wrongAnswerStillConsumesExpectedToEofForIntegrityVerification() throws Exception {
        class TrackedStream extends ByteArrayInputStream {
            boolean eof;
            TrackedStream() {
                super("different\nremaining".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                int result = super.read(bytes, offset, length);
                eof |= result < 0;
                return result;
            }
        }
        TrackedStream expected = new TrackedStream();
        assertFalse(OutputComparator.matches(expected, stream("wrong")));
        assertTrue(expected.eof);
    }

    /**
     * 两侧均为 20 MB 长行时只按固定块读取，末尾一处不同仍必须得到 WA
     * @throws Exception 输出比对失败
     */
    @Test
    void largeActualAndExpectedAreComparedWithoutWholeLineBuffers() throws Exception {
        assertTrue(OutputComparator.matches(repeatedOutput("\n"), repeatedOutput(" \r\n\t\n")));
        assertFalse(OutputComparator.matches(repeatedOutput("1"), repeatedOutput("2")));
        assertFalse(OutputComparator.matches(stream("a \tb"), stream("a\t b")));
        assertTrue(OutputComparator.matches(stream("a \t\n b"), stream("a\t \r\n b")));
    }

    /**
     * 按需生成两百万个整数，避免测试本身持有完整输出
     * @param suffix 最后一个整数后的文本
     * @return 长行及尾部内容的组合流
     */
    private InputStream repeatedOutput(String suffix) {
        byte[] number = "100000000 ".getBytes(StandardCharsets.UTF_8);
        InputStream generated = new InputStream() {
            private long position;
            private final long size = 2_000_000L * number.length;

            /**
             * 按整数文本的循环位置生成单个字节
             * @return 文本字节，输出完成后为 -1
             */
            @Override
            public int read() {
                return position == size ? -1 : number[(int) (position++ % number.length)];
            }

            /**
             * 将生成的整数填入调用方缓冲，不创建完整文本
             * @param buffer 接收输出的缓冲
             * @param offset 写入起点
             * @param length 请求的最多字节数
             * @return 实际写入数，输出完成后为 -1
             */
            @Override
            public int read(byte[] buffer, int offset, int length) {
                assertTrue(length <= 8192);
                if (position == size) {
                    return -1;
                }
                int count = (int) Math.min(length, size - position);
                for (int index = 0; index < count; index++) {
                    buffer[offset + index] = number[(int) (position++ % number.length)];
                }
                return count;
            }
        };
        return new SequenceInputStream(generated, stream(suffix));
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 保留原字符串归一化算法作为独立回归基准，仅处理测试中的小样本
     * @param value 测试输出文本
     * @return 去除行末空白及末尾空行后的文本
     */
    private String normalize(String value) {
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
                if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)
                        && character != 0xFEFF && character != 0x202A && character != 0
                        && character != 0x3164 && character != 0x2800 && character != 0x200C && character != 0x180E) {
                    lineEnd = normalized.length();
                    contentEnd = lineEnd;
                }
            }
        }
        return normalized.substring(0, contentEnd);
    }
}

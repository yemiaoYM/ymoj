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
        assertEquals("\n a\n\nb c", OutputComparator.normalize("\r\n a \r\n \t\r\nb c\t\n \n"));
        String prefix = "x".repeat(8191);
        assertTrue(OutputComparator.matches(stream(prefix + "\r\nnext \r\n"), prefix + "\nnext"));
        assertFalse(OutputComparator.matches(stream("a b"), "ab"));
        assertEquals("", OutputComparator.normalize(" \r\n".repeat(300000)));
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
        assertTrue(OutputComparator.matches(new SequenceInputStream(stream("answer"), spaces), "answer\n"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> OutputComparator.matches(stream("answer"), "answer"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void streamingComparisonMatchesEstablishedRulesIncludingLeadingWhitespaceAndEmptyLines() throws Exception {
        String[] values = {"", " ", "\r\n\n", "a", "a\n", "a  \r\n\r\n", " a\n", "\na\n", "a\nb",
                "a\n\nb", "a b", "中文😀 \r\n尾部\t\n\n", "answer\u00a0\n\ufeff\n",
                "answer\u202a\u0000\u3164\u2800\u200c\u180e\n"};
        for (String expected : values) {
            for (String actual : values) {
                assertEquals(OutputComparator.normalize(expected).equals(OutputComparator.normalize(actual)),
                        OutputComparator.matches(stream(expected), actual), expected + " / " + actual);
            }
        }
    }

    @Test
    void randomWhitespaceCasesAndVeryLongTrailingWhitespaceStayCompatible() throws Exception {
        Random random = new Random(19L);
        String alphabet = "ab \t\r\n界";
        for (int trial = 0; trial < 1000; trial++) {
            StringBuilder expected = new StringBuilder();
            StringBuilder actual = new StringBuilder();
            for (int index = 0; index < random.nextInt(50); index++) {
                expected.append(alphabet.charAt(random.nextInt(alphabet.length())));
                actual.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertEquals(OutputComparator.normalize(expected.toString()).equals(OutputComparator.normalize(actual.toString())),
                    OutputComparator.matches(stream(expected.toString()), actual.toString()));
        }
        assertTrue(OutputComparator.matches(stream("answer" + " ".repeat(2 * 1024 * 1024)), "answer\n"));
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
        assertFalse(OutputComparator.matches(expected, "wrong"));
        assertTrue(expected.eof);
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}

package com.ymcloud.judge;

import com.ymcloud.judge.strategy.OutputComparator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputComparatorTest {

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

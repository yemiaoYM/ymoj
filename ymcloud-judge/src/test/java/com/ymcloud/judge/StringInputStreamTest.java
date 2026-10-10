package com.ymcloud.judge;

import com.ymcloud.judge.bo.StringInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Docker stdin 必须使用 UTF-8 字节，保留 Unicode 及首尾空白。
 */
class StringInputStreamTest {

    @Test
    void bulkReadsPreserveUtf8UnicodeAndWhitespace() throws Exception {
        String value = " \t中文😀\r\n尾部  \n\n";
        try (StringInputStream stream = new StringInputStream(value)) {
            assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), stream.readAllBytes());
        }
    }

    @Test
    void mixedSingleAndOffsetReadsProduceTheSameUtf8Bytes() throws Exception {
        String value = "界面\n";
        try (StringInputStream stream = new StringInputStream(value);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(stream.read());
            byte[] buffer = new byte[4];
            int count;
            while ((count = stream.read(buffer, 1, 2)) != -1) {
                output.write(buffer, 1, count);
            }
            assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), output.toByteArray());
            assertEquals(-1, stream.read());
        }
    }
}

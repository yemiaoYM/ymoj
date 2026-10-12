package com.ymcloud.judge.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedTestDataStreamTest {

    @Test
    void failedVerificationStopsFurtherReadsAndDoesNotReadAgainOnClose() throws Exception {
        byte[] original = bytes("input");
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        InputStream input = new ByteArrayInputStream(bytes("wrong")) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                reads.incrementAndGet();
                return super.read(buffer, offset, length);
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        VerifiedTestDataStream verified = new VerifiedTestDataStream(input, original.length, hash(original));
        IOException failure = assertThrows(IOException.class, verified::readAllBytes);
        int readsBeforeClose = reads.get();
        IOException subsequent = assertThrows(IOException.class, verified::read);
        assertEquals(failure, subsequent.getCause());
        verified.close();
        verified.close();
        assertEquals(readsBeforeClose, reads.get());
        assertEquals(1, closes.get());
        assertThrows(IOException.class, verified::read);
        assertThrows(IOException.class, () -> verified.skip(1));
    }

    @Test
    void closingPartialFilePreservesIntegrityFailureWhenUnderlyingCloseAlsoFails() throws Exception {
        byte[] original = bytes("input");
        InputStream input = new ByteArrayInputStream(bytes("wrong")) {
            @Override
            public void close() throws IOException {
                throw new IOException("underlying close failed");
            }
        };
        VerifiedTestDataStream verified = new VerifiedTestDataStream(input, original.length, hash(original));
        verified.read();
        IOException failure = assertThrows(IOException.class, verified::close);
        assertTrue(failure.getMessage().contains("完整性"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("underlying close failed", failure.getSuppressed()[0].getMessage());
        assertThrows(IOException.class, verified::read);
    }

    @Test
    void closingPartialValidFileDrainsExactlyOnceAndRejectsFurtherReads() throws Exception {
        byte[] original = bytes(" \tinput\r\n\n");
        AtomicInteger bytesRead = new AtomicInteger();
        InputStream input = new ByteArrayInputStream(original) {
            @Override
            public synchronized int read() {
                int value = super.read();
                if (value >= 0) {
                    bytesRead.incrementAndGet();
                }
                return value;
            }

            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                int count = super.read(buffer, offset, length);
                if (count > 0) {
                    bytesRead.addAndGet(count);
                }
                return count;
            }
        };
        VerifiedTestDataStream verified = new VerifiedTestDataStream(input, original.length, hash(original));
        assertFalse(verified.markSupported());
        assertEquals(original[0], verified.read());
        assertEquals(2, verified.skip(2));
        verified.close();
        verified.close();
        assertEquals(original.length, bytesRead.get());
        assertThrows(IOException.class, verified::read);
        assertThrows(IOException.class, verified::reset);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}

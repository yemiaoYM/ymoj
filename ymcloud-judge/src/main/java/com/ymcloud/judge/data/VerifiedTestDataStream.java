package com.ymcloud.judge.data;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 逐字节校验缓存文件，部分读取后关闭仍须校验剩余内容。
 * 校验失败后停止读取，避免再次计算摘要掩盖首次失败。
 */
final class VerifiedTestDataStream extends FilterInputStream {

    private static final int BUFFER_SIZE = 8192;

    private final long expectedSize;
    private final String expectedHash;
    private final MessageDigest digest;

    private long bytesRead;
    private boolean verified;
    private boolean closed;
    private IOException failure;

    VerifiedTestDataStream(InputStream input, long expectedSize, String expectedHash) {
        super(input);
        this.expectedSize = expectedSize;
        this.expectedHash = expectedHash;
        this.digest = sha256();
    }

    @Override
    public int read() throws IOException {
        requireReadable();
        try {
            int value = in.read();
            if (value < 0) {
                verify();
            } else {
                bytesRead++;
                digest.update((byte) value);
                checkSize();
            }
            return value;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        requireReadable();
        try {
            int count = in.read(buffer, offset, length);
            if (count < 0) {
                verify();
            } else if (count > 0) {
                bytesRead += count;
                digest.update(buffer, offset, count);
                checkSize();
            }
            return count;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public long skip(long amount) throws IOException {
        requireReadable();
        long skipped = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        while (skipped < amount) {
            int count = read(buffer, 0, (int) Math.min(buffer.length, amount - skipped));
            if (count < 0) {
                break;
            }
            skipped += count;
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("校验流不支持 reset");
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        try (InputStream ignored = in) {
            if (!verified && failure == null) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (read(buffer) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("测试数据校验被中断");
                    }
                }
            }
        } finally {
            closed = true;
        }
    }

    private void requireReadable() throws IOException {
        if (closed) {
            throw new IOException("测试数据校验流已经关闭");
        }
        if (failure != null) {
            throw new IOException("测试数据校验流已失败", failure);
        }
    }

    private void checkSize() throws IOException {
        if (bytesRead > expectedSize) {
            throw new IOException("运行中的测试数据文件大小改变");
        }
    }

    private void verify() throws IOException {
        if (!verified) {
            if (bytesRead != expectedSize || !expectedHash.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("运行中的测试数据文件完整性校验失败");
            }
            verified = true;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}

package com.ymcloud.judge.data;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 逐字节校验缓存文件，部分读取后关闭仍须校验剩余内容。
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

    /**
     * 绑定原始数据流与不可变文件元数据。
     * @param input 由本实例负责关闭的输入
     * @param expectedSize 期望字节数
     * @param expectedHash 小写 SHA-256
     */
    VerifiedTestDataStream(InputStream input, long expectedSize, String expectedHash) {
        super(input);
        this.expectedSize = expectedSize;
        this.expectedHash = expectedHash;
        this.digest = sha256();
    }

    /**
     * 读取一个字节并累计摘要，EOF 时验证完整性。
     * @return 字节值，EOF 为 -1
     * @throws IOException 已关闭、中断或完整性失败
     */
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

    /**
     * 按块读取并累计摘要，EOF 时验证完整性。
     * @param buffer 目标缓冲区
     * @param offset 写入偏移
     * @param length 最多读取字节数
     * @return 实际字节数，EOF 为 -1
     * @throws IOException 已关闭、读取或完整性失败
     */
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

    /**
     * 通过实际读取实现跳过，不能绕过摘要校验。
     * @param amount 请求跳过的字节数
     * @return 实际跳过字节数
     * @throws IOException 读取或完整性失败
     */
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

    /**
     * 禁止回退读取，避免摘要重复累计。
     * @return 始终为 false
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * 拒绝重置校验流。
     * @throws IOException 始终抛出，不支持回退
     */
    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("校验流不支持 reset");
    }

    /**
     * 未失败时读完剩余内容并验证，再关闭底层流；重复关闭无操作。
     * @throws IOException 中断、完整性或关闭失败
     */
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

    /**
     * 拒绝已关闭或已失败的流继续读取。
     * @throws IOException 流已关闭或先前失败
     */
    private void requireReadable() throws IOException {
        if (closed) {
            throw new IOException("测试数据校验流已经关闭");
        }
        if (failure != null) {
            throw new IOException("测试数据校验流已失败", failure);
        }
    }

    /**
     * 立即拒绝超过清单大小的数据。
     * @throws IOException 已读取字节数超过声明
     */
    private void checkSize() throws IOException {
        if (bytesRead > expectedSize) {
            throw new IOException("运行中的测试数据文件大小改变");
        }
    }

    /**
     * 到达 EOF 后只核对一次长度和摘要。
     * @throws IOException 大小或摘要不匹配
     */
    private void verify() throws IOException {
        if (!verified) {
            if (bytesRead != expectedSize || !expectedHash.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("运行中的测试数据文件完整性校验失败");
            }
            verified = true;
        }
    }

    /**
     * 为当前流创建独立摘要。
     * @return SHA-256 摘要实例
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}

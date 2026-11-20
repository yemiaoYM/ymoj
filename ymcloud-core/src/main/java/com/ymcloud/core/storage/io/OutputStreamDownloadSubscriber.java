package com.ymcloud.core.storage.io;

import com.ymcloud.core.storage.exception.S3StorageException;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.function.Consumer;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 将异步下载的缓冲区写入调用方的输出流或通道
 */
public class OutputStreamDownloadSubscriber implements Consumer<ByteBuffer>, AutoCloseable {

    private final WritableByteChannel channel;

    private final boolean allowAutoClose;

    /**
     * 创建输出流下载订阅器
     * @param channel 可写通道
     * @param allowAutoClose 是否允许自动关闭通道
     */
    private OutputStreamDownloadSubscriber(WritableByteChannel channel, boolean allowAutoClose) {
        this.channel = channel;
        this.allowAutoClose = allowAutoClose;
    }

    /**
     * 创建输出流下载订阅器
     * @param out 由调用方管理的下载输出流
     * @param allowAutoClose 是否允许自动关闭流
     */
    private OutputStreamDownloadSubscriber(OutputStream out, boolean allowAutoClose) {
        this.allowAutoClose = allowAutoClose;

        if (out instanceof FileOutputStream outputStream) {

            channel = outputStream.getChannel();
        } else {
            channel = Channels.newChannel(out);
        }
    }

    /**
     * 写入下载到的字节缓冲区
     * @param byteBuffer 字节缓冲区
     */
    @Override
    public void accept(ByteBuffer byteBuffer) {
        try {
            while (byteBuffer.hasRemaining()) {
                channel.write(byteBuffer);
            }
        } catch (Exception e) {
            throw new S3StorageException("写入下载目标失败", e);
        }
    }

    /**
     * 按配置关闭底层通道
     * @throws Exception 关闭通道异常
     */
    @Override
    public void close() throws Exception {
        if (channel.isOpen() && allowAutoClose) {
            channel.close();
        }
    }

    /**
     * 创建一个输出流下载订阅器
     * @param out 由调用方管理的下载输出流
     * @return 输出流下载订阅器
     */
    public static OutputStreamDownloadSubscriber create(OutputStream out) {
        return create(out, false);
    }

    /**
     * 创建一个输出流下载订阅器
     * @param out 由调用方管理的下载输出流
     * @param allowAutoClose 是否允许自动关闭流
     * @return 输出流下载订阅器
     */
    public static OutputStreamDownloadSubscriber create(OutputStream out, boolean allowAutoClose) {
        return new OutputStreamDownloadSubscriber(out, allowAutoClose);
    }

    /**
     * 创建一个输出流下载订阅器
     * @param channel 可写字节通道
     * @return 输出流下载订阅器
     */
    public static OutputStreamDownloadSubscriber create(WritableByteChannel channel) {
        return create(channel, false);
    }

    /**
     * 创建一个输出流下载订阅器
     * @param channel 可写字节通道
     * @param allowAutoClose 是否允许自动关闭流
     * @return 输出流下载订阅器
     */
    public static OutputStreamDownloadSubscriber create(WritableByteChannel channel, boolean allowAutoClose) {
        return new OutputStreamDownloadSubscriber(channel, allowAutoClose);
    }
}

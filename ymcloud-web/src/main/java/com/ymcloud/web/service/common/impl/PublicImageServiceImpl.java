package com.ymcloud.web.service.common.impl;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.common.PublicImageService;
import com.ymcloud.web.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 公开图片的真实格式校验、重编码、压缩和统一存储入口
 */
@Service
public class PublicImageServiceImpl implements PublicImageService {

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_COMPRESSED_EDGE = 1920;
    private static final long MAX_IMAGE_PIXELS = 24_000_000;

    @Autowired
    private FileService storedFiles;

    /**
     * 校验真实格式并重编码公开图片，展示比例由前端处理
     * @param upload 不超过 5 MiB 的静态 JPEG、PNG 或 WebP
     * @return 可直接访问的公开图片 URL，输出内容不超过 2 MiB
     */
    @Override
    public String upload(MultipartFile upload) {
        if (upload.isEmpty() || upload.getSize() > MAX_IMAGE_BYTES) {
            throw new FileException("图片不能为空且不能超过5MiB", HttpStatus.BAD_REQUEST);
        }
        try (InputStream input = upload.getInputStream()) {
            BufferedImage image = readImage(input);
            int edge = Math.max(image.getWidth(), image.getHeight());
            if (edge > MAX_COMPRESSED_EDGE) {
                image = resize(image, (double) MAX_COMPRESSED_EDGE / edge);
            }
            String format = !image.getColorModel().hasAlpha() ? "jpeg" : "png";
            byte[] bytes = encode(image, format);
            while (bytes.length > MAX_COMPRESSED_BYTES) {
                image = resize(image, Math.min(0.85, Math.sqrt((double) MAX_COMPRESSED_BYTES / bytes.length) * 0.95));
                bytes = encode(image, format);
            }
            return saveImage(bytes, FileConstants.SUB_DIR_IMAGE + "." + format, "image/" + format);
        } catch (IOException e) {
            throw new FileException("图片读取或处理失败，请重新选择", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 将已校验的图片登记到当前用户，复用存储事务和失败清理
     * @param bytes 已重编码的图片内容
     * @param fileName 服务端指定的图片名称
     * @param contentType 实际编码对应的媒体类型
     * @return 保存后的公开图片 URL
     */
    private String saveImage(byte[] bytes, String fileName, String contentType) {
        User user = SecurityUtils.getUser();
        File file = storedFiles.create(new CreateStoredFileCommand(
                fileName,
                contentType,
                FileConstants.SUB_DIR_IMAGE,
                FileBizType.IMAGE,
                null,
                true, user.getId(),
                user.getUsername()), new ByteArrayInputStream(bytes), bytes.length);
        return FileConstants.PUBLIC_URL_PREFIX + file.getPublicId();
    }

    /**
     * 等比缩小图片，不裁剪、不放大，保留透明通道
     * @param image 已验证解码尺寸的图片
     * @param scale 大于零且小于一的缩放比例
     * @return 缩小后的图片
     */
    private BufferedImage resize(BufferedImage image, double scale) {
        int width = Math.max(1, (int) (image.getWidth() * scale));
        int height = Math.max(1, (int) (image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    /**
     * 重新编码静态图片，不携带原文件的元数据或附加内容
     * @param image 待编码的图片
     * @param format JDK 支持的 png 或 jpeg
     * @return 编码后的字节，JPEG 质量固定为 0.85
     * @throws IOException 编码或输出流写入失败
     */
    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName(format).next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            try {
                writer.setOutput(stream);
                var params = writer.getDefaultWriteParam();
                if ("jpeg".equals(format)) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.85f);
                }
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
        }
        return output.toByteArray();
    }

    /**
     * 读取真实格式后安全解码，像素总量上限用于限制解码内存，不限制业务展示比例
     * @param input 调用方负责关闭的图片流，最多读取 5 MiB 加一个探测字节
     * @return 已验证的静态图片，解码面积不超过 2400 万像素
     * @throws IOException 图片流损坏或无法读取
     */
    private BufferedImage readImage(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new FileException("图片不能超过5MB", HttpStatus.BAD_REQUEST);
        }
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new FileException("请选择有效的图片文件", HttpStatus.BAD_REQUEST);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!("PNG".equalsIgnoreCase(format)
                        || "JPEG".equalsIgnoreCase(format)
                        || "WebP".equalsIgnoreCase(format))) {
                    throw new FileException("图片格式不支持，请选择JPEG、PNG或静态WebP", HttpStatus.BAD_REQUEST);
                }
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new FileException("图片不能超过2400万像素", HttpStatus.BAD_REQUEST);
                }
                if (reader.getNumImages(true) > 1) {
                    throw new FileException("暂不支持动画图片，请选择静态图片", HttpStatus.BAD_REQUEST);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}

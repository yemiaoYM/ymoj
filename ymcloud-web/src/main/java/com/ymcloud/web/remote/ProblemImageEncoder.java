package com.ymcloud.web.remote;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 题面插图校验与压缩，保留比例和透明背景，不套用比赛封面裁剪规格
 */
public final class ProblemImageEncoder {

    private static final int MAX_PIXELS = 24_000_000;

    /**
     * 工具类型不允许实例化。
     */
    private ProblemImageEncoder() {}

    /**
     * 静态插图重新编码去除附加内容，超出容量时按比例缩小，动画在限额内原样保留。
     * @param bytes 已限制下载体积的候选图片
     * @param maxBytes 本站允许的单张存储上限
     * @return 可保存的图片字节及格式
     */
    public static Encoded encode(byte[] bytes, long maxBytes) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new FileException("题面图片格式暂不支持或文件已损坏", HttpStatus.BAD_REQUEST);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new FileException("题面图片分辨率过大，无法处理", HttpStatus.BAD_REQUEST);
                }
                BufferedImage image = reader.read(0);
                String sourceFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if ((sourceFormat.equals("gif") || sourceFormat.equals("webp")) && reader.getNumImages(true) > 1) {
                    if (bytes.length > maxBytes) {
                        throw new FileException("题面动画图片过大，无法保留动画", HttpStatus.BAD_REQUEST);
                    }
                    return new Encoded(bytes, sourceFormat, "image/" + sourceFormat);
                }
                String format = "JPEG".equalsIgnoreCase(reader.getFormatName()) ? "jpg" : "png";
                while (true) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, format, output)) {
                        throw new FileException("题面图片编码失败", HttpStatus.SERVICE_UNAVAILABLE);
                    }
                    if (output.size() <= maxBytes) {
                        return new Encoded(output.toByteArray(), format, format.equals("jpg") ? "image/jpeg" : "image/png");
                    }
                    if (image.getWidth() == 1 && image.getHeight() == 1) {
                        throw new FileException("文件存储容量限制不足，无法保存题面图片", HttpStatus.BAD_REQUEST);
                    }
                    double scale = Math.min(0.85, Math.sqrt((double) maxBytes / output.size()) * 0.95);
                    int nextWidth = Math.max(1, (int) (image.getWidth() * scale));
                    int nextHeight = Math.max(1, (int) (image.getHeight() * scale));
                    BufferedImage resized = new BufferedImage(nextWidth, nextHeight,
                            image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = resized.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(image, 0, 0, nextWidth, nextHeight, null);
                    } finally {
                        graphics.dispose();
                    }
                    image = resized;
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new FileException("题面图片读取或压缩失败", HttpStatus.BAD_REQUEST, exception);
        }
    }

    /**
     * @Author: Yemiao
     * @CreateTime: 2026-10-29
     * @Description: 已校验并压缩的插图及真实媒体类型
     */
    public record Encoded(byte[] bytes, String extension, String contentType) {}
}

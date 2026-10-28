package com.ymcloud.web.service.common.impl;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.common.PublicImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 轮播与比赛封面共用的图片校验、重编码和统一存储入口。
 */
@Service
public class PublicImageServiceImpl implements PublicImageService {

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    @Autowired
    private StoredFileService storedFiles;

    /**
     * 校验后保存公开图片；重新编码去掉元数据及附加内容，存储失败沿用事务补偿。
     * @param upload 不超过 5 MiB 的 JPEG 或 PNG 图片
     * @param fileName 业务指定的文件名，不使用客户端路径
     * @param ratioWidth 业务指定的宽度比例
     * @param ratioHeight 业务指定的高度比例
     * @return 已登记的公开图片文件
     */
    @Override
    public File upload(MultipartFile upload, String fileName, int ratioWidth, int ratioHeight) {
        if (upload.isEmpty() || upload.getSize() > MAX_IMAGE_BYTES) {
            throw new FileException("图片不能为空且不能超过5MB", HttpStatus.BAD_REQUEST);
        }
        try (InputStream input = upload.getInputStream()) {
            BufferedImage image = readImage(input, ratioWidth, ratioHeight);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new FileException("图片处理后超过5MB，请降低分辨率", HttpStatus.BAD_REQUEST);
            }
            User user = SecurityUtils.getUser();
            File file = new File();
            file.setFileName(fileName);
            file.setFileType("image/png");
            file.setBizType(FileBizType.IMAGE);
            file.setIsPublic(FileConstants.ACCESS_PUBLIC);
            file.setUploaderId(user.getId());
            file.setUploaderUsername(user.getUsername());
            storedFiles.save(new ByteArrayInputStream(bytes), bytes.length, file);
            return file;
        } catch (IOException e) {
            throw new FileException("图片读取失败，请重新选择", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 验证已有公开图片仍符合展示规格，流由调用方关闭。
     * @param input 待校验的图片流
     * @param ratioWidth 业务指定的宽度比例
     * @param ratioHeight 业务指定的高度比例
     * @throws IOException 图片读取失败
     */
    @Override
    public void validate(InputStream input, int ratioWidth, int ratioHeight) throws IOException {
        readImage(input, ratioWidth, ratioHeight);
    }

    /**
     * 先读取尺寸再解码，拒绝非图片、过大图片及比例不符的图片。
     * @param input 调用方负责关闭的图片流，最多读取 5 MiB 加一个探测字节
     * @param ratioWidth 业务指定的宽度比例
     * @param ratioHeight 业务指定的高度比例
     * @return 已解码的 JPEG 或 PNG；高度不超过 2160，宽度受比例和 3840 上限约束
     * @throws IOException 图片流损坏或无法读取
     */
    private BufferedImage readImage(InputStream input, int ratioWidth, int ratioHeight) throws IOException {
        byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new FileException("图片不能超过5MB", HttpStatus.BAD_REQUEST);
        }
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new FileException("请选择有效的JPEG或PNG图片", HttpStatus.BAD_REQUEST);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int maxWidth = Math.min(3840, 2160 * ratioWidth / ratioHeight);
                if (!("PNG".equalsIgnoreCase(format)
                        || "JPEG".equalsIgnoreCase(format))
                        || width < ratioWidth
                        || height < ratioHeight
                        || width > maxWidth
                        || height > 2160
                        || width * ratioHeight != height * ratioWidth) {
                    throw new FileException("图片必须为" + ratioWidth + ":" + ratioHeight
                            + "的JPEG或PNG，最大" + maxWidth + "×2160", HttpStatus.BAD_REQUEST);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}

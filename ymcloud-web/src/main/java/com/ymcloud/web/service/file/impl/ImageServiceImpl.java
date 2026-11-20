package com.ymcloud.web.service.file.impl;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.bo.FileUploadBO;
import com.ymcloud.pojo.bo.ImageUploadBO;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.file.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 图片上传大小与格式校验及原图存储
 */
@Service
public class ImageServiceImpl implements ImageService {

    @Autowired
    private FileService storedFiles;

    /**
     * 校验真实格式并保存公开图片原始内容
     *
     * @param upload 不超过 3 MiB 的 JPEG、PNG 或 WebP 图片
     * @return 可直接访问的公开图片 URL
     */
    @Override
    public Long upload(MultipartFile upload) {
        ImageUploadBO bo = ImageUploadBO.builder()
                .uploaderId(SecurityUtils.getUserId())
                .maxBytes(3 * 1024 * 1024)
                .bizType(FileBizType.GENERAL)
                .build();
        return saveImage(upload, bo);
    }

    /**
     * 上传已授权的正文草稿插图，业务保存后绑定归属
     * @param upload 已选择的图片
     * @param bo 业务
     * @return 十进制文件主键
     */
    @Override
    public Long upload(MultipartFile upload, ImageUploadBO bo) {
        return saveImage(upload, bo);
    }

    /**
     * 按业务大小限制和实际图片格式登记上传者，保存收到的原始内容
     * @param upload 接收的图片
     * @param bo 业务
     * @return 已登记的文件主键
     */
    private Long saveImage(MultipartFile upload, ImageUploadBO bo) {
        long size = upload.getSize();
        if (size == 0) {
            throw new FileException("图片不能为空", HttpStatus.BAD_REQUEST);
        }
        if (size > bo.getMaxBytes()) {
            throw new FileException("图片不能超过" + bo.getMaxBytes()/(1024 * 1024)+"MB", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        //检查文件扩展名
        String ext = FileUtil.extName(upload.getOriginalFilename());
        if (StrUtil.isBlank(ext) || !Set.of("jpg", "jpeg", "png", "webp").contains(ext.toLowerCase(Locale.ROOT))) {
            throw new FileException("不支持的图片格式", HttpStatus.UNSUPPORTED_TYPE);
        }
        String fileType;
        try (InputStream input = upload.getInputStream()) {
            fileType = FileTypeUtil.getType(input);
        } catch (IOException e) {
            throw new FileException("图片读取失败", HttpStatus.ERROR, e);
        }
        //检查实际文件格式
        if (!Set.of("jpg", "png", "webp").contains(fileType) || !fileType.equalsIgnoreCase("jpeg".equalsIgnoreCase(ext)?"jpg":ext)) {
            throw new FileException("文件内容不是受支持的图片", HttpStatus.UNSUPPORTED_TYPE);
        }
        String contentType = FileUtil.getMimeType("image." + fileType);

        FileUploadBO request = new FileUploadBO(upload.getOriginalFilename(), contentType, bo.getBizType(), bo.getBizId(), bo.getUploaderId());
        try (InputStream input = upload.getInputStream()) {
            return storedFiles.upload(request, input, size).getId();
        } catch (IOException e) {
            throw new FileException("图片上传失败", HttpStatus.ERROR, e);
        }
    }

}

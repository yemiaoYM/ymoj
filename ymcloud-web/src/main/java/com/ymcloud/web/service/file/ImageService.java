package com.ymcloud.web.service.file;

import com.ymcloud.pojo.bo.ImageUploadBO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 头像、公开图片及业务正文图片的上传服务
 */
public interface ImageService {

    /**
     * 校验实际格式并原样保存公开图片
     *
     * @param upload 不超过 3 MiB 的 JPEG、PNG 或 WebP 图片
     * @return 可直接访问的公开图片 URL
     */
    Long upload(MultipartFile upload);

    /**
     * 上传已授权的正文草稿插图，业务保存后绑定归属
     * @param upload 已选择的图片
     * @param bo 业务字段
     * @return 十进制文件主键
     */
    Long upload(MultipartFile upload, ImageUploadBO bo);
}

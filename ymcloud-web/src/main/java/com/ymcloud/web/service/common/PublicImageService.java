package com.ymcloud.web.service.common;

import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 轮播、比赛封面与 Markdown 插图的公开图片处理契约
 */
public interface PublicImageService {

    /**
     * 校验真实图片格式、重编码并保存公开图片，展示比例由前端裁剪负责
     * @param upload 不超过 5 MiB 的静态 JPEG、PNG 或 WebP 图片
     * @return 可直接访问的公开图片 URL
     */
    String upload(MultipartFile upload);
}

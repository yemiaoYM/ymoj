package com.ymcloud.web.service.common;

import com.ymcloud.pojo.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 轮播与比赛封面的公开图片处理契约。
 */
public interface PublicImageService {

    /**
     * 按指定比例校验图片，重编码为 PNG 并登记到统一存储。
     * @param upload 不超过 5 MiB 的 JPEG 或 PNG 图片
     * @param fileName 业务指定的文件名
     * @param ratioWidth 业务指定的宽度比例
     * @param ratioHeight 业务指定的高度比例
     * @return 已保存的公开图片
     */
    File upload(MultipartFile upload, String fileName, int ratioWidth, int ratioHeight);

    /**
     * 校验已有图片的格式、尺寸与比例。
     * @param input 调用方负责关闭的图片流
     * @param ratioWidth 业务指定的宽度比例
     * @param ratioHeight 业务指定的高度比例
     * @throws IOException 图片读取失败
     */
    void validate(InputStream input, int ratioWidth, int ratioHeight) throws IOException;
}

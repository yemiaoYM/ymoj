package com.ymcloud.web.service.common;

import com.ymcloud.pojo.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 轮播、比赛封面与 Markdown 插图的公开图片处理契约
 */
public interface PublicImageService {

    /**
     * 校验、重编码并保存公开图片；固定比例输出 PNG，自由比例图片等比压缩
     * @param upload 不超过 5 MiB 的静态 JPEG 或 PNG，自由比例另支持 WebP
     * @param fileName 服务端指定的不含扩展名的文件名
     * @param ratioWidth 固定宽度比例；自由比例时与 ratioHeight 同为 0
     * @param ratioHeight 固定高度比例；自由比例时与 ratioWidth 同为 0
     * @return 已保存的公开图片；自由比例输出长边不超过 1920 像素且不超过 2 MiB
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

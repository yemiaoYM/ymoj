package com.ymcloud.web.service.common;

import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 轮播配置、图片上传及公开缓存的业务契约
 */
public interface CarouselSettingService {

    /**
     * 直接读取最新配置，仅保留可公开访问的图片。
     * @return 配置标识和有序图片；尚未配置与明确清空分别表示
     */
    CarouselSettingVO get();

    /**
     * 验证并保存图片，上传成功不自动发布到首页。
     * @param upload 不超过 5 MiB、16:9 的 JPEG 或 PNG
     * @return 图片 ID 和公开地址
     */
    CarouselSettingVO.Image upload(MultipartFile upload);

    /**
     * 在短事务内保存图片顺序；Controller 在方法返回后用注解失效公开缓存。
     * @param request 已通过结构校验的图片 ID 列表，空列表用于关闭轮播
     */
    void save(CarouselSettingDTO request);
}

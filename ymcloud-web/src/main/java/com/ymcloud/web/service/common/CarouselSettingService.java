package com.ymcloud.web.service.common;

import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 轮播配置与公开缓存的业务契约
 */
public interface CarouselSettingService {

    /**
     * 直接读取最新配置，仅保留可公开访问的图片。
     * @return 配置标识和有序图片；尚未配置与明确清空分别表示
     */
    List<String> list();

    /**
     * 上传轮播图
     * @param file 图片
     * @return 轮播图列表
     */
    List<String> uploadCarousel(MultipartFile file);

    /**
     * 在短事务内保存图片顺序；Controller 在方法返回后用注解失效公开缓存。
     * @param request 已通过结构校验的图片 ID 列表，空列表用于关闭轮播
     */
    void save(CarouselSettingDTO request);

}

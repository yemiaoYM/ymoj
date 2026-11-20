package com.ymcloud.web.controller.admin.system;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.file.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 查询与发布首页轮播配置，沿用系统设置权限
 */
@Slf4j
@RestController
@RequestMapping("/admin/system-setting/carousel")
public class AdminCarouselController {

    @Autowired
    private CarouselSettingService carouselSettingService;

    /**
     * 上传轮播图
     * @param file 前端完成裁剪的 JPEG、PNG 或 WebP 图片
     * @return 轮播图列表
     */
    @PostMapping("/image")
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<List<String>> uploadCarousel(@RequestParam("file") MultipartFile file) {
        log.info("上传轮播图");
        return Result.ok(carouselSettingService.uploadCarousel(file));
    }

    /**
     * 保存轮播顺序，空数组隐藏首页轮播
     * @param dto 有序文件 ID，最多 10 张
     * @return 操作结果
     */
    @CacheEvict(cacheNames = CacheConstants.CACHE_NAME, key = "'" + CacheConstants.HOME_CAROUSEL_KEY + "'")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<Void> save(@Validated @RequestBody CarouselSettingDTO dto) {
        log.info("保存轮播图");
        carouselSettingService.save(dto);
        return Result.ok();
    }

}

package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.common.PublicImageService;
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

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 查询与发布首页轮播配置，沿用系统设置权限
 */
@RestController
@RequestMapping("/admin/system-setting/carousel")
public class AdminCarouselController {

    @Autowired
    private CarouselSettingService service;

    @Autowired
    private PublicImageService publicImages;

    /**
     * 上传轮播候选图；文件保存后仍需另行发布轮播配置
     * @param file 前端完成裁剪的 JPEG、PNG 或 WebP 图片
     * @return 已登记文件的字符串 ID 和公开地址
     */
    @PostMapping("/image")
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<CarouselSettingVO.Image> uploadCarousel(@RequestParam("file") MultipartFile file) {
        String url = publicImages.upload(file);
        return Result.ok(new CarouselSettingVO.Image(url.substring(FileConstants.PUBLIC_URL_PREFIX.length()), url));
    }

    /**
     * 查询当前轮播配置
     * @return 图片顺序和是否已配置
     */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('system:setting:list')")
    public Result<CarouselSettingVO> get() {
        return Result.ok(service.get());
    }

    /**
     * 保存轮播顺序，空数组隐藏首页轮播；Service 短事务提交后失效公开缓存
     * @param request 有序文件 ID，最多 10 张
     * @return 操作结果
     */
    @CacheEvict(cacheNames = CacheConstants.CACHE_NAME, key = "'" + CacheConstants.HOME_CAROUSEL_KEY + "'")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<Void> save(@Validated @RequestBody CarouselSettingDTO request) {
        service.save(request);
        return Result.ok();
    }

}

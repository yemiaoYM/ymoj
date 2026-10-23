package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.service.common.CarouselSettingService;
import lombok.RequiredArgsConstructor;
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
 * @Description: 系统配置中的轮播列表和图片上传入口，沿用系统设置权限
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/system-setting/carousel")
public class AdminCarouselController {

    private final CarouselSettingService service;

    /**
     * 查询当前轮播配置。
     * @return 图片顺序和是否已配置
     */
    @GetMapping
    @PreAuthorize("@ss.hasPermi('system:setting:list')")
    public Result<CarouselSettingVO> get() {
        return Result.ok(service.get());
    }

    /**
     * 保存轮播顺序，空数组隐藏首页轮播。
     * @param request 有序文件 ID，最多 10 张
     * @return 操作结果
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<Void> save(@Validated @RequestBody CarouselSettingDTO request) {
        service.save(request);
        return Result.ok();
    }

    /**
     * 上传已经裁剪的轮播图片，图片可公开访问，需另行保存列表后首页才展示。
     * @param file 16:9 的 JPEG 或 PNG 图片
     * @return 已校验的文件 ID 和公开 URL
     */
    @PostMapping("/image")
    @PreAuthorize("@ss.hasPermi('system:setting:edit')")
    public Result<CarouselSettingVO.Image> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(file));
    }
}

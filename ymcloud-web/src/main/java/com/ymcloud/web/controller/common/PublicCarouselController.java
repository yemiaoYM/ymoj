package com.ymcloud.web.controller.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.service.common.CarouselSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 读取首页轮播公开图片，不返回文件存储路径或凭据
 */
@RestController
@RequiredArgsConstructor
public class PublicCarouselController {

    private final CarouselSettingService service;

    /**
     * 查询已配置的首页轮播。
     * @return 公开图片及排列顺序
     */
    @GetMapping("/public/carousel")
    public Result<CarouselSettingVO> get() {
        return Result.ok(service.get());
    }
}

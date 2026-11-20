package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 首页轮播排序配置，不接受客户端提供的图片地址
 */
@Data
public class CarouselSettingDTO {

    /**
     * 已上传的公开图片 ID，十进制文件主键
     * 最多 10 张，空数组隐藏轮播，不允许重复
     */
    @NotNull(message = "请传入轮播图列表")
    @Size(max = 10, message = "轮播图最多10张")
    private List<String> imgApiUrl;
}

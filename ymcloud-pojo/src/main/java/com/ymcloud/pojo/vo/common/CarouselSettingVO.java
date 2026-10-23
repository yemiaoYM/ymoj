package com.ymcloud.pojo.vo.common;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 公开轮播配置；未配置时由前端保留内置图片，配置为空时隐藏
 */
public record CarouselSettingVO(boolean configured, List<Image> images) {

    /** 文件 ID 使用字符串传输；url 只来自服务端登记的公开图片。 */
    public record Image(String id, String url) {
    }
}

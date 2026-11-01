package com.ymcloud.web.controller.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.web.service.common.PublicImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-31
 * @Description: 个人资料、题目、比赛及公告编辑器共用的正文图片上传入口
 */
@Slf4j
@RestController
public class ImageController {

    @Autowired
    private PublicImageService publicImages;

    /**
     * 登录用户上传正文插图
     * 正文保存权限由各业务接口校验，不从服务端抓取外部 URL
     * @param file 待校验的 JPEG、PNG 或 WebP 图片
     * @return 压缩后图片的公开地址
     */
    @PostMapping("/markdown/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("上传 Markdown 插图");
        return Result.ok(publicImages.upload(file, "markdown", 0, 0).getUrl());
    }

}

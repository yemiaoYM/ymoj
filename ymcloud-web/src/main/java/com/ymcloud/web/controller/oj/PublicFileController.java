package com.ymcloud.web.controller.oj;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.oj.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 公开文件统一代理，存储桶本身无需开放匿名权限。
 * 仅代理已登记并绑定存储配置的公开文件，不回退到历史目录。
 */
@Slf4j
@RestController
public class PublicFileController {

    private static final Set<String> INLINE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    @Autowired
    private FileService fileService;

    /**
     * 读取已登记的公开文件；缺少存储配置、私有文件及测试数据按不存在处理。
     * @param path 公开 URL 前缀之后的路径，包含开头的斜杠
     * @return 文件流及安全响应头；白名单图片内联展示，其他类型作为附件下载
     * @throws FileException 路径非法、文件不可公开访问或存储服务读取失败
     */
    @GetMapping("/files/public/{*path}")
    public ResponseEntity<Resource> download(@PathVariable("path") String path) {
        File file = fileService.resolvePublicFile(path);
        String name = file.getFileName();
        log.debug("读取公开文件，文件编号：{}", file.getId());
        String contentType = file.getFileType();
        boolean inline = contentType != null && INLINE_TYPES.contains(contentType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(inline ? contentType : "application/octet-stream"));
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        boolean resourceAttachment = file.getBizType() == FileBizType.PROBLEM_ATTACHMENT
                || file.getBizType() == FileBizType.CONTEST_ATTACHMENT;
        headers.setCacheControl(resourceAttachment ? CacheControl.noStore()
                : CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic());
        if (file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(fileService.openPublicFile(file)));
    }
}

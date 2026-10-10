package com.ymcloud.web.controller.oj;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * 公开文件统一代理，存储桶本身无需开放匿名权限。
 * 兼容历史 /files/public URL，并限制回退路径在 public 目录内。
 */
@RestController
public class PublicFileController {

    private static final Set<String> INLINE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final StoredFileService files;
    private final FileEntityService metadata;

    public PublicFileController(StoredFileService files, FileEntityService metadata) {
        this.files = files;
        this.metadata = metadata;
    }

    @GetMapping("/files/public/{*path}")
    public ResponseEntity<Resource> download(@PathVariable("path") String path) {
        if (!path.startsWith("/") || path.length() > 1000 || path.contains("%") || path.contains("\\")
                || path.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new FileException("非法公开文件路径", 404);
        }
        String url = "/files/public" + path;
        // 已登记的私有 URL 和测试数据绝不回退到历史 public 目录。
        boolean forbidden = metadata.lambdaQuery()
                .eq(File::getUrl, url)
                .and(record -> record.ne(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                        .or()
                        .isNull(File::getIsPublic)
                        .or()
                        .eq(File::getBizType, FileBizType.TEST_DATA))
                .exists();
        if (forbidden) {
            throw new FileException("文件不存在", 404);
        }
        File file = files.resolvePublicUrl(url);
        if (file != null && (file.getBizType() == FileBizType.TEST_DATA
                || !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic()))) {
            throw new FileException("文件不存在", 404);
        }
        // 旧 URL 按原 public 目录服务，兼容数据库仍保存其他部署节点绝对路径的记录。
        boolean legacy = file == null || (file.getStorageProfile() == null
                && Integer.valueOf(FileConstants.STORAGE_LOCAL).equals(file.getStorageType()));
        InputStream input = legacy ? files.openLegacyPublic(path.substring(1)) : files.open(file);
        String name = file == null ? path.substring(path.lastIndexOf('/') + 1) : file.getFileName();
        String contentType = file == null ? legacyImageType(name) : file.getFileType();
        boolean inline = contentType != null && INLINE_TYPES.contains(contentType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(inline ? contentType : "application/octet-stream"));
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic());
        if (file != null && file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(input));
    }

    private String legacyImageType(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}

package com.ymcloud.web.controller.oj;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-11
 * @Description: 公开文件统一代理，存储桶本身无需开放匿名权限。
 * 仅代理已登记并绑定存储配置的公开文件，不回退到历史目录。
 */
@RestController
public class PublicFileController {

    private static final Set<String> INLINE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final StoredFileService files;
    private final FileEntityService metadata;

    /**
     * 初始化公开文件读取与访问范围查询依赖。
     * @param files 文件存储服务
     * @param metadata 文件元数据查询服务
     */
    public PublicFileController(StoredFileService files, FileEntityService metadata) {
        this.files = files;
        this.metadata = metadata;
    }

    /**
     * 读取已登记的公开文件；缺少存储配置、私有文件及测试数据按不存在处理。
     * @param path 公开 URL 前缀之后的路径，包含开头的斜杠
     * @return 文件流及安全响应头；白名单图片内联展示，其他类型作为附件下载
     * @throws FileException 路径非法、文件不可公开访问或存储服务读取失败
     */
    @GetMapping("/files/public/{*path}")
    public ResponseEntity<Resource> download(@PathVariable("path") String path) {
        if (!path.startsWith("/")
                || path.length() > 1000
                || path.contains("%")
                || path.contains("\\")
                || path.chars().anyMatch(character -> character < 32
                || character == 127)) {
            throw new FileException("非法公开文件路径", HttpStatus.NOT_FOUND);
        }
        String url = "/files/public" + path;
        // 已登记的私有 URL 和测试数据不得通过公开接口读取。
        boolean forbidden = metadata.lambdaQuery()
                .eq(File::getUrl, url)
                .and(record -> record.ne(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                        .or()
                        .isNull(File::getIsPublic)
                        .or()
                        .eq(File::getBizType, FileBizType.TEST_DATA))
                .exists();
        if (forbidden) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        File file = files.resolvePublicUrl(url);
        if (file == null || file.getStorageProfile() == null || file.getStorageProfile().isBlank()
                || file.getBizType() == FileBizType.TEST_DATA
                || !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        String name = file.getFileName();
        String contentType = file.getFileType();
        boolean inline = contentType != null && INLINE_TYPES.contains(contentType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(inline ? contentType : "application/octet-stream"));
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic());
        if (file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(files.open(file)));
    }
}

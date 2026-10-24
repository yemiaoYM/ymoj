package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 通用文件管理，测试数据权限与删除生命周期由业务服务单独检查。
 */
@Slf4j
@RestController
@RequestMapping("/admin/file")
public class AdminFileController {

    @Autowired
    private AdminFileService service;

    @Autowired
    private StoredFileService storedFiles;

    /**
     * 按权限范围分页查询文件。
     * @param query 分页及文件筛选条件
     * @return 当前用户可见的文件分页
     */
    @PreAuthorize("@ss.hasAnyPermi('file:file:list', 'problem:testcase:query')")
    @GetMapping("/list")
    public Result<PageVO<AdminFileVO>> list(@Validated AdminFilePageQuery query) {
        log.info("查询文件列表，页码：{}", query.getPageNum());
        return Result.ok(service.queryList(query));
    }

    /**
     * 校验下载权限后返回文件流。
     * @param id 文件编号
     * @return 附件流及禁止缓存的响应头
     */
    @PreAuthorize("@ss.hasAnyPermi('file:file:download', 'problem:testcase:query')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        log.info("下载管理文件，文件编号：{}", id);
        File file = service.requireDownload(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.getFileName(), StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());
        if (file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(openDownload(file)));
    }

    /**
     * 上传普通文件，公开范围由服务校验。
     * @param file 待上传文件
     * @param isPublic 0 为私有，1 为公开
     * @return 文件元数据及可执行操作
     */
    @PreAuthorize("@ss.hasPermi('file:file:upload')")
    @PostMapping("/upload")
    public Result<AdminFileVO> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("isPublic") Integer isPublic) {
        log.info("上传管理文件，公开状态：{}", isPublic);
        return Result.ok(service.upload(file, isPublic));
    }

    /**
     * 按文件权限和业务关联约束删除文件。
     * @param id 文件编号
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:file:remove')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("删除管理文件，文件编号：{}", id);
        service.delete(id);
        return Result.ok();
    }

    private InputStream openDownload(File file) {
        String url = file.getUrl();
        boolean legacyPublic = file.getBizType() != FileBizType.TEST_DATA
                && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())
                && file.getStorageProfile() == null
                && Integer.valueOf(FileConstants.STORAGE_LOCAL).equals(file.getStorageType())
                && url != null && url.startsWith("/files/public/");
        if (legacyPublic) {
            String relative = url.substring("/files/public/".length());
            if (relative.isEmpty() || relative.contains("%") || relative.contains("\\")
                    || relative.chars().anyMatch(character -> character < 32 || character == 127)
                    || Arrays.stream(relative.split("/")).anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
                throw new FileException("非法历史公开文件路径", HttpStatus.NOT_FOUND);
            }
            // 原节点绝对路径不用于回退；存储层再校验相对路径位于本地 public 目录。
            return storedFiles.openLegacyPublic(relative);
        }
        return storedFiles.open(file);
    }
}

package com.ymcloud.web.controller.internal;

import com.ymcloud.pojo.entity.ProblemCase;
import com.ymcloud.web.service.file.ProblemCaseService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 供 Worker 读取当前清单及属于指定题目的不可变文件
 */
@RestController
@RequestMapping("/internal/judge-worker/problems/{problemId}")
@PreAuthorize("hasAuthority('ROLE_JUDGE_WORKER')")
public class JudgeWorkerDataController {

    @Autowired
    private ProblemCaseService problemCases;

    /**
     * 从同一条发布记录读取版本和清单。
     * @param problemId 题目主键
     * @param cachedTag Worker 持有的完整清单版本
     * @return 当前清单、未修改或尚未发布
     */
    @GetMapping("/manifest")
    public ResponseEntity<byte[]> manifest(@PathVariable("problemId") Long problemId,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String cachedTag) {
        ProblemCase row = problemCases.current(problemId);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        String tag = '"' + row.getVersion().toString() + '"';
        if (tag.equals(cachedTag)) {
            return ResponseEntity.status(304).eTag(tag).build();
        }
        return ResponseEntity.ok()
                .eTag(tag)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(row.getManifest().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 传输本题不可变测试文件，不受后续清单发布影响。
     * @param problemId 题目主键
     * @param fileId 执行清单中的文件主键
     * @return 从登记的存储服务读取的原始字节
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable("problemId") Long problemId,
                                         @PathVariable("fileId") Long fileId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(problemCases.openFile(problemId, fileId)));
    }
}

package com.ymcloud.web.controller.internal;

import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.web.service.internal.JudgeWorkerDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 供 Worker 读取已发布版本，文件必须属于指定题目与版本清单
 */
@Slf4j
@RestController
@RequestMapping("/internal/judge-worker/problems/{problemId}/versions/{versionId}")
@PreAuthorize("hasAuthority('ROLE_JUDGE_WORKER')")
public class JudgeWorkerDataController {

    @Autowired
    private JudgeWorkerDataService judgeWorkerDataService;

    /**
     * 返回已校验版本的原始清单，避免重新序列化改变哈希。
     * @param problemId 题目编号，必须为正数
     * @param versionId 属于该题目的已发布版本编号
     * @return 原始 UTF-8 清单字节及内容哈希
     */
    @GetMapping("/manifest")
    public ResponseEntity<byte[]> manifest(@PathVariable("problemId") Long problemId,
                                          @PathVariable("versionId") Long versionId) {
        log.debug("Worker 读取测试清单，题目编号：{}，版本编号：{}", problemId, versionId);
        TestDataVersion version = judgeWorkerDataService.getVerifiedVersion(problemId, versionId);
        byte[] body = version.getManifest().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Content-SHA256", version.getManifestSha256())
                .body(body);
    }

    /**
     * 下载指定版本清单中的文件，并验证实际传输长度。
     * @param problemId 题目编号，必须为正数
     * @param versionId 属于该题目的已发布版本编号
     * @param fileId 必须属于该版本清单的文件编号
     * @return 文件流、内容长度及哈希；不公开内部存储路径
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<StreamingResponseBody> file(@PathVariable("problemId") Long problemId,
                                                     @PathVariable("versionId") Long versionId,
                                                     @PathVariable("fileId") Long fileId) {
        log.debug("Worker 读取测试文件，题目编号：{}，版本编号：{}，文件编号：{}", problemId, versionId, fileId);
        JudgeWorkerDataService.FileReference file = judgeWorkerDataService.resolveFile(problemId, versionId, fileId);
        long expectedSize = file.size();
        StreamingResponseBody body = output -> {
            try (InputStream input = judgeWorkerDataService.openFile(problemId, versionId, file)) {
                byte[] buffer = new byte[8192];
                long copied = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > expectedSize) {
                        throw new IOException("Test data size mismatch");
                    }
                    output.write(buffer, 0, count);
                }
                if (copied != expectedSize) {
                    throw new IOException("Test data size mismatch");
                }
            }
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(expectedSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + ".dat\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Content-SHA256", file.sha256())
                .body(body);
    }

}

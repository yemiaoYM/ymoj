package com.ymcloud.web.controller.internal;

import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.TestDataVersion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 仅供 Worker 读取已发布版本。下载文件必须同时属于指定题目与版本清单。
 */
@RestController
@RequestMapping("/internal/judge-worker/problems/{problemId}/versions/{versionId}")
@PreAuthorize("hasAuthority('ROLE_JUDGE_WORKER')")
public class JudgeWorkerDataController {

    private final TestDataService testDataService;
    private final TestDataVersionEntityService versionEntityService;

    public JudgeWorkerDataController(TestDataService testDataService,
                                     TestDataVersionEntityService versionEntityService) {
        this.testDataService = testDataService;
        this.versionEntityService = versionEntityService;
    }

    @GetMapping("/manifest")
    public ResponseEntity<byte[]> manifest(@PathVariable("problemId") Long problemId,
                                          @PathVariable("versionId") Long versionId) {
        requireIds(problemId, versionId);
        // 先验证版本内容及哈希，再返回原始清单，避免 JSON 重新序列化改变其哈希。
        testDataService.getManifest(problemId, versionId);
        TestDataVersion version = versionEntityService.lambdaQuery()
                .eq(TestDataVersion::getId, versionId)
                .eq(TestDataVersion::getProblemId, problemId)
                .one();
        if (version == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        byte[] body = version.getManifest().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Content-SHA256", version.getManifestSha256())
                .body(body);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<StreamingResponseBody> file(@PathVariable("problemId") Long problemId,
                                                     @PathVariable("versionId") Long versionId,
                                                     @PathVariable("fileId") Long fileId) {
        requireIds(problemId, versionId);
        if (fileId == null || fileId <= 0) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        TestDataManifest manifest = testDataService.getManifest(problemId, versionId);
        String logicalPath = null;
        Long size = null;
        String hash = null;
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            if (Objects.equals(fileId, entry.getInputFileId())) {
                logicalPath = entry.getInput();
                size = entry.getInputSize();
                hash = entry.getInputSha256();
                break;
            }
            if (Objects.equals(fileId, entry.getOutputFileId())) {
                logicalPath = entry.getOutput();
                size = entry.getOutputSize();
                hash = entry.getOutputSha256();
                break;
            }
        }
        if (logicalPath == null || size == null || size < 0) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        String path = logicalPath;
        long expectedSize = size;
        StreamingResponseBody body = output -> {
            try (InputStream input = testDataService.openFile(problemId, versionId, path)) {
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
                .header("X-Content-SHA256", hash)
                .body(body);
    }

    private void requireIds(Long problemId, Long versionId) {
        if (problemId == null || problemId <= 0 || versionId == null || versionId <= 0) {
            throw new ResponseStatusException(NOT_FOUND);
        }
    }
}

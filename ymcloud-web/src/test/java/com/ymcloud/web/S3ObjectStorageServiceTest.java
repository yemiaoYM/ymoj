package com.ymcloud.web;

import com.ymcloud.core.storage.client.S3ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 验证 S3 驱动保留对象定位、上传内容和失败传播契约
 */
class S3ObjectStorageServiceTest {

    @TempDir
    Path directory;

    /**
     * 验证上传内容与元数据、删除定位及关闭行为，不连接云服务
     * @throws Exception 暂存内容读写失败
     */
    @Test
    void uploadsExactObjectAndPropagatesDeleteFailure() throws Exception {
        S3Client client = mock(S3Client.class);
        Path source = directory.resolve("data.in");
        byte[] bytes = new byte[]{1, 2, 3};
        Files.write(source, bytes);
        try (S3ObjectStorageService storage = new S3ObjectStorageService(client)) {
            storage.put("private", "version/input", source, "text/plain", "content-hash");
            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
            verify(client).putObject(request.capture(), body.capture());
            assertEquals("private", request.getValue().bucket());
            assertEquals("version/input", request.getValue().key());
            assertEquals("text/plain", request.getValue().contentType());
            assertEquals("Uonfc331cyb83SJZevsfrA==", request.getValue().contentMD5());
            assertEquals("content-hash", request.getValue().metadata().get("sha256"));
            try (var input = body.getValue().contentStreamProvider().newStream()) {
                assertArrayEquals(bytes, input.readAllBytes());
            }
            doThrow(new IllegalStateException("storage unavailable"))
                    .when(client).deleteObject(any(DeleteObjectRequest.class));
            assertThrows(IllegalStateException.class, () -> storage.delete("private", "version/input"));
            verify(client).deleteObject(DeleteObjectRequest.builder().bucket("private").key("version/input").build());
        }
        verify(client).close();
    }
}

package com.ymcloud.web;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.ymcloud.core.storage.client.OssObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 使用 SDK 桩验证原生 OSS 驱动的上传与关闭契约
 */
class OssObjectStorageServiceTest {

    @TempDir
    Path root;

    @Test
    void nativeOssUploadsMetadataReadsDeletesAndCloses() throws Exception {
        OSS client = mock(OSS.class);
        OssObjectStorageService storage = new OssObjectStorageService(client);
        Path source = root.resolve("source");
        Files.write(source, new byte[]{1, 2, 3});
        storage.put("private-bucket", "key", source, "text/plain", "hash");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture());
        assertEquals("private-bucket", request.getValue().getBucketName());
        assertEquals("key", request.getValue().getKey());
        assertEquals(3, request.getValue().getMetadata().getContentLength());
        assertEquals("hash", request.getValue().getMetadata().getUserMetadata().get("sha256"));
        OSSObject object = new OSSObject();
        object.setObjectContent(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(client.getObject("private-bucket", "key")).thenReturn(object);
        try (var input = storage.open("private-bucket", "key")) {
            assertArrayEquals(new byte[]{1, 2, 3}, input.readAllBytes());
        }
        storage.delete("private-bucket", "key");
        verify(client).deleteObject("private-bucket", "key");
        storage.close();
        verify(client).shutdown();
    }
}

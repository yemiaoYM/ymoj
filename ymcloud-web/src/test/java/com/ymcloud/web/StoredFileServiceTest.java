package com.ymcloud.web;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.core.storage.util.StoragePaths;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.file.impl.FileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 使用真实本地驱动核对字节、哈希、失败补偿与路径边界
 */
class StoredFileServiceTest {

    @TempDir
    Path root;

    private FileEntityService entities;
    private StorageProperties properties;
    private StorageRegistry registry;
    private FileService files;

    /**
     * 装配本地存储及文件元数据桩，使用真实驱动验证文件生命周期
     */
    @BeforeEach
    void setup() {
        entities = mock(FileEntityService.class);
        properties = new StorageProperties();
        registry = new StorageRegistry(properties, root.toString());
        files = new FileServiceImpl();
        ReflectionTestUtils.setField(files, "files", entities);
        ReflectionTestUtils.setField(files, "storage", registry);
        when(entities.save(any(File.class))).thenAnswer(invocation -> {
            invocation.<File>getArgument(0).setId(10L);
            return true;
        });
    }

    /**
     * 保存 UTF-8 原始字节及摘要，私有测试数据不标记公开
     * @throws Exception 文件读写或测试资源处理失败
     */
    @Test
    void savesExactUtf8BytesAndHashWithoutExposingPrivateUrl() throws Exception {
        byte[] content = "  中文\r\n\t尾部  ".getBytes(StandardCharsets.UTF_8);
        File file = files.create(new CreateStoredFileCommand("样例.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(content), content.length);
        assertEquals(0, file.getIsPublic());
        assertEquals("private", file.getBucket());
        assertEquals(content.length, file.getFileSize());
        assertEquals(FileDigests.sha256(new ByteArrayInputStream(content)), file.getSha256());
        try (var input = files.open(file)) {
            assertArrayEquals(content, input.readAllBytes());
        }
    }

    /**
     * 允许合法的零字节输入输出文件
     * @throws Exception 文件读写或测试资源处理失败
     */
    @Test
    void permitsEmptyTestFiles() throws Exception {
        File file = files.create(new CreateStoredFileCommand("empty.out", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[0]), 0);
        try (var input = files.open(file)) {
            assertEquals(-1, input.read());
        }
    }

    /**
     * 切换默认配置后原文件仍按固定位置读取，对外标识与对象键独立
     * @throws Exception 文件读写或测试资源处理失败
     */
    @Test
    void oldProfileRemainsReadableAfterDefaultSwitch() throws Exception {
        byte[] content = {1, 2, 3};
        File file = files.create(new CreateStoredFileCommand("avatar.png", "image/png", "image", FileBizType.IMAGE, null, true, null, null),
                new ByteArrayInputStream(content), content.length);
        StorageProperties.Profile local = new StorageProperties.Profile();
        local.setDriverType(StorageDriverType.LOCAL);
        properties.getProfiles().put("another", local);
        properties.setActive("another");
        assertEquals("another", registry.active());
        try (var input = files.open(file)) {
            assertArrayEquals(content, input.readAllBytes());
        }
        assertTrue(file.getPublicId().matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"));
        assertNotEquals(file.getPublicId(), Path.of(file.getObjectKey()).getFileName().toString().replace(".png", ""));
    }

    /**
     * 登记失败补偿已上传对象并删除暂存文件
     * @throws Exception 文件读写或测试资源处理失败
     */
    @Test
    void dbInsertFailureRemovesUploadedObjectAndStaging() throws Exception {
        when(entities.save(any(File.class))).thenReturn(false);
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[]{1}), 1));
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    /**
     * 外层事务回滚时删除本次创建的对象
     * @throws Exception 文件读写或测试资源处理失败
     */
    @Test
    void enclosingTransactionRollbackRemovesNewObject() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            File file = files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[]{1}), 1);
            Path object = root.resolve(file.getBucket() + "/" + file.getObjectKey());
            assertTrue(Files.exists(object));
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(Files.exists(object));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 拒绝公开测试数据及不存在的存储配置
     */
    @Test
    void rejectsPrivateTestDataMarkedPublicAndUnknownProfile() {
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, true, null, null),
                new ByteArrayInputStream(new byte[0]), 0));
        properties.setActive("missing");
        assertThrows(FileException.class, registry::active);
        verifyNoInteractions(entities);
    }

    /**
     * 拒绝超限或与实际内容长度不一致的上传
     */
    @Test
    void rejectsOversizeAndDishonestContentLength() {
        properties.setMaxFileSize(2);
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[3]), 3));
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[2]), 1));
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata", FileBizType.TEST_DATA, 1L, false, null, null),
                new ByteArrayInputStream(new byte[1]), 2));
        verifyNoInteractions(entities);
    }

    /**
     * 对象键和文件名拒绝目录穿越、绝对路径及控制字符
     */
    @Test
    void rejectsTraversalAbsolutePathsAndControlCharacters() {
        for (String path : new String[]{"../private/a.in", "public/../../secret", "E:/secret", "public\\a", "a\0b"}) {
            assertThrows(FileException.class, () -> StoragePaths.resolve(root, path), path);
        }
        assertThrows(FileException.class, () -> files.create(new CreateStoredFileCommand("bad\r\nname.png", "image/png", "image", FileBizType.IMAGE, null, true, null, null),
                new ByteArrayInputStream(new byte[0]), 0));
        assertThrows(FileException.class, () -> StoragePaths.validateObjectKey("../private/a.in"));
    }

    /**
     * 远程配置要求凭据完整且公开私有桶不同
     */
    @Test
    void remoteProfilesRequireCredentialsAndDistinctBuckets() {
        StorageProperties.Profile remote = new StorageProperties.Profile();
        remote.setDriverType(StorageDriverType.S3);
        remote.setEndpoint("http://127.0.0.1:18333");
        properties.getProfiles().put("seaweedfs", remote);
        assertThrows(FileException.class, () -> registry.profile("seaweedfs"));
        remote.setAccessKey("test");
        remote.setSecretKey("test");
        remote.setPrivateBucket(remote.getPublicBucket());
        assertThrows(FileException.class, () -> registry.profile("seaweedfs"));
    }

    /**
     * 验证回读摘要不一致时先补偿对象且不写入文件登记
     */
    @Test
    void remoteReadBackHashMismatchIsCompensatedBeforeDatabaseInsert() {
        StorageRegistry routed = spy(registry);
        ObjectStorageService corrupted = mock(ObjectStorageService.class);
        doReturn(corrupted).when(routed).lease("local");
        when(corrupted.open(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{9}));
        FileService service = new FileServiceImpl();
        ReflectionTestUtils.setField(service, "files", entities);
        ReflectionTestUtils.setField(service, "storage", routed);
        assertThrows(FileException.class, () -> service.create(new CreateStoredFileCommand("a.in", "text/plain", "problem/1/testdata",
                FileBizType.TEST_DATA, 1L, false, null, null), new ByteArrayInputStream(new byte[]{1}), 1));
        verify(corrupted).delete(eq("private"), anyString());
        verify(corrupted).close();
        verifyNoInteractions(entities);
    }

    /**
     * 远程明文 HTTP 被拒绝且错误消息不暴露凭据
     */
    @Test
    void remoteHttpOutsideLoopbackIsRejectedWithoutLeakingCredentials() {
        StorageProperties.Profile remote = new StorageProperties.Profile();
        remote.setDriverType(StorageDriverType.S3);
        remote.setEndpoint("http://storage.internal:8333");
        remote.setAccessKey("key-must-not-appear");
        remote.setSecretKey("secret-must-not-appear");
        properties.getProfiles().put("seaweedfs", remote);
        FileException exception = assertThrows(FileException.class, () -> registry.profile("seaweedfs"));
        assertFalse(exception.getMessage().contains(remote.getSecretKey()));
        assertFalse(exception.getMessage().contains(remote.getAccessKey()));
        remote.setEndpoint("https://storage.example.org");
        assertSame(remote, registry.profile("seaweedfs"));
    }
}

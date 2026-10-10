package com.ymcloud.web;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.FileDigests;
import com.ymcloud.core.storage.ObjectStorageService;
import com.ymcloud.core.storage.StoragePaths;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * 使用真实本地驱动核对字节、哈希、失败补偿与路径边界。
 */
class StoredFileServiceTest {

    @TempDir
    Path root;

    private FileEntityService entities;
    private StorageProperties properties;
    private StorageRegistry registry;
    private StoredFileService files;

    @BeforeEach
    void setup() {
        entities = mock(FileEntityService.class);
        properties = new StorageProperties();
        registry = new StorageRegistry(properties, root.toString());
        files = new StoredFileService(entities, registry);
        when(entities.save(any(File.class))).thenAnswer(invocation -> {
            invocation.<File>getArgument(0).setId(10L);
            return true;
        });
    }

    @Test
    void savesExactUtf8BytesAndHashWithoutExposingPrivateUrl() throws Exception {
        byte[] content = "  中文\r\n\t尾部  ".getBytes(StandardCharsets.UTF_8);
        File file = files.save(new ByteArrayInputStream(content), content.length, "样例.in", "text/plain",
                FileBizType.TEST_DATA, 1L, false);
        assertNull(file.getUrl());
        assertEquals("private", file.getBucket());
        assertEquals(content.length, file.getFileSize());
        assertEquals(FileDigests.sha256(new ByteArrayInputStream(content)), file.getSha256());
        try (var input = files.open(file)) {
            assertArrayEquals(content, input.readAllBytes());
        }
    }

    @Test
    void permitsEmptyTestFiles() throws Exception {
        File file = files.save(new ByteArrayInputStream(new byte[0]), 0, "empty.out", "text/plain",
                FileBizType.TEST_DATA, 1L, false);
        try (var input = files.open(file)) {
            assertEquals(-1, input.read());
        }
    }

    @Test
    void oldProfileRemainsReadableAfterDefaultSwitch() throws Exception {
        byte[] content = {1, 2, 3};
        File file = files.save(new ByteArrayInputStream(content), content.length, "avatar.png", "image/png",
                FileBizType.IMAGE, null, true);
        properties.getProfiles().put("another", new StorageProperties.Profile());
        properties.setActive("another");
        assertEquals("another", registry.active());
        try (var input = files.open(file)) {
            assertArrayEquals(content, input.readAllBytes());
        }
        assertTrue(file.getUrl().startsWith("/files/public/local/"));
    }

    @Test
    void dbInsertFailureRemovesUploadedObjectAndStaging() throws Exception {
        when(entities.save(any(File.class))).thenReturn(false);
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[]{1}), 1,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, false));
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void enclosingTransactionRollbackRemovesNewObject() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            File file = files.save(new ByteArrayInputStream(new byte[]{1}), 1, "a.in", "text/plain",
                    FileBizType.TEST_DATA, 1L, false);
            Path object = root.resolve(file.getStoragePath());
            assertTrue(Files.exists(object));
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(Files.exists(object));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsPrivateTestDataMarkedPublicAndUnknownProfile() {
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[0]), 0,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, true));
        properties.setActive("missing");
        assertThrows(FileException.class, registry::active);
        verifyNoInteractions(entities);
    }

    @Test
    void rejectsOversizeAndDishonestContentLength() {
        properties.setMaxFileSize(2);
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[3]), 3,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, false));
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[2]), 1,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, false));
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[1]), 2,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, false));
        verifyNoInteractions(entities);
    }

    @Test
    void rejectsTraversalAbsolutePathsAndControlCharacters() {
        for (String path : new String[]{"../private/a.in", "public/../../secret", "E:/secret", "public\\a", "a\0b"}) {
            assertThrows(FileException.class, () -> StoragePaths.resolve(root, path), path);
        }
        assertThrows(FileException.class, () -> files.save(new ByteArrayInputStream(new byte[0]), 0,
                "bad\r\nname.png", "image/png", FileBizType.IMAGE, null, true));
        assertThrows(FileException.class, () -> files.openLegacyPublic("../private/a.in"));
    }

    @Test
    void remoteProfilesRequireCredentialsAndDistinctBuckets() {
        StorageProperties.Profile remote = new StorageProperties.Profile();
        remote.setType("s3");
        remote.setEndpoint("http://127.0.0.1:18333");
        properties.getProfiles().put("seaweedfs", remote);
        assertThrows(FileException.class, () -> registry.profile("seaweedfs"));
        remote.setAccessKey("test");
        remote.setSecretKey("test");
        remote.setPrivateBucket(remote.getPublicBucket());
        assertThrows(FileException.class, () -> registry.profile("seaweedfs"));
    }

    @Test
    void remoteReadBackHashMismatchIsCompensatedBeforeDatabaseInsert() {
        StorageRegistry routed = spy(registry);
        ObjectStorageService corrupted = mock(ObjectStorageService.class);
        doReturn(corrupted).when(routed).lease("local");
        when(corrupted.open(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{9}));
        StoredFileService service = new StoredFileService(entities, routed);
        assertThrows(FileException.class, () -> service.save(new ByteArrayInputStream(new byte[]{1}), 1,
                "a.in", "text/plain", FileBizType.TEST_DATA, 1L, false));
        verify(corrupted).delete(eq("private"), anyString());
        verify(corrupted).close();
        verifyNoInteractions(entities);
    }

    @Test
    void remoteHttpOutsideLoopbackIsRejectedWithoutLeakingCredentials() {
        StorageProperties.Profile remote = new StorageProperties.Profile();
        remote.setType("s3");
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

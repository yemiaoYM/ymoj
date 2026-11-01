package com.ymcloud.web;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.storage.FileDigests;
import com.ymcloud.core.storage.ObjectStorageService;
import com.ymcloud.core.storage.StorageConfigCatalog;
import com.ymcloud.core.storage.StorageConnectionTester;
import com.ymcloud.core.storage.StorageCredentialCipher;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.admin.impl.AdminStorageConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 恢复丢失连接先逐一验证旧对象，校验失败不保存且不修改对象
 */
class StorageConfigRestoreTest {

    private StorageProperties properties;
    private StorageCredentialCipher cipher;
    private StorageConfigCatalog catalog;
    private StorageRegistry registry;
    private ObjectStorageService driver;
    private StorageConnectionTester tester;
    private AdminStorageConfigServiceImpl service;
    private AnnotationConfigApplicationContext context;

    /**
     * 初始化隔离依赖，由 Spring 注入存储配置服务
     */
    @BeforeEach
    void setup() {
        properties = new StorageProperties();
        properties.setConfigEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new StorageCredentialCipher(properties);
        catalog = mock(StorageConfigCatalog.class);
        registry = mock(StorageRegistry.class);
        driver = mock(ObjectStorageService.class);
        tester = new StorageConnectionTester(registry);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(StorageProperties.class, () -> properties);
        context.registerBean(StorageConfigCatalog.class, () -> catalog);
        context.registerBean(StorageCredentialCipher.class, () -> cipher);
        context.registerBean(StorageConnectionTester.class, () -> tester);
        context.register(AdminStorageConfigServiceImpl.class);
        context.refresh();
        service = context.getBean(AdminStorageConfigServiceImpl.class);
        StorageConfigState state = new StorageConfigState();
        state.setId(1);
        state.setRevision(0L);
        when(catalog.lockForWrite()).thenReturn(state);
        when(catalog.active()).thenReturn("local");
        when(registry.createClient(any())).thenReturn(driver);
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.OK);
        LoginUser login = new LoginUser(user, List.of("ADMIN"), List.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, "", login.getAuthorities()));
    }

    /**
     * 清理认证状态并关闭隔离的 Spring 上下文
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void restorationReadsEveryObjectAndStillRequiresManualTestBeforeActivation() throws Exception {
        byte[] publicBytes = new byte[128 * 1024 + 3];
        Arrays.fill(publicBytes, (byte) ' ');
        publicBytes[publicBytes.length - 1] = '\n';
        byte[] privateBytes = " \n  input\n\t ".getBytes(StandardCharsets.UTF_8);
        File publicFile = reference(1, true, "public-object", publicBytes);
        File privateFile = reference(2, false, "private-object", privateBytes);
        File emptyFile = reference(3, false, "empty-object", new byte[0]);
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(publicFile, privateFile, emptyFile));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-public", "public-object")).thenReturn(tracked(publicBytes, closed));
        when(driver.open("test-private", "private-object")).thenReturn(tracked(privateBytes, closed));
        when(driver.open("test-private", "empty-object")).thenReturn(tracked(new byte[0], closed));

        service.create(dto());

        assertEquals(3, closed.get());
        verify(driver).open("test-public", "public-object");
        verify(driver).open("test-private", "private-object");
        verify(driver).open("test-private", "empty-object");
        verify(driver).close();
        assertReadOnly();
        ArgumentCaptor<StorageConfig> capture = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(capture.capture(), eq(false));
        StorageConfig config = capture.getValue();
        assertEquals(1L, config.getRevision());
        assertNull(config.getTestedRevision());
        assertNull(config.getTestedAt());
        assertEquals("access-value", cipher.decrypt("seaweedfs", "accessKey", config.getEncryptedAccessKey()));
        assertEquals("secret-value", cipher.decrypt("seaweedfs", "secretKey", config.getEncryptedSecretKey()));
        when(catalog.find("seaweedfs")).thenReturn(config);
        assertThrows(FileException.class, () -> service.activate("seaweedfs"));
        verify(catalog, never()).activate(any(), anyString());
    }

    @Test
    void wrongBucketsAndInvalidVisibilityRejectBeforeConnecting() {
        byte[] bytes = new byte[]{1};
        File publicInPrivate = reference(1, true, "object", bytes);
        publicInPrivate.setBucket("test-private");
        File privateInPublic = reference(2, false, "object", bytes);
        privateInPublic.setBucket("test-public");
        File unrelated = reference(3, false, "object", bytes);
        unrelated.setBucket("another-private");
        File missingVisibility = reference(4, false, "object", bytes);
        missingVisibility.setIsPublic(null);
        File unknownVisibility = reference(5, false, "object", bytes);
        unknownVisibility.setIsPublic(2);
        for (File file : List.of(publicInPrivate, privateInPublic, unrelated, missingVisibility, unknownVisibility)) {
            when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(file));
            assertSanitizedFailure(() -> service.create(dto()));
        }
        verifyNoInteractions(registry, driver);
        assertNotSaved();
    }

    @Test
    void unverifiableMetadataIsRejectedBeforeAnyObjectRead() {
        List<Consumer<File>> invalidMetadata = List.of(
                file -> file.setSha256(null),
                file -> file.setSha256(""),
                file -> file.setSha256("x".repeat(64)),
                file -> file.setSha256("a".repeat(63)),
                file -> file.setFileSize(null),
                file -> file.setFileSize(-1L),
                file -> file.setObjectKey(null),
                file -> file.setObjectKey(" "),
                file -> file.setObjectKey("object\nkey"));
        for (Consumer<File> mutation : invalidMetadata) {
            File file = reference(1, false, "object", new byte[]{1});
            mutation.accept(file);
            when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(file));
            assertSanitizedFailure(() -> service.create(dto()));
        }
        verifyNoInteractions(registry, driver);
        assertNotSaved();
    }

    @Test
    void missingObjectIsSanitizedAndClientClosedWithoutSaving() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1})));
        when(driver.open("test-private", "object"))
                .thenThrow(new IllegalStateException("secret-value signed URL missing object"));

        assertSanitizedFailure(() -> service.create(dto()));

        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void wrongHashWithCorrectSizeClosesStreamAndRejectsPublication() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1, 2})));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-private", "object")).thenReturn(tracked(new byte[]{1, 3}, closed));

        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(1, closed.get());
        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void shorterAndLongerObjectsBothRejectAndCloseStreams() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1, 2})));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-private", "object"))
                .thenReturn(tracked(new byte[]{1}, closed), tracked(new byte[]{1, 2, 3}, closed));

        assertSanitizedFailure(() -> service.create(dto()));
        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(2, closed.get());
        verify(driver, times(2)).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void failureOnLaterObjectDoesNotSaveAfterEarlierObjectPasses() {
        File first = reference(1, true, "first", new byte[]{1});
        File second = reference(2, false, "second", new byte[]{2});
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(first, second));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-public", "first")).thenReturn(tracked(new byte[]{1}, closed));
        when(driver.open("test-private", "second"))
                .thenThrow(new IllegalStateException("secret-value signed URL"));

        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(1, closed.get());
        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void streamReadFailureAndClientCloseFailureDoNotLeakRemoteErrors() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1})));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-private", "object")).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("secret-value remote stream error");
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        });
        doThrow(new IllegalStateException("secret-value client close error")).when(driver).close();

        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(1, closed.get());
        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void streamCloseFailureRejectsOtherwiseValidObjectAndClosesClient() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1})));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-private", "object")).thenReturn(new ByteArrayInputStream(new byte[]{1}) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                throw new IOException("secret-value response close error");
            }
        });

        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(1, closed.get());
        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void clientCloseFailureRejectsOtherwiseValidRestore() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1})));
        AtomicInteger closed = new AtomicInteger();
        when(driver.open("test-private", "object")).thenReturn(tracked(new byte[]{1}, closed));
        doThrow(new IllegalStateException("secret-value close error")).when(driver).close();

        assertSanitizedFailure(() -> service.create(dto()));

        assertEquals(1, closed.get());
        verify(driver).close();
        assertReadOnly();
        assertNotSaved();
    }

    @Test
    void clientCreationFailureDoesNotPersistOrExposeCredentials() {
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(reference(1, false, "object", new byte[]{1})));
        when(registry.createClient(any())).thenThrow(new IllegalStateException("secret-value credential failure"));

        assertSanitizedFailure(() -> service.create(dto()));

        verifyNoInteractions(driver);
        assertNotSaved();
    }

    @Test
    void existingDatabaseOrYamlConfigurationCannotUseRestorePath() {
        StorageConfig existing = new StorageConfig();
        existing.setId("seaweedfs");
        when(catalog.find("seaweedfs")).thenReturn(existing);
        assertThrows(FileException.class, () -> service.create(dto()));
        when(catalog.find("seaweedfs")).thenReturn(null);
        properties.getProfiles().put("SeaweedFS", new StorageProperties.Profile());
        assertThrows(FileException.class, () -> service.create(dto()));

        verify(catalog, never()).referencedFiles(anyString());
        verifyNoInteractions(registry, driver);
        assertNotSaved();
    }

    @Test
    void caseAliasCannotBindToHistoricalProfileReferences() {
        File file = reference(1, false, "object", new byte[]{1});
        file.setStorageProfile("SeaweedFS");
        when(catalog.referencedFiles("seaweedfs")).thenReturn(List.of(file));

        assertSanitizedFailure(() -> service.create(dto()));

        verifyNoInteractions(registry, driver);
        assertNotSaved();
    }

    @Test
    void localDriverCannotBypassRemoteObjectValidation() {
        StorageProperties.Profile local = new StorageProperties.Profile();
        assertSanitizedFailure(() -> tester.verifyReferencedFiles(local,
                List.of(reference(1, false, "object", new byte[]{1}))));

        verifyNoInteractions(registry, driver);
    }

    private File reference(long id, boolean publicAccess, String objectKey, byte[] bytes) {
        File file = new File();
        file.setId(id);
        file.setStorageType(3);
        file.setStorageProfile("seaweedfs");
        file.setIsPublic(publicAccess ? 1 : 0);
        file.setBucket(publicAccess ? "test-public" : "test-private");
        file.setObjectKey(objectKey);
        file.setFileSize((long) bytes.length);
        file.setSha256(HexFormat.of().formatHex(FileDigests.digest().digest(bytes)));
        return file;
    }

    private InputStream tracked(byte[] bytes, AtomicInteger closed) {
        return new ByteArrayInputStream(bytes) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        };
    }

    private StorageConfigDTO dto() {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey("seaweedfs");
        dto.setName("SeaweedFS");
        dto.setProvider("seaweedfs");
        dto.setEndpoint("http://127.0.0.1:8333");
        dto.setRegion("us-east-1");
        dto.setPublicBucket("test-public");
        dto.setPrivateBucket("test-private");
        dto.setPathStyle(true);
        dto.setAccessKey("access-value");
        dto.setSecretKey("secret-value");
        return dto;
    }

    private void assertSanitizedFailure(Runnable operation) {
        FileException error = assertThrows(FileException.class, operation::run);
        assertEquals(400, error.getCode());
        assertFalse(error.getMessage().contains("secret-value"));
        assertNull(error.getCause());
    }

    private void assertReadOnly() {
        verify(driver, never()).put(anyString(), anyString(), any(), anyString(), anyString());
        verify(driver, never()).delete(anyString(), anyString());
    }

    private void assertNotSaved() {
        verify(catalog, never()).save(any(), anyBoolean());
    }
}

package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.storage.ObjectStorageService;
import com.ymcloud.core.storage.StorageConfigCatalog;
import com.ymcloud.core.storage.StorageConfigValidation;
import com.ymcloud.core.storage.StorageConnectionTester;
import com.ymcloud.core.storage.StorageCredentialCipher;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminStorageConfigController;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import com.ymcloud.web.service.admin.impl.AdminStorageConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 配置权限、加密、历史定位与连接退休的回归，不启动外部服务。 */
class StorageConfigSecurityTest {

    @TempDir
    private Path root;

    private StorageProperties properties;
    private StorageCredentialCipher cipher;
    private StorageConfigCatalog catalog;
    private StorageConnectionTester tester;
    private AdminStorageConfigServiceImpl service;

    @BeforeEach
    void setup() {
        properties = new StorageProperties();
        properties.setConfigEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new StorageCredentialCipher(properties);
        catalog = mock(StorageConfigCatalog.class);
        tester = mock(StorageConnectionTester.class);
        service = new AdminStorageConfigServiceImpl(properties, catalog, cipher, tester);
        StorageConfigState state = new StorageConfigState();
        state.setId(1);
        state.setRevision(0L);
        when(catalog.lockForWrite()).thenReturn(state);
        when(catalog.active()).thenReturn("local");
        authenticate(1L, "*:*:*");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void encryptionIsRandomAuthenticatedAndBoundToConfigurationAndField() {
        String first = cipher.encrypt("minio", "secretKey", "sensitive-value");
        String second = cipher.encrypt("minio", "secretKey", "sensitive-value");
        assertNotEquals(first, second);
        assertEquals("sensitive-value", cipher.decrypt("minio", "secretKey", first));
        assertThrows(FileException.class, () -> cipher.decrypt("other", "secretKey", first));
        assertThrows(FileException.class, () -> cipher.decrypt("minio", "accessKey", first));
        byte[] tampered = Base64.getDecoder().decode(first);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(FileException.class, () -> cipher.decrypt("minio", "secretKey",
                Base64.getEncoder().encodeToString(tampered)));
    }

    @Test
    void missingOrInvalidMasterKeyCannotEncrypt() {
        properties.setConfigEncryptionKey(null);
        assertThrows(FileException.class, () -> cipher.encrypt("minio", "secretKey", "value"));
        properties.setConfigEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThrows(FileException.class, cipher::requireKey);
    }

    @Test
    void ordinaryAdministratorCannotWriteTestOrActivateEvenWithExplicitPermissions() {
        authenticate(2L, "file:storage:add", "file:storage:edit", "file:storage:remove",
                "file:storage:test", "file:storage:activate");
        assertThrows(AccessDeniedException.class, () -> service.create(dto()));
        assertThrows(AccessDeniedException.class, () -> service.update("minio", dto()));
        assertThrows(AccessDeniedException.class, () -> service.delete("minio"));
        assertThrows(AccessDeniedException.class, () -> service.test("minio"));
        assertThrows(AccessDeniedException.class, () -> service.activate("minio"));
        verifyNoInteractions(catalog, tester);
    }

    @Test
    void controllerUsesActualListPermissionAndRejectsWildcardForOrdinaryUsers() {
        AdminStorageConfigService backend = mock(AdminStorageConfigService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(MethodSecurityConfig.class, PermissionService.class, AdminStorageConfigController.class);
            context.registerBean(AdminStorageConfigService.class, () -> backend);
            context.refresh();
            AdminStorageConfigController controller = context.getBean(AdminStorageConfigController.class);
            authenticate(2L, "file:storage:list");
            assertDoesNotThrow(controller::list);
            assertThrows(AccessDeniedException.class, () -> controller.create(dto()));
            authenticate(2L, "*:*:*");
            assertThrows(AccessDeniedException.class, controller::list);
            authenticate(1L, "*:*:*");
            assertDoesNotThrow(() -> controller.create(dto()));
            verify(backend).create(any());
        }
    }

    @Test
    void createStoresOnlyEncryptedCredentialsAndRequiresTestBeforeActivation() {
        service.create(dto());
        ArgumentCaptor<StorageConfig> captured = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(captured.capture(), eq(false));
        StorageConfig config = captured.getValue();
        assertNotEquals("access-value", config.getEncryptedAccessKey());
        assertNotEquals("secret-value", config.getEncryptedSecretKey());
        when(catalog.find("minio")).thenReturn(config);
        assertThrows(FileException.class, () -> service.activate("minio"));
        verify(catalog, never()).activate(any(), anyString());
    }

    @Test
    void blankCredentialsPreserveYamlAndActiveUpdateTestsBeforePersistence() {
        StorageProperties.Profile profile = remoteProfile();
        properties.getProfiles().put("minio", profile);
        when(catalog.active()).thenReturn("minio");
        when(catalog.referenced("minio")).thenReturn(true);
        StorageConfigDTO dto = dto();
        dto.setAccessKey(" ");
        dto.setSecretKey(null);
        service.update("minio", dto);
        ArgumentCaptor<StorageConfig> captured = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(captured.capture(), eq(false));
        assertEquals("access-value", cipher.decrypt("minio", "accessKey", captured.getValue().getEncryptedAccessKey()));
        assertEquals("secret-value", cipher.decrypt("minio", "secretKey", captured.getValue().getEncryptedSecretKey()));
        assertEquals(1L, captured.getValue().getTestedRevision());
        verify(tester).test(any());
    }

    @Test
    void activeUpdateFailureDoesNotPersistCandidate() {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.active()).thenReturn("minio");
        doThrow(new FileException("probe failed", 400)).when(tester).test(any());
        assertThrows(FileException.class, () -> service.update("minio", dto()));
        verify(catalog, never()).save(any(), eq(false));
    }

    @Test
    void referencedYamlConfigurationCannotChangeAnyLocationOrProvider() {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.referenced("minio")).thenReturn(true);
        StorageConfigDTO changedEndpoint = dto();
        changedEndpoint.setEndpoint("https://other.example");
        assertThrows(FileException.class, () -> service.update("minio", changedEndpoint));
        StorageConfigDTO changedProvider = dto();
        changedProvider.setProvider("seaweedfs");
        assertThrows(FileException.class, () -> service.update("minio", changedProvider));
        StorageConfigDTO changedBucket = dto();
        changedBucket.setPrivateBucket("other-private");
        assertThrows(FileException.class, () -> service.update("minio", changedBucket));
        verify(catalog, never()).save(any(), eq(false));
    }

    @Test
    void referencedAndActiveConfigurationCannotBeDeleted() {
        when(catalog.referenced("minio")).thenReturn(true);
        assertThrows(FileException.class, () -> service.delete("minio"));
        when(catalog.active()).thenReturn("active-s3");
        assertThrows(FileException.class, () -> service.delete("active-s3"));
        assertThrows(FileException.class, () -> service.delete("local"));
        verify(catalog, never()).remove(anyString());
    }

    @Test
    void listNeverSerializesPlaintextOrEncryptedCredentials() throws Exception {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.list()).thenReturn(List.of());
        String json = new ObjectMapper().writeValueAsString(service.list());
        assertFalse(json.contains("access-value"));
        assertFalse(json.contains("secret-value"));
        assertFalse(json.contains("accessKey"));
        assertFalse(json.contains("encrypted"));
        assertTrue(json.contains("credentialConfigured"));
        assertTrue(json.contains("\"source\":\"config\""));
    }

    @Test
    void yamlConnectionTestStillWorksWithoutMasterKeyAndDoesNotImport() {
        properties.setConfigEncryptionKey(null);
        properties.getProfiles().put("minio", remoteProfile());
        service.test("minio");
        verify(tester).test(any());
        verify(catalog, never()).lockForWrite();
        verify(catalog, never()).save(any(), eq(false));
    }

    @Test
    void endpointValidationRejectsMetadataHttpOutsideLoopbackAndUrlExtras() {
        for (String endpoint : List.of("http://192.168.1.1:9000", "https://169.254.169.254",
                "https://100.100.100.200", "https://[fd00:ec2::254]", "https://[fd00:ec2:0:0:0:0:0:254]",
                "https://metadata.google.internal",
                "https://user:password@storage.example", "https://storage.example/path",
                "https://storage.example?secret=value", "https://storage.example#fragment",
                "https://0.0.0.0", "https://2130706433", "https://127.1")) {
            StorageProperties.Profile profile = remoteProfile();
            profile.setEndpoint(endpoint);
            assertThrows(FileException.class, () -> StorageConfigValidation.validate(profile, false), endpoint);
        }
        StorageProperties.Profile loopback = remoteProfile();
        loopback.setEndpoint("http://127.0.0.1:8333");
        assertDoesNotThrow(() -> StorageConfigValidation.validate(loopback, true));
    }

    @Test
    void credentialRotationRetiresClientOnlyAfterOldStreamCloses() throws Exception {
        StorageProperties.Profile profile = remoteProfile();
        properties.getProfiles().put("minio", profile);
        AtomicInteger closed = new AtomicInteger();
        StorageRegistry registry = new StorageRegistry(properties, root.toString()) {
            @Override
            public ObjectStorageService createClient(StorageProperties.Profile ignored) {
                return new ObjectStorageService() {
                    @Override
                    public void put(String bucket, String key, Path source, String type, String hash) {
                    }

                    @Override
                    public InputStream open(String bucket, String key) {
                        return new ByteArrayInputStream(new byte[]{1, 2, 3});
                    }

                    @Override
                    public void delete(String bucket, String key) {
                    }

                    @Override
                    public void close() {
                        closed.incrementAndGet();
                    }
                };
            }
        };
        InputStream old = registry.client("minio").open("test-private", "object");
        profile.setSecretKey("rotated-secret");
        try (InputStream newer = registry.client("minio").open("test-private", "object")) {
            assertEquals(0, closed.get());
            assertEquals(1, old.read());
            assertEquals(1, newer.read());
        }
        old.close();
        old.close();
        assertEquals(1, closed.get());
        registry.close();
        assertEquals(2, closed.get());
    }

    @Test
    void failedUploadProbeCompensatesUnknownSuccessAndSanitizesError() {
        StorageRegistry registry = mock(StorageRegistry.class);
        ObjectStorageService driver = mock(ObjectStorageService.class);
        when(registry.createClient(any())).thenReturn(driver);
        doThrow(new IllegalStateException("secret-value AWS credential signed URL"))
                .when(driver).put(anyString(), anyString(), any(), anyString(), anyString());
        StorageProperties.Profile profile = remoteProfile();
        profile.setEndpoint("http://127.0.0.1:8333");
        FileException error = assertThrows(FileException.class,
                () -> new StorageConnectionTester(registry).test(profile));
        assertEquals(400, error.getCode());
        assertFalse(error.getMessage().contains("secret-value"));
        assertEquals(null, error.getCause());
        verify(driver).delete(eq("test-public"), anyString());
        verify(driver).close();
    }

    @Test
    void oneClientCloseFailureDoesNotPreventOtherClientsRetiring() throws Exception {
        properties.getProfiles().put("first", remoteProfile());
        properties.getProfiles().put("second", remoteProfile());
        AtomicInteger created = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        StorageRegistry registry = new StorageRegistry(properties, root.toString()) {
            @Override
            public ObjectStorageService createClient(StorageProperties.Profile ignored) {
                int sequence = created.incrementAndGet();
                ObjectStorageService driver = mock(ObjectStorageService.class);
                when(driver.open(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1}));
                org.mockito.Mockito.doAnswer(invocation -> {
                    attempts.incrementAndGet();
                    if (sequence == 1) {
                        throw new IllegalStateException("remote credentials must never reach logs");
                    }
                    return null;
                }).when(driver).close();
                return driver;
            }
        };
        registry.client("first").open("test-private", "object").close();
        registry.client("second").open("test-private", "object").close();
        assertDoesNotThrow(registry::close);
        assertEquals(2, attempts.get());
    }

    private StorageProperties.Profile remoteProfile() {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setType("s3");
        profile.setEndpoint("https://storage.example");
        profile.setAccessKey("access-value");
        profile.setSecretKey("secret-value");
        profile.setPublicBucket("test-public");
        profile.setPrivateBucket("test-private");
        return profile;
    }

    private StorageConfigDTO dto() {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey("minio");
        dto.setName("MinIO");
        dto.setProvider("minio");
        dto.setEndpoint("https://storage.example");
        dto.setRegion("us-east-1");
        dto.setPublicBucket("test-public");
        dto.setPrivateBucket("test-private");
        dto.setPathStyle(true);
        dto.setAccessKey("access-value");
        dto.setSecretKey("secret-value");
        return dto;
    }

    private void authenticate(long id, String... permissions) {
        User user = new User();
        user.setId(id);
        user.setStatus(UserStatus.OK);
        LoginUser login = new LoginUser(user, List.of("ADMIN"), List.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, "", login.getAuthorities()));
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}

package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageConfigValidation;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.security.StorageCredentialCipher;
import com.ymcloud.core.storage.security.StorageMasterKeyStore;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminStorageConfigController;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import com.ymcloud.web.service.admin.impl.AdminStorageConfigServiceImpl;
import com.ymcloud.web.service.file.StorageConfigService;
import com.ymcloud.web.service.file.impl.StorageConfigServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 配置权限、加密、历史定位与连接退休回归，不启动外部服务
 */
class StorageConfigSecurityTest {

    @TempDir
    private Path root;

    private StorageProperties properties;
    private StorageCredentialCipher cipher;
    private StorageConfigService catalog;
    private StorageRegistry registry;
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
        StorageMasterKeyStore keyStore = mock(StorageMasterKeyStore.class);
        when(keyStore.load(anyBoolean())).thenThrow(new FileException("主密钥不可用"));
        catalog = mock(StorageConfigService.class);
        registry = mock(StorageRegistry.class);
        when(registry.createClient(any())).thenAnswer(invocation ->
                new com.ymcloud.core.storage.client.LocalObjectStorageService(root));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(StorageProperties.class, () -> properties);
        context.registerBean(StorageConfigService.class, () -> catalog);
        context.registerBean(StorageCredentialCipher.class, () -> cipher);
        context.registerBean(StorageMasterKeyStore.class, () -> keyStore);
        context.getBeanFactory().registerSingleton("storageRegistry", registry);
        context.register(AdminStorageConfigServiceImpl.class);
        context.refresh();
        service = context.getBean(AdminStorageConfigServiceImpl.class);
        when(catalog.active()).thenReturn("local");
        authenticate(1L, "*:*:*");
    }

    /**
     * 清理认证状态并关闭隔离的 Spring 上下文
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    /**
     * 无版本认证上下文可解密独立生成的固定样本
     * 样本使用全零测试主密钥，不包含真实凭据
     */
    @Test
    void fixedAuthenticationContextDecryptsKnownCiphertext() {
        String encrypted = "AAECAwQFBgcICQoL+65dIWmJ5o+nVSSEkXa7nkM9RtWE3g41VAbKC4dkpg==";
        assertEquals("sensitive-value", cipher.decrypt("minio", "secretKey", encrypted));
    }

    /**
     * 每次加密使用随机 nonce，认证拒绝篡改及跨配置、跨字段替换
     */
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

    /**
     * 普通管理员不能修改、测试或切换存储配置
     */
    @Test
    void ordinaryAdministratorCannotWriteTestOrActivateEvenWithExplicitPermissions() {
        authenticate(2L, "file:storage:add", "file:storage:edit", "file:storage:remove",
                "file:storage:test", "file:storage:activate");
        assertThrows(AccessDeniedException.class, () -> service.create(dto()));
        assertThrows(AccessDeniedException.class, () -> service.update("minio", dto()));
        assertThrows(AccessDeniedException.class, () -> service.delete("minio"));
        assertThrows(AccessDeniedException.class, () -> service.test("minio"));
        assertThrows(AccessDeniedException.class, () -> service.activate("minio"));
        verifyNoInteractions(catalog, registry);
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

    /**
     * 配置仅保存加密凭据，启用前必须完成连接测试
     */
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
        verify(catalog, never()).activate(anyString());
    }

    /**
     * 空凭据保留原配置，启用中的配置更新先测试后保存
     */
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
        verify(registry).createClient(any());
    }

    /**
     * Silo 配置保存加密凭据，测试当前版本后才可启用，列表保留服务商标识
     */
    @Test
    void siloConfigurationUsesExistingTestAndActivationWorkflow() {
        StorageConfigDTO request = dto();
        request.setKey("silo");
        request.setName("Silo");
        request.setProvider("silo");
        service.create(request);
        ArgumentCaptor<StorageConfig> captured = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(captured.capture(), eq(false));
        StorageConfig config = captured.getValue();
        assertEquals("silo", config.getProvider());
        assertEquals("secret-value", cipher.decrypt("silo", "secretKey", config.getEncryptedSecretKey()));
        when(catalog.find("silo")).thenReturn(config);
        when(catalog.list()).thenReturn(List.of(config));
        when(catalog.profile(config)).thenReturn(remoteProfile());
        assertThrows(FileException.class, () -> service.activate("silo"));
        service.test("silo");
        verify(registry).createClient(any());
        assertEquals(config.getRevision(), config.getTestedRevision());
        service.activate("silo");
        verify(catalog).activate("silo");
        assertEquals("silo", service.list().stream().filter(row -> "silo".equals(row.getKey()))
                .findFirst().orElseThrow().getProvider());
    }

    /**
     * YAML 中的 Silo 与历史 MinIO 配置继续独立定位，轮换旧凭据不会更改旧文件标识
     */
    @Test
    void siloAndLegacyMinioYamlProfilesKeepTheirIdentity() {
        StorageProperties.Profile silo = remoteProfile();
        silo.setProvider("silo");
        properties.getProfiles().put("silo", silo);
        properties.getProfiles().put("minio", remoteProfile());
        var listed = service.list();
        assertEquals("silo", listed.stream().filter(row -> "silo".equals(row.getKey()))
                .findFirst().orElseThrow().getProvider());
        assertEquals("minio", listed.stream().filter(row -> "minio".equals(row.getKey()))
                .findFirst().orElseThrow().getProvider());
        service.test("silo");
        ArgumentCaptor<StorageProperties.Profile> tested = ArgumentCaptor.forClass(StorageProperties.Profile.class);
        verify(registry).createClient(tested.capture());
        assertEquals(StorageDriverType.S3, tested.getValue().getDriverType());
        assertTrue(tested.getValue().isPathStyle());
        assertEquals("test-private", tested.getValue().getPrivateBucket());
    }

    /**
     * 启用中的配置测试失败不保存候选配置
     */
    @Test
    void activeUpdateFailureDoesNotPersistCandidate() {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.active()).thenReturn("minio");
        doThrow(new FileException("probe failed", 400)).when(registry).createClient(any());
        assertThrows(FileException.class, () -> service.update("minio", dto()));
        verify(catalog, never()).save(any(), eq(false));
    }

    /**
     * 已引用配置禁止修改驱动及对象位置，避免历史文件不可读
     */
    @Test
    void referencedYamlConfigurationCannotChangeLocationOrDriver() {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.referenced("minio")).thenReturn(true);
        StorageConfigDTO changedEndpoint = dto();
        changedEndpoint.setEndpoint("https://other.example");
        assertThrows(FileException.class, () -> service.update("minio", changedEndpoint));
        StorageConfigDTO changedDriver = dto();
        changedDriver.setDriverType(StorageDriverType.ALIYUN_OSS);
        assertThrows(FileException.class, () -> service.update("minio", changedDriver));
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

    /**
     * 本地存储测试不依赖凭据密钥或动态配置写锁
     */
    @Test
    void localConnectionTestDoesNotRequireAnEncryptionKeyOrDynamicConfigurationLock() {
        properties.setConfigEncryptionKey(null);
        service.test("local");
        verify(registry).createClient(any());
        verify(catalog, never()).lockForWrite();
        verify(catalog, never()).save(any(), eq(false));
    }

    /**
     * 本地主密钥不可用时，不得保存未加密的连接凭据
     */
    @Test
    void unavailableMasterKeyCannotPersistYamlCredentials() {
        properties.setConfigEncryptionKey(null);
        properties.getProfiles().put("minio", remoteProfile());
        assertThrows(FileException.class, () -> service.test("minio"));
        verify(registry).createClient(any());
        verify(catalog).lockForWrite();
        verify(catalog, never()).save(any(), eq(false));
    }

    /**
     * 地址校验拒绝元数据端点、非回环明文连接及多余 URL 成分
     */
    @Test
    void endpointValidationRejectsMetadataHttpOutsideLoopbackAndUrlExtras() {
        for (String endpoint : List.of("http://192.168.1.1:9000", "https://169.254.169.254",
                "https://100.100.100.200", "https://[fd00:ec2::254]", "https://[fd00:ec2:0:0:0:0:0:254]",
                "https://metadata.google.internal",
                "https://user:password@storage.example", "http://127.0.0.1:8333/path",
                "http://127.0.0.1:8333?secret=value", "http://127.0.0.1:8333#fragment",
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
                    public void put(String bucket, String key, Path source, String type, String hash) {}

                    @Override
                    public InputStream open(String bucket, String key) {
                        return new ByteArrayInputStream(new byte[]{1, 2, 3});
                    }

                    @Override
                    public void delete(String bucket, String key) {}

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

    /**
     * 验证探测上传失败仍补偿可能已创建的对象，并隐藏远端凭据异常
     */
    @Test
    void failedUploadProbeCompensatesUnknownSuccessAndSanitizesError() {
        StorageRegistry registry = mock(StorageRegistry.class);
        ObjectStorageService driver = mock(ObjectStorageService.class);
        when(registry.createClient(any())).thenReturn(driver);
        IllegalStateException failure = new IllegalStateException("secret-value AWS credential signed URL");
        doThrow(failure)
                .when(driver).put(anyString(), anyString(), any(), anyString(), anyString());
        StorageProperties.Profile profile = remoteProfile();
        profile.setEndpoint("http://127.0.0.1:8333");
        ReflectionTestUtils.setField(service, "registry", registry);
        FileException error = assertThrows(FileException.class, () -> ReflectionTestUtils.invokeMethod(service, "testProfile", profile));
        assertEquals(400, error.getCode());
        assertFalse(error.getMessage().contains("secret-value"));
        assertSame(failure, error.getCause());
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

    /**
     * 服务商名称仅作描述，同名服务商也可以显式选择不同协议
     */
    @Test
    void providerDoesNotChooseDriver() {
        StorageConfigDTO request = dto();
        request.setProvider("aliyun");
        request.setDriverType(StorageDriverType.S3);
        service.create(request);
        ArgumentCaptor<StorageConfig> saved = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(saved.capture(), eq(false));
        assertEquals(StorageDriverType.S3, saved.getValue().getDriverType());

        var source = new StorageConfigServiceImpl();
        ReflectionTestUtils.setField(source, "cipher", cipher);
        assertEquals(StorageDriverType.S3, source.profile(saved.getValue()).getDriverType());
        saved.getValue().setProvider("自建对象服务");
        saved.getValue().setDriverType(StorageDriverType.ALIYUN_OSS);
        assertEquals(StorageDriverType.ALIYUN_OSS, source.profile(saved.getValue()).getDriverType());
    }

    /**
     * 未提供驱动的请求和静态配置均失败，不因服务商存在而选择默认驱动
     */
    @Test
    void missingDriverDoesNotFallBack() {
        StorageConfigDTO request = dto();
        request.setDriverType(null);
        assertThrows(FileException.class, () -> service.create(request));
        StorageProperties.Profile profile = remoteProfile();
        profile.setDriverType(null);
        assertThrows(FileException.class, () -> StorageConfigValidation.validate(profile, false));
        verify(catalog, never()).save(any(), anyBoolean());
    }

    /**
     * 接口只接受明确驱动编码，未知名称和旧模糊标识不能进入服务层
     * @throws Exception JSON 处理失败
     */
    @Test
    void driverJsonContractRejectsUnknownNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("\"ALIYUN_OSS\"", mapper.writeValueAsString(StorageDriverType.ALIYUN_OSS));
        assertEquals(StorageDriverType.S3, mapper.readValue("\"S3\"", StorageDriverType.class));
        for (String invalid : List.of("oss", "s3", "aliyun", "unknown")) {
            assertThrows(InvalidFormatException.class,
                         () -> mapper.readValue("\"" + invalid + "\"", StorageDriverType.class));
        }
    }

    /**
     * 已引用配置可以修改服务商描述，不改变实际存储位置
     */
    @Test
    void referencedConfigurationCanChangeProviderDescription() {
        properties.getProfiles().put("minio", remoteProfile());
        when(catalog.referenced("minio")).thenReturn(true);
        StorageConfigDTO request = dto();
        request.setProvider("自建 S3 服务");
        service.update("minio", request);
        ArgumentCaptor<StorageConfig> saved = ArgumentCaptor.forClass(StorageConfig.class);
        verify(catalog).save(saved.capture(), eq(false));
        assertEquals("自建 S3 服务", saved.getValue().getProvider());
        assertEquals(StorageDriverType.S3, saved.getValue().getDriverType());
    }

    /**
     * 创建带显式协议和测试凭据的远端配置
     * @return 不连接真实网络的客户端配置
     */
    private StorageProperties.Profile remoteProfile() {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setDriverType(StorageDriverType.S3);
        profile.setProvider("minio");
        profile.setEndpoint("http://127.0.0.1:8333");
        profile.setAccessKey("access-value");
        profile.setSecretKey("secret-value");
        profile.setPublicBucket("test-public");
        profile.setPrivateBucket("test-private");
        return profile;
    }

    /**
     * 创建使用 S3 协议的合法保存请求
     * @return 含测试凭据的配置请求
     */
    private StorageConfigDTO dto() {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey("minio");
        dto.setName("MinIO");
        dto.setProvider("minio");
        dto.setDriverType(StorageDriverType.S3);
        dto.setEndpoint("http://127.0.0.1:8333");
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
    static class MethodSecurityConfig {}
}

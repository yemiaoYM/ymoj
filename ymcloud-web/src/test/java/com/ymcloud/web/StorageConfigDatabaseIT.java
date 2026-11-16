package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.security.StorageCredentialCipher;
import com.ymcloud.core.storage.security.StorageMasterKeyStore;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.dao.impl.FileEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.StorageConfigEntityServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import com.ymcloud.web.service.admin.impl.AdminStorageConfigServiceImpl;
import com.ymcloud.web.service.file.StorageConfigService;
import com.ymcloud.web.service.file.impl.StorageConfigServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 真实数据库事务与多实例配置回归，网络驱动替身用于观察连接退休
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_STORAGE_DB_IT", matches = "true")
class StorageConfigDatabaseIT {

    @TempDir
    static Path directory;

    private static String schema;
    private static boolean schemaCreated;
    private static DriverManagerDataSource administration;
    private static DruidDataSource datasource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static AdminStorageConfigService service;
    private static StorageConfigService catalog;
    private static TrackingRegistry registry;

    /**
     * 在本机创建独立临时库并装配真实存储配置服务
     *
     * @throws Exception 配置读取、建库或服务装配失败
     */
    @BeforeAll
    static void createIsolatedDatabase() throws Exception {
        schema = "ymcloud_config_it_" + UUID.randomUUID().toString().replace("-", "");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sql/ymcloud.sql"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("无法定位项目开发配置");
        }
        datasource = new DruidDataSource();
        administration = new DriverManagerDataSource();
        try (InputStream input = Files.newInputStream(root.resolve("ymcloud-web/src/main/resources/application-dev.yml"))) {
            Map<?, ?> configuration = new Yaml().load(input);
            Map<?, ?> spring = (Map<?, ?>) configuration.get("spring");
            Map<?, ?> database = (Map<?, ?>) spring.get("datasource");
            String url = String.valueOf(database.get("url"));
            if (!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:\\d+)?/[^?]+(\\?.*)?")) {
                throw new IllegalArgumentException("集成测试只允许使用本机 MySQL");
            }
            administration.setUrl(url);
            administration.setUsername(String.valueOf(database.get("username")));
            administration.setPassword(String.valueOf(database.get("password")));
            URI endpoint = URI.create(url.substring(5));
            datasource.setUrl("jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
                    + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery()));
            datasource.setUsername(String.valueOf(database.get("username")));
            datasource.setPassword(String.valueOf(database.get("password")));
        }
        datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        datasource.setInitialSize(0);
        datasource.setMaxActive(4);
        datasource.setMinIdle(0);
        datasource.setMaxWait(10000);
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("openTelemetry", "DISABLED");
        administration.setConnectionProperties(connectionProperties);
        datasource.setConnectProperties(connectionProperties);
        new JdbcTemplate(administration).execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        schemaCreated = true;
        jdbc = new JdbcTemplate(datasource);
        assertEquals(schema, jdbc.queryForObject("SELECT DATABASE()", String.class));
        try (var connection = datasource.getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.support.EncodedResource(
                            new org.springframework.core.io.ByteArrayResource(
                                    InitializationSqlTestSupport.storageSchema().getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
        }
        jdbc.update("INSERT INTO storage_config (id,name,driver_type,public_bucket,private_bucket,is_default,created_time,updated_time) "
                + "VALUES ('local','本地文件','LOCAL','public','private',1,NOW(),NOW())");
        clearTableInfo();
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
        assertIsolatedMappings();
        service = context.getBean(AdminStorageConfigService.class);
        catalog = context.getBean(StorageConfigService.class);
        registry = context.getBean(TrackingRegistry.class);
    }

    /**
     * 重置独立测试库中的文件及配置，保留内置本地配置
     */
    @BeforeEach
    void resetOnlyOwnRows() {
        assertIsolatedMappings();
        jdbc.update("DELETE FROM `file`");
        jdbc.update("UPDATE storage_config SET is_default=0 WHERE is_default=1");
        jdbc.update("DELETE FROM storage_config WHERE id != 'local'");
        jdbc.update("UPDATE storage_config SET is_default=1 WHERE id='local'");
        authenticate();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 关闭连接并仅删除本次成功创建的临时数据库
     */
    @AfterAll
    static void removeIsolatedDatabase() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (datasource != null) {
                datasource.close();
            }
            if (schemaCreated) {
                if (!schema.matches("ymcloud_config_it_[0-9a-f]{32}")) {
                    throw new IllegalStateException("禁止删除非本次测试数据库");
                }
                new JdbcTemplate(administration).execute("DROP DATABASE `" + schema + "`");
            }
            clearTableInfo();
        }
    }

    @Test
    void newInstallationCreatesTestsAndEnablesExternalStorageWithoutRestartOrInstallScripts() throws Exception {
        StorageProperties properties = context.getBean(StorageProperties.class);
        assertEquals(null, properties.getConfigEncryptionKey());
        service.create(dto("external-seaweed", "http://127.0.0.1:8333"));
        service.test("external-seaweed");
        service.activate("external-seaweed");
        assertEquals("external-seaweed", registry.active());
        assertTrue(Files.isRegularFile(directory.resolve(".secrets/storage-config.key")));
        try (InputStream input = registry.client("external-seaweed").open("test-private", "object")) {
            assertEquals("http://127.0.0.1:8333", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        StorageCredentialCipher anotherProcess = new StorageCredentialCipher(new StorageProperties());
        ReflectionTestUtils.setField(anotherProcess, "keyStore", new StorageMasterKeyStore(directory.toString(),
                context.getBean(StorageConfigEntityService.class)));
        assertEquals("test-secret", anotherProcess.decrypt("external-seaweed", "secretKey",
                catalog.find("external-seaweed").getEncryptedSecretKey()));
    }

    /**
     * 密钥丢失后须人工切换本地并清除无引用配置，才能重新初始化
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void lostMasterKeyAllowsLocalFallbackAndUnusedConfigurationCleanupBeforeFreshSetup() throws Exception {
        service.create(dto("old-seaweed", "http://127.0.0.1:8333"));
        service.create(dto("old-minio", "http://127.0.0.1:8334"));
        service.test("old-seaweed");
        service.test("old-minio");
        service.activate("old-seaweed");
        Path masterKey = directory.resolve(".secrets/storage-config.key");
        byte[] original = Files.readAllBytes(masterKey);
        Files.delete(masterKey);

        assertThrows(FileException.class, () -> service.create(dto("fresh-seaweed", "http://127.0.0.1:8335")));
        assertThrows(FileException.class, () -> service.activate("old-minio"));
        assertThrows(FileException.class, () -> service.delete("old-seaweed"));
        assertEquals("old-seaweed", catalog.active());
        assertThrows(FileException.class, registry::active);
        assertFalse(Files.exists(masterKey));

        service.activate("local");
        assertEquals("local", registry.active());
        service.delete("old-seaweed");
        assertThrows(FileException.class, () -> service.create(dto("fresh-seaweed", "http://127.0.0.1:8335")));
        assertFalse(Files.exists(masterKey));
        service.delete("old-minio");
        assertEquals(List.of("local"), catalog.list().stream().map(StorageConfig::getId).toList());

        service.create(dto("fresh-seaweed", "http://127.0.0.1:8335"));
        service.test("fresh-seaweed");
        service.activate("fresh-seaweed");
        assertFalse(Arrays.equals(original, Files.readAllBytes(masterKey)));
        assertEquals("fresh-seaweed", registry.active());
        assertEquals("test-secret", catalog.profile(catalog.find("fresh-seaweed")).getSecretKey());
    }

    /**
     * 密钥丢失不绕过文件引用保护，恢复原密钥后可继续访问
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void lostMasterKeyDoesNotBypassFileReferenceProtectionAndRestoredKeyRemainsUsable() throws Exception {
        service.create(dto("referenced-seaweed", "http://127.0.0.1:8333"));
        service.test("referenced-seaweed");
        jdbc.update("INSERT INTO file (id,public_id,file_name,storage_key,bucket,object_key) VALUES(1,'00000000000040008000000000000001','test.txt',?,'test-private','test.txt')", "referenced-seaweed");
        Path masterKey = directory.resolve(".secrets/storage-config.key");
        byte[] original = Files.readAllBytes(masterKey);
        Files.delete(masterKey);
        try {
            service.activate("local");
            FileException error = assertThrows(FileException.class, () -> service.delete("referenced-seaweed"));
            assertTrue(error.getMessage().contains("已被文件引用"));
            assertThrows(FileException.class, () -> service.test("referenced-seaweed"));
            assertThrows(FileException.class, () -> service.activate("referenced-seaweed"));
            assertThrows(FileException.class, () -> service.create(dto("new-seaweed", "http://127.0.0.1:8334")));
            assertFalse(Files.exists(masterKey));
            assertEquals(2, catalog.list().size());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `file`", Integer.class));
        } finally {
            Files.write(masterKey, original);
        }
        assertEquals("test-secret", catalog.profile(catalog.find("referenced-seaweed")).getSecretKey());
        service.activate("referenced-seaweed");
        assertEquals("referenced-seaweed", registry.active());
    }

    /**
     * 多实例读取已提交配置，并发切换只保留一个默认值
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void multipleRegistriesReadCommittedSwitchesAndConcurrentActivationsDoNotLoseRevision() throws Exception {
        service.create(dto("profile-a", "http://127.0.0.1:8333"));
        service.create(dto("profile-b", "http://127.0.0.1:8334"));
        service.test("profile-a");
        service.test("profile-b");
        service.activate("profile-a");
        StorageRegistry second = new StorageRegistry(context.getBean(StorageProperties.class), directory.toString());
        second.setCatalog(catalog);
        try {
            assertEquals("profile-a", second.active());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> first = executor.submit(() -> activateTogether(start, "profile-a"));
                Future<?> other = executor.submit(() -> activateTogether(start, "profile-b"));
                start.countDown();
                first.get(20, TimeUnit.SECONDS);
                other.get(20, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM storage_config WHERE is_default=1", Long.class));
            assertEquals(registry.active(), second.active());
        } finally {
            second.close();
        }
    }

    /**
     * 被引用配置禁止修改对象位置，空凭据编辑保留原密文
     */
    @Test
    void referencedConfigurationRejectsLocatorChangesButBlankCredentialEditPreservesEncryptedValues() {
        String key = "referenced-profile";
        service.create(dto(key, "http://127.0.0.1:8333"));
        jdbc.update("INSERT INTO file (id,public_id,file_name,storage_key,bucket,object_key) VALUES(1,'00000000000040008000000000000001','test.txt',?,'test-private','test.txt')", key);
        StorageConfigDTO changed = dto(key, "http://127.0.0.1:8334");
        assertThrows(FileException.class, () -> service.update(key, changed));
        assertEquals("http://127.0.0.1:8333", catalog.find(key).getEndpoint());
        StorageConfigDTO preserving = dto(key, "http://127.0.0.1:8333");
        preserving.setAccessKey("");
        preserving.setSecretKey(" ");
        service.update(key, preserving);
        StorageConfig config = catalog.find(key);
        assertEquals(2L, config.getRevision());
        assertEquals("test-access", catalog.profile(config).getAccessKey());
        assertEquals("test-secret", catalog.profile(config).getSecretKey());
        assertNotEquals("test-secret", config.getEncryptedSecretKey());
        assertThrows(FileException.class, () -> service.delete(key));
        assertTrue(service.list().stream().filter(view -> key.equals(view.getKey())).findFirst().orElseThrow().getReferenced());
    }

    /**
     * 编辑未启用配置使测试结果失效，重新测试后才可启用
     */
    @Test
    void editingInactiveConfigurationInvalidatesTestRevisionAndActivationRequiresRetest() {
        String key = "test-revision";
        StorageConfigDTO dto = dto(key, "http://127.0.0.1:8333");
        service.create(dto);
        service.test(key);
        assertEquals(1L, catalog.find(key).getTestedRevision());
        dto.setName("编辑后的配置");
        service.update(key, dto);
        assertEquals(2L, catalog.find(key).getRevision());
        assertEquals(null, catalog.find(key).getTestedRevision());
        assertThrows(FileException.class, () -> service.activate(key));
        assertEquals("local", registry.active());
        service.test(key);
        assertDoesNotThrow(() -> service.activate(key));
        assertEquals(key, registry.active());
        assertEquals("database", service.list().stream().filter(view -> key.equals(view.getKey()))
                .findFirst().orElseThrow().getSource());
    }

    /**
     * 对外标识唯一且配置外键禁止悬空引用，约束由真实数据库执行
     */
    @Test
    void publicIdentityAndStorageReferenceAreConstrained() {
        String insert = "INSERT INTO file (public_id,file_name,storage_key,bucket,object_key) VALUES (?, 'image.png', ?, 'public', ?)";
        String publicId = "00000000000040008000000000000001";
        jdbc.update(insert, publicId, "local", "image/first.png");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                     () -> jdbc.update(insert, publicId, "local", "image/second.png"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                     () -> jdbc.update(insert, "00000000000040008000000000000002", "missing", "image/third.png"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                     () -> jdbc.update("DELETE FROM storage_config WHERE id='local'"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
    }

    @Test
    void deletingAndRecreatingSameKeyAndRevisionUsesNewClientWhileOldStreamFinishes() throws Exception {
        String key = "recreate-profile";
        service.create(dto(key, "http://127.0.0.1:8333"));
        int closedBefore = registry.closed.get();
        try (InputStream old = registry.client(key).open("test-private", "object")) {
            service.delete(key);
            service.create(dto(key, "http://127.0.0.1:8334"));
            assertEquals(1L, catalog.find(key).getRevision());
            try (InputStream newer = registry.client(key).open("test-private", "object")) {
                assertEquals(closedBefore, registry.closed.get());
                assertEquals("http://127.0.0.1:8334", new String(newer.readAllBytes(), StandardCharsets.UTF_8));
                assertEquals("http://127.0.0.1:8333", new String(old.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(closedBefore + 1, registry.closed.get());
        assertFalse(catalog.referenced(key));
    }

    /**
     * 并发上传共享配置读锁，配置切换等待全部上传事务完成
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void concurrentUploadLeasesShareLocksAndConfigurationWriterWaitsUntilBothFinish() throws Exception {
        String key = "shared-lock-profile";
        Files.writeString(directory.resolve("fake-input"), "上传事务共享锁测试", StandardCharsets.UTF_8);
        service.create(dto(key, "http://127.0.0.1:8333"));
        service.test(key);
        CountDownLatch readersReady = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> first = executor.submit(() -> holdUploadLease(key, readersReady, releaseFirst));
            Future<?> second = executor.submit(() -> holdUploadLease(key, readersReady, releaseSecond));
            assertTrue(readersReady.await(10, TimeUnit.SECONDS), "两个上传应能同时取得配置共享锁");
            Future<?> writer = executor.submit(() -> {
                authenticate();
                try {
                    writerStarted.countDown();
                    service.activate(key);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(writerStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> writer.get(200, TimeUnit.MILLISECONDS), "读者仍持锁时配置切换须等待");
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> writer.get(200, TimeUnit.MILLISECONDS), "第二个读者完成前切换仍须等待");
            releaseSecond.countDown();
            second.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            assertEquals(key, registry.active());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            executor.shutdownNow();
        }
    }

    private static void holdUploadLease(String key, CountDownLatch ready, CountDownLatch release) {
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            try (ObjectStorageService client = registry.lease(key)) {
                client.put("test-private", "isolated-upload", directory.resolve("fake-input"), "text/plain", "test-hash");
                ready.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static void activateTogether(CountDownLatch start, String key) {
        authenticate();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发激活启动超时");
            }
            service.activate(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 确认实体映射使用独立数据库中的正式表名
     */
    private static void assertIsolatedMappings() {
        assertEquals("file", TableInfoHelper.getTableInfo(File.class).getTableName());
        assertEquals("storage_config", TableInfoHelper.getTableInfo(StorageConfig.class).getTableName());
    }

    private static void clearTableInfo() {
        TableInfoHelper.remove(File.class);
        TableInfoHelper.remove(StorageConfig.class);
    }

    private static StorageConfigDTO dto(String key, String endpoint) {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey(key);
        dto.setName("数据库配置测试");
        dto.setProvider("s3");
        dto.setDriverType(StorageDriverType.S3);
        dto.setEndpoint(endpoint);
        dto.setRegion("us-east-1");
        dto.setPublicBucket("test-public");
        dto.setPrivateBucket("test-private");
        dto.setPathStyle(true);
        dto.setAccessKey("test-access");
        dto.setSecretKey("test-secret");
        return dto;
    }

    /**
     * 在当前线程设置隔离的超级管理员认证
     */
    private static void authenticate() {
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.OK);
        LoginUser login = new LoginUser(user, List.of("ADMIN"), List.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, "", login.getAuthorities()));
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = FileMapper.class)
    @Import({FileEntityServiceImpl.class, StorageConfigEntityServiceImpl.class,
            StorageConfigServiceImpl.class, StorageCredentialCipher.class, AdminStorageConfigServiceImpl.class})
    static class DatabaseConfiguration {

        @Bean(destroyMethod = "")
        DataSource dataSource() {
            return datasource;
        }

        /**
         * 装配正式 Mapper，使用独立数据库验证真实查询与行锁
         * @param dataSource 独立临时数据库
         * @return 同时支持 MP 和 XML 查询的会话工厂
         * @throws Exception Mapper 资源加载失败
         */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new ClassPathResource("mapper/StorageConfigMapper.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        @Primary
        StorageProperties storageProperties() {
            return new StorageProperties();
        }

        @Bean
        StorageMasterKeyStore storageMasterKeyStore(StorageConfigEntityService configs) {
            return new StorageMasterKeyStore(directory.toString(), configs);
        }

        @Bean
        TrackingRegistry storageRegistry(StorageProperties properties) {
            return new TrackingRegistry(properties);
        }

    }

    static class TrackingRegistry extends StorageRegistry {

        final AtomicInteger closed = new AtomicInteger();

        TrackingRegistry(StorageProperties properties) {
            super(properties, directory.toString());
        }

        /**
         * 创建可跟踪关闭次数的内存对象客户端
         * @param profile 本轮连接配置
         * @return 记录探测对象的客户端
         */
        @Override
        public ObjectStorageService createClient(StorageProperties.Profile profile) {
            String endpoint = profile.getEndpoint();
            Map<String, byte[]> objects = new HashMap<>();
            return new ObjectStorageService() {
                /**
                 * 保存探测对象的字节，供连接测试回读
                 * @param bucket 存储桶
                 * @param key 对象键
                 * @param source 探测源文件
                 * @param contentType 内容类型
                 * @param sha256 预期内容摘要
                 */
                @Override
                public void put(String bucket, String key, Path source, String contentType, String sha256) {
                    try {
                        objects.put(bucket + "/" + key, Files.readAllBytes(source));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                /**
                 * 读取探测字节，固定对象用于验证客户端所属配置
                 * @param bucket 存储桶
                 * @param key 对象键
                 * @return 对象内容流
                 */
                @Override
                public InputStream open(String bucket, String key) {
                    return new ByteArrayInputStream(key.equals("object")
                            ? endpoint.getBytes(StandardCharsets.UTF_8) : objects.get(bucket + "/" + key));
                }

                /**
                 * 删除连接测试创建的内存对象
                 * @param bucket 存储桶
                 * @param key 对象键
                 */
                @Override
                public void delete(String bucket, String key) {
                    objects.remove(bucket + "/" + key);
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }
    }
}

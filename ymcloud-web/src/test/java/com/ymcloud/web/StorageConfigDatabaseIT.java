package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.storage.ObjectStorageService;
import com.ymcloud.core.storage.StorageConfigCatalog;
import com.ymcloud.core.storage.StorageConnectionTester;
import com.ymcloud.core.storage.StorageCredentialCipher;
import com.ymcloud.core.storage.StorageMasterKeyStore;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.dao.impl.FileEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.StorageConfigEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.StorageConfigStateEntityServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import com.ymcloud.web.service.admin.impl.AdminStorageConfigServiceImpl;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 真实数据库事务与多实例配置回归，网络驱动替身用于观察连接退休
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_STORAGE_DB_IT", matches = "true")
class StorageConfigDatabaseIT {

    @TempDir
    static Path directory;

    private static final Set<String> TABLES = Set.of("file", "storage_config", "storage_config_state");
    private static String prefix;
    private static DruidDataSource datasource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static AdminStorageConfigService service;
    private static StorageConfigCatalog catalog;
    private static TrackingRegistry registry;

    @BeforeAll
    static void createOnlyIsolatedTables() throws Exception {
        prefix = "ymcloud_config_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "_";
        datasource = new DruidDataSource();
        try (InputStream input = new ClassPathResource("application-dev.yml").getInputStream()) {
            Map<?, ?> configuration = new Yaml().load(input);
            Map<?, ?> spring = (Map<?, ?>) configuration.get("spring");
            Map<?, ?> database = (Map<?, ?>) spring.get("datasource");
            String url = String.valueOf(database.get("url"));
            if (!url.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException("仅允许复用项目明确配置的 MySQL 服务");
            }
            datasource.setUrl(url);
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
        datasource.setConnectProperties(connectionProperties);
        jdbc = new JdbcTemplate(datasource);
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
        jdbc.execute("CREATE TABLE `" + table("file") + "` (id BIGINT NOT NULL PRIMARY KEY,"
                + "file_name VARCHAR(255) NULL,file_type VARCHAR(255) NULL,file_size BIGINT NULL,file_ext VARCHAR(20) NULL,"
                + "storage_path VARCHAR(1000) NULL,storage_type INT NULL,storage_profile VARCHAR(64) NULL,"
                + "bucket VARCHAR(128) NULL,object_key VARCHAR(500) NULL,sha256 CHAR(64) NULL,legacy_path VARCHAR(1000) NULL,"
                + "url VARCHAR(1000) NULL,uploader_id BIGINT NULL,uploader_username VARCHAR(100) NULL,"
                + "biz_type INT NULL,biz_id BIGINT NULL,is_public TINYINT NULL,created_time DATETIME NULL,updated_time DATETIME NULL,"
                + "INDEX idx_profile(storage_profile)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE `" + table("storage_config") + "` ("
                + "id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,"
                + "name VARCHAR(100) NOT NULL,provider VARCHAR(16) NOT NULL,endpoint VARCHAR(512) NOT NULL,"
                + "region VARCHAR(64) NOT NULL,public_bucket VARCHAR(128) NOT NULL,private_bucket VARCHAR(128) NOT NULL,"
                + "path_style TINYINT NOT NULL DEFAULT 1,encrypted_access_key TEXT NOT NULL,encrypted_secret_key TEXT NOT NULL,"
                + "remark VARCHAR(500) NULL,revision BIGINT NOT NULL DEFAULT 1,tested_revision BIGINT NULL,tested_at DATETIME NULL,"
                + "created_time DATETIME NOT NULL,updated_time DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE `" + table("storage_config_state") + "` ("
                + "id TINYINT NOT NULL PRIMARY KEY,active_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,"
                + "revision BIGINT NOT NULL DEFAULT 0,updated_time DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.update("INSERT INTO `" + table("storage_config_state") + "` VALUES(1,NULL,0,NOW())");
        clearTableInfo();
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
        assertIsolatedMappings();
        service = context.getBean(AdminStorageConfigService.class);
        catalog = context.getBean(StorageConfigCatalog.class);
        registry = context.getBean(TrackingRegistry.class);
    }

    @BeforeEach
    void resetOnlyOwnRows() {
        assertIsolatedMappings();
        jdbc.update("DELETE FROM `" + table("file") + "`");
        jdbc.update("DELETE FROM `" + table("storage_config") + "`");
        jdbc.update("UPDATE `" + table("storage_config_state") + "` SET active_key=NULL,revision=0 WHERE id=1");
        authenticate();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void removeOnlyVerifiedOwnedTables() {
        try {
            if (context != null) {
                context.close();
            }
            if (jdbc != null && prefix != null) {
                for (String name : TABLES) {
                    jdbc.execute("DROP TABLE IF EXISTS `" + table(name) + "`");
                }
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema=DATABASE() AND table_name IN (?,?,?)", Integer.class,
                        table("file"), table("storage_config"), table("storage_config_state")));
            }
        } finally {
            if (datasource != null) {
                datasource.close();
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
        anotherProcess.setKeyStore(new StorageMasterKeyStore(directory.toString(),
                context.getBean(StorageConfigEntityService.class)));
        assertEquals("test-secret", anotherProcess.decrypt("external-seaweed", "secretKey",
                catalog.find("external-seaweed").getEncryptedSecretKey()));
    }

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
        assertTrue(catalog.list().isEmpty());

        service.create(dto("fresh-seaweed", "http://127.0.0.1:8335"));
        service.test("fresh-seaweed");
        service.activate("fresh-seaweed");
        assertFalse(Arrays.equals(original, Files.readAllBytes(masterKey)));
        assertEquals("fresh-seaweed", registry.active());
        assertEquals("test-secret", catalog.profile(catalog.find("fresh-seaweed")).getSecretKey());
    }

    @Test
    void lostMasterKeyDoesNotBypassFileReferenceProtectionAndRestoredKeyRemainsUsable() throws Exception {
        service.create(dto("referenced-seaweed", "http://127.0.0.1:8333"));
        service.test("referenced-seaweed");
        jdbc.update("INSERT INTO `" + table("file") + "` (id,storage_profile) VALUES(1,?)", "referenced-seaweed");
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
            assertEquals(1, catalog.list().size());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table("file") + "`", Integer.class));
        } finally {
            Files.write(masterKey, original);
        }
        assertEquals("test-secret", catalog.profile(catalog.find("referenced-seaweed")).getSecretKey());
        service.activate("referenced-seaweed");
        assertEquals("referenced-seaweed", registry.active());
    }

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
            assertEquals(3L, jdbc.queryForObject("SELECT revision FROM `" + table("storage_config_state")
                    + "` WHERE id=1", Long.class));
            assertEquals(registry.active(), second.active());
        } finally {
            second.close();
        }
    }

    @Test
    void referencedConfigurationRejectsLocatorChangesButBlankCredentialEditPreservesEncryptedValues() {
        String key = "referenced-profile";
        service.create(dto(key, "http://127.0.0.1:8333"));
        jdbc.update("INSERT INTO `" + table("file") + "` (id,storage_profile) VALUES(1,?)", key);
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

    @Test
    void concurrentUploadLeasesShareLocksAndConfigurationWriterWaitsUntilBothFinish() throws Exception {
        String key = "shared-lock-profile";
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

    private static String table(String name) {
        if (prefix == null || !prefix.matches("ymcloud_config_it_[0-9a-f]{16}_") || !TABLES.contains(name)) {
            throw new IllegalArgumentException("临时表标识不合法，禁止操作");
        }
        String identifier = prefix + name;
        if (identifier.length() > 64 || !identifier.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("临时表超出标识边界");
        }
        return identifier;
    }

    private static void assertIsolatedMappings() {
        assertEquals(table("file"), TableInfoHelper.getTableInfo(File.class).getTableName());
        assertEquals(table("storage_config"), TableInfoHelper.getTableInfo(StorageConfig.class).getTableName());
        assertEquals(table("storage_config_state"), TableInfoHelper.getTableInfo(StorageConfigState.class).getTableName());
    }

    private static void clearTableInfo() {
        TableInfoHelper.remove(File.class);
        TableInfoHelper.remove(StorageConfig.class);
        TableInfoHelper.remove(StorageConfigState.class);
    }

    private static StorageConfigDTO dto(String key, String endpoint) {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey(key);
        dto.setName("数据库配置测试");
        dto.setProvider("s3");
        dto.setEndpoint(endpoint);
        dto.setRegion("us-east-1");
        dto.setPublicBucket("test-public");
        dto.setPrivateBucket("test-private");
        dto.setPathStyle(true);
        dto.setAccessKey("test-access");
        dto.setSecretKey("test-secret");
        return dto;
    }

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
    @Import({FileEntityServiceImpl.class, StorageConfigEntityServiceImpl.class, StorageConfigStateEntityServiceImpl.class,
            StorageConfigCatalog.class, StorageCredentialCipher.class, AdminStorageConfigServiceImpl.class})
    static class DatabaseConfiguration {

        @Bean(destroyMethod = "")
        DataSource dataSource() {
            return datasource;
        }

        /**
         * 装配真实查询与锁语句，所有表名映射到本次随机测试表
         * @param dataSource 隔离表所在的数据源
         * @return 同时支持 MP 和 XML 查询的会话工厂
         * @throws Exception Mapper 资源加载失败
         */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig database = new GlobalConfig.DbConfig();
            database.setTableUnderline(true);
            database.setTablePrefix(prefix);
            globalConfig.setDbConfig(database);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(globalConfig);
            List<Resource> mapperResources = new ArrayList<>();
            for (String mapper : List.of("StorageConfigMapper", "StorageConfigStateMapper")) {
                String path = "mapper/" + mapper + ".xml";
                String xml;
                try (InputStream input = new ClassPathResource(path).getInputStream()) {
                    xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (String name : TABLES) {
                    xml = xml.replaceAll("\\b" + name + "\\b", table(name));
                }
                mapperResources.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8), path));
            }
            factory.setMapperLocations(mapperResources.toArray(Resource[]::new));
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

        @Bean
        StorageConnectionTester storageConnectionTester() {
            return mock(StorageConnectionTester.class);
        }
    }

    static class TrackingRegistry extends StorageRegistry {

        final AtomicInteger closed = new AtomicInteger();

        TrackingRegistry(StorageProperties properties) {
            super(properties, directory.toString());
        }

        @Override
        public ObjectStorageService createClient(StorageProperties.Profile profile) {
            String endpoint = profile.getEndpoint();
            return new ObjectStorageService() {
                @Override
                public void put(String bucket, String key, Path source, String contentType, String sha256) {
                }

                @Override
                public InputStream open(String bucket, String key) {
                    return new ByteArrayInputStream(endpoint.getBytes(StandardCharsets.UTF_8));
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
    }
}

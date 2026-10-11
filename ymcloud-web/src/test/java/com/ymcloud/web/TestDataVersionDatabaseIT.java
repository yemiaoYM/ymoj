package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.storage.TestDataServiceImpl;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.dao.impl.FileEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ProblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.TestDataVersionEntityServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.enums.FileBizType;
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
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import javax.sql.DataSource;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 显式启用的真实事务测试，复用项目现有数据库服务，在唯一临时库或前缀测试表内执行。
 * 配置凭据只在内存读取；绝不修改项目原数据库的业务表。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_STORAGE_DB_IT", matches = "true")
class TestDataVersionDatabaseIT {

    @TempDir
    static Path directory;

    static AnnotationConfigApplicationContext context;
    static TestDataService testData;
    static ProblemEntityService problems;
    static FileEntityService files;
    static TestDataVersionEntityService versions;
    static StorageRegistry storage;
    static StoredFileService fileStorage;
    static JdbcTemplate jdbc;
    static DriverManagerDataSource adminDataSource;
    static DruidDataSource preparedDataSource;
    static DatabaseCredentials credentials;
    static String temporarySchema;
    static boolean temporarySchemaCreated;
    static String tablePrefix;
    static String sourceCatalog;
    static boolean disposableTablesAttempted;
    Long problemId;

    @BeforeAll
    static void openDatabase() throws Exception {
        credentials = readCredentials();
        adminDataSource = dataSource(credentials.url(), credentials);
        JdbcTemplate administration = new JdbcTemplate(adminDataSource);
        assertEquals(1, administration.queryForObject("SELECT 1", Integer.class));
        sourceCatalog = administration.queryForObject("SELECT DATABASE()", String.class);
        temporarySchema = "ymcloud_storage_test_" + UUID.randomUUID().toString().replace("-", "");
        verifyTemporarySchema();
        try {
            administration.execute("CREATE DATABASE `" + temporarySchema + "` CHARACTER SET utf8mb4");
            temporarySchemaCreated = true;
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null && !(cause instanceof SQLException)) {
                cause = cause.getCause();
            }
            if (cause instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 1044 || sqlException.getErrorCode() == 1045
                    || sqlException.getErrorCode() == 1142 || sqlException.getErrorCode() == 1227)) {
                String reason = "原数据库只读连接成功，创建临时库权限不足，改用唯一前缀测试表：SQLState="
                        + sqlException.getSQLState() + "，错误码=" + sqlException.getErrorCode();
                System.out.println(reason);
                tablePrefix = "ymcloud_storage_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "_";
                verifyTablePrefix();
            } else {
                throw e;
            }
        }
        preparedDataSource = pooledDataSource(testUrl(), credentials);
        disposableTablesAttempted = true;
        try (Connection connection = preparedDataSource.getConnection()) {
            assertEquals(temporarySchemaCreated ? temporarySchema : sourceCatalog, connection.getCatalog());
            String schema = InitializationSqlTestSupport.storageSchema(temporarySchemaCreated ? "" : tablePrefix);
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(
                    schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("storage-it",
                Map.of("ymcloud.file.base-dir", directory.toString())));
        context.register(DatabaseConfiguration.class);
        context.refresh();
        assertIsolatedTables();
        testData = context.getBean(TestDataService.class);
        problems = context.getBean(ProblemEntityService.class);
        files = context.getBean(FileEntityService.class);
        versions = context.getBean(TestDataVersionEntityService.class);
        storage = context.getBean(StorageRegistry.class);
        fileStorage = context.getBean(StoredFileService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(temporarySchemaCreated ? temporarySchema : sourceCatalog,
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name='test_data_version_id'",
                Integer.class, tableName("problem")));
    }

    @BeforeEach
    void createIsolatedProblem() {
        assertIsolatedTables();
        Problem problem = new Problem();
        problem.setProblemId("STORAGE-IT-" + UUID.randomUUID());
        problem.setTitle("存储事务测试");
        problem.setCreatedTime(LocalDateTime.now());
        problem.setUpdatedTime(problem.getCreatedTime());
        assertTrue(problems.save(problem));
        problemId = problem.getId();
    }

    @AfterEach
    void removeOnlyOwnRecords() {
        if (problemId == null) {
            return;
        }
        List<File> ownFiles = files.lambdaQuery()
                .eq(File::getBizId, problemId)
                .list();
        versions.lambdaUpdate()
                .eq(TestDataVersion::getProblemId, problemId)
                .remove();
        files.lambdaUpdate()
                .eq(File::getBizId, problemId)
                .remove();
        problems.lambdaUpdate()
                .eq(Problem::getId, problemId)
                .remove();
        for (File file : ownFiles) {
            if (file.getStorageProfile() != null) {
                storage.client(file.getStorageProfile()).delete(file.getBucket(), file.getObjectKey());
            }
        }
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) {
            context.close();
        }
        if (temporarySchemaCreated) {
            verifyTemporarySchema();
            new JdbcTemplate(adminDataSource).execute("DROP DATABASE `" + temporarySchema + "`");
        } else if (disposableTablesAttempted) {
            verifyTablePrefix();
            JdbcTemplate cleanup = new JdbcTemplate(adminDataSource);
            cleanup.execute("DROP TRIGGER IF EXISTS `" + helperName("reject_version") + "`");
            for (String name : List.of("judge", "test_data_version", "file", "problem")) {
                cleanup.execute("DROP TABLE IF EXISTS `" + tableName(name) + "`");
            }
        }
        if (preparedDataSource != null) {
            preparedDataSource.close();
        }
        TableInfoHelper.remove(File.class);
        TableInfoHelper.remove(Problem.class);
        TableInfoHelper.remove(TestDataVersion.class);
    }

    private static void assertIsolatedTables() {
        assertEquals(tableName("file"), TableInfoHelper.getTableInfo(File.class).getTableName());
        assertEquals(tableName("problem"), TableInfoHelper.getTableInfo(Problem.class).getTableName());
        assertEquals(tableName("test_data_version"), TableInfoHelper.getTableInfo(TestDataVersion.class).getTableName());
    }

    private static DatabaseCredentials readCredentials() throws Exception {
        try (InputStream input = new ClassPathResource("application-dev.yml").getInputStream()) {
            Map<?, ?> configuration = new Yaml().load(input);
            Map<?, ?> spring = (Map<?, ?>) configuration.get("spring");
            Map<?, ?> datasource = (Map<?, ?>) spring.get("datasource");
            String url = Objects.toString(datasource.get("url"), "");
            String username = Objects.toString(datasource.get("username"), "");
            String password = Objects.toString(datasource.get("password"), "");
            if (!url.startsWith("jdbc:mysql://") || username.isBlank() || password.isBlank()) {
                throw new IllegalArgumentException("现有数据库配置不完整，禁止推断其他数据库凭据");
            }
            URI endpoint = URI.create(url.substring(5));
            if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("现有数据库URL无法安全解析");
            }
            return new DatabaseCredentials(url, username, password);
        }
    }

    private static DriverManagerDataSource dataSource(String url, DatabaseCredentials configuration) {
        DriverManagerDataSource datasource = new DriverManagerDataSource();
        datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        datasource.setUrl(url);
        datasource.setUsername(configuration.username());
        datasource.setPassword(configuration.password());
        datasource.setConnectionProperties(testConnectionProperties());
        return datasource;
    }

    private static DruidDataSource pooledDataSource(String url, DatabaseCredentials configuration) {
        DruidDataSource datasource = new DruidDataSource();
        datasource.setUrl(url);
        datasource.setUsername(configuration.username());
        datasource.setPassword(configuration.password());
        datasource.setConnectProperties(testConnectionProperties());
        datasource.setInitialSize(0);
        datasource.setMinIdle(0);
        datasource.setMaxActive(4);
        datasource.setMaxWait(30_000);
        return datasource;
    }

    private static Properties testConnectionProperties() {
        Properties properties = new Properties();
        // 本测试不采集遥测，避免 Connector/J 为属性采集触发环境中的慢反向DNS。
        properties.setProperty("openTelemetry", "DISABLED");
        return properties;
    }

    private static String temporaryUrl() {
        verifyTemporarySchema();
        URI endpoint = URI.create(credentials.url().substring(5));
        String query = endpoint.getRawQuery();
        return "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + temporarySchema
                + (query == null ? "" : "?" + query);
    }

    private static String testUrl() {
        if (temporarySchemaCreated) {
            return temporaryUrl();
        }
        verifyTablePrefix();
        return credentials.url();
    }

    private static void verifyTablePrefix() {
        if (tablePrefix == null || !tablePrefix.matches("ymcloud_storage_it_[0-9a-f]{16}_")) {
            throw new IllegalStateException("测试表前缀未通过安全校验");
        }
    }

    private static String tableName(String name) {
        if (!Set.of("file", "problem", "judge", "test_data_version").contains(name)) {
            throw new IllegalArgumentException("未授权的测试表名");
        }
        if (temporarySchemaCreated) {
            return name;
        }
        verifyTablePrefix();
        return tablePrefix + name;
    }

    private static String helperName(String name) {
        if (!"reject_version".equals(name)) {
            throw new IllegalArgumentException("未授权的测试辅助对象名");
        }
        if (temporarySchemaCreated) {
            return "ymcloud_storage_it_" + name;
        }
        verifyTablePrefix();
        return tablePrefix + name;
    }

    private static void verifyTemporarySchema() {
        if (temporarySchema == null || !temporarySchema.matches("ymcloud_storage_test_[0-9a-f]{32}")) {
            throw new IllegalStateException("临时测试库名未通过安全校验");
        }
    }

    private record DatabaseCredentials(String url, String username, String password) {
        @Override
        public String toString() {
            return "DatabaseCredentials[redacted]";
        }
    }

    @Test
    void concurrentPublishSerializesWithoutLosingEitherPair() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> concurrentUpload(start, "a"), executor);
            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> concurrentUpload(start, "b"), executor);
            start.countDown();
            CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        Long current = testData.getCurrentVersion(problemId);
        TestDataManifest manifest = testData.getManifest(problemId, current);
        assertEquals(List.of("/in/a.in", "/in/b.in"), manifest.getEntries().stream()
                .map(TestDataManifest.Entry::getInput).toList());
        assertEquals(2, versionCount());
        assertEquals(4, fileCount());
        assertEquals("a-input", read(current, "/in/a.in"));
        assertEquals("b-input", read(current, "/in/b.in"));
    }

    @Test
    void databasePublicationFailureRollsBackRowsAndCompensatesObjects() throws Exception {
        testData.upload(problemId, pair("a", "input", "output"));
        Long originalVersion = testData.getCurrentVersion(problemId);
        long originalObjects = storedObjectCount();
        jdbc.execute("CREATE TRIGGER `" + helperName("reject_version") + "` BEFORE INSERT ON `"
                + tableName("test_data_version") + "` "
                + "FOR EACH ROW BEGIN IF NEW.problem_id = " + problemId
                + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='storage-it publication failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> testData.upload(problemId, pair("b", "new", "new")));
            assertEquals(originalVersion, testData.getCurrentVersion(problemId));
            assertEquals(1, versionCount());
            assertEquals(2, fileCount());
            assertEquals(originalObjects, storedObjectCount());
            assertEquals("input", read(originalVersion, "/in/a.in"));
        } finally {
            jdbc.execute("DROP TRIGGER `" + helperName("reject_version") + "`");
        }
    }

    @Test
    void pinnedVersionSurvivesReplacementAndCurrentPairDeletion() throws Exception {
        testData.upload(problemId, pair("a", "  old\n", "old-output"));
        Long firstVersion = testData.getCurrentVersion(problemId);
        testData.upload(problemId, pair("a", "new", "new-output"));
        Long secondVersion = testData.getCurrentVersion(problemId);
        testData.delete(problemId, List.of("/in/a.in", "/out/a.out"));
        assertTrue(testData.listCurrent(problemId).isEmpty());
        assertEquals("  old\n", read(firstVersion, "/in/a.in"));
        assertEquals("new", read(secondVersion, "/in/a.in"));
        assertEquals(3, versionCount());
        assertEquals(4, fileCount());
    }

    @Test
    void realLegacyMigrationIsReadOnlyThenIdempotentAndRetainsSource() throws Exception {
        Path root = legacyPair("数据", "\n 输入 \n", "");
        TestDataMigrationReport inventory = testData.migrateLegacy(problemId, false);
        assertTrue(inventory.getErrors().isEmpty());
        assertEquals(2, inventory.getFileCount());
        assertEquals(0, versionCount());
        assertEquals(0, fileCount());
        TestDataMigrationReport imported = testData.migrateLegacy(problemId, true);
        assertTrue(imported.isApplied());
        assertNotNull(imported.getVersionId());
        assertEquals(imported.getVersionId(), testData.migrateLegacy(problemId, true).getVersionId());
        assertEquals(1, versionCount());
        assertEquals(2, fileCount());
        assertEquals("\n 输入 \n", read(imported.getVersionId(), "/in/数据.in"));
        assertEquals("", read(imported.getVersionId(), "/out/数据.out"));
        assertTrue(Files.exists(root.resolve("in/数据.in")));
        assertTrue(Files.exists(root.resolve("out/数据.out")));
    }

    @Test
    void legacyHashMismatchAndTraversalNeverPublish() throws Exception {
        Path root = legacyPair("a", "input", "output");
        Files.writeString(root.resolve("info.json"), "[{\"id\":1,\"input\":\"/in/a.in\",\"output\":\"/out/a.out\","
                + "\"inputSha256\":\"" + "0".repeat(64) + "\"}]", StandardCharsets.UTF_8);
        TestDataMigrationReport report = testData.migrateLegacy(problemId, true);
        assertFalse(report.getErrors().isEmpty());
        assertFalse(report.isApplied());
        assertEquals(0, versionCount());
        assertEquals(0, fileCount());
        assertThrows(RuntimeException.class, () -> testData.openFile(problemId, null, "/in/../a.in"));
    }

    @Test
    void publicLegacyFileMigrationKeepsIdUrlAndBytesAndCanRepeat() throws Exception {
        byte[] bytes = "历史公开头像\n".getBytes(StandardCharsets.UTF_8);
        File original = legacyPublicFile(bytes);
        String originalPath = original.getStoragePath();
        String originalUrl = original.getUrl();
        File inventory = fileStorage.migrateLocal(original.getId(), false);
        assertNotNull(inventory.getSha256());
        assertNull(files.getById(original.getId()).getSha256());
        assertNull(files.getById(original.getId()).getStorageProfile());
        File migrated = fileStorage.migrateLocal(original.getId(), true);
        assertEquals(original.getId(), migrated.getId());
        assertEquals(originalUrl, migrated.getUrl());
        assertEquals(originalPath, migrated.getLegacyPath());
        assertEquals("local", migrated.getStorageProfile());
        assertEquals(migrated.getObjectKey(), fileStorage.migrateLocal(original.getId(), true).getObjectKey());
        assertEquals(original.getId(), fileStorage.resolvePublicUrl(originalUrl).getId());
        try (InputStream input = fileStorage.open(migrated)) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve(originalPath)));
    }

    @Test
    void legacyFileSizeAndHashMismatchDoNotChangeOriginalMetadata() throws Exception {
        File original = legacyPublicFile("bytes".getBytes(StandardCharsets.UTF_8));
        files.lambdaUpdate()
                .eq(File::getId, original.getId())
                .set(File::getFileSize, 6L)
                .update();
        assertThrows(RuntimeException.class, () -> fileStorage.migrateLocal(original.getId(), true));
        assertNull(files.getById(original.getId()).getStorageProfile());
        files.lambdaUpdate()
                .eq(File::getId, original.getId())
                .set(File::getFileSize, 5L)
                .set(File::getSha256, "0".repeat(64))
                .update();
        assertThrows(RuntimeException.class, () -> fileStorage.migrateLocal(original.getId(), true));
        assertNull(files.getById(original.getId()).getStorageProfile());
        assertEquals(original.getStoragePath(), files.getById(original.getId()).getStoragePath());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_S3_TESTS", matches = "true")
    void realSeaweedPublicationFailureRollsBackDatabaseAndObjectsTogether() throws Exception {
        StorageProperties properties = context.getBean(StorageProperties.class);
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setType("s3");
        profile.setEndpoint(System.getenv("YMCLOUD_S3_ENDPOINT"));
        profile.setAccessKey(System.getenv("YMCLOUD_S3_ACCESS_KEY"));
        profile.setSecretKey(System.getenv("YMCLOUD_S3_SECRET_KEY"));
        properties.getProfiles().put("seaweedfs", profile);
        properties.setActive("seaweedfs");
        boolean triggerCreated = false;
        try (S3Client observer = S3Client.builder()
                .endpointOverride(URI.create(profile.getEndpoint()))
                .region(Region.of(profile.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(profile.getAccessKey(), profile.getSecretKey())))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build()) {
            testData.upload(problemId, pair("a", "  actual s3 input\n", "s3-output"));
            Long originalVersion = testData.getCurrentVersion(problemId);
            long originalObjects = objectCount(observer, profile.getPrivateBucket());
            jdbc.execute("CREATE TRIGGER `" + helperName("reject_version") + "` BEFORE INSERT ON `"
                    + tableName("test_data_version") + "` "
                    + "FOR EACH ROW BEGIN IF NEW.problem_id = " + problemId
                    + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='storage-it publication failure'; END IF; END");
            triggerCreated = true;
            assertThrows(RuntimeException.class, () -> testData.upload(problemId, pair("b", "new", "new")));
            assertEquals(originalVersion, testData.getCurrentVersion(problemId));
            assertEquals(1, versionCount());
            assertEquals(2, fileCount());
            assertEquals(originalObjects, objectCount(observer, profile.getPrivateBucket()));
            assertEquals("  actual s3 input\n", read(originalVersion, "/in/a.in"));
        } finally {
            if (triggerCreated) {
                jdbc.execute("DROP TRIGGER `" + helperName("reject_version") + "`");
            }
            properties.setActive("local");
        }
    }

    private long objectCount(S3Client client, String bucket) {
        return client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).build())
                .stream()
                .mapToLong(page -> page.contents().size())
                .sum();
    }

    private File legacyPublicFile(byte[] bytes) throws Exception {
        String path = "public/avatar/" + UUID.randomUUID() + ".png";
        Path original = directory.resolve(path);
        Files.createDirectories(original.getParent());
        Files.write(original, bytes);
        File file = new File();
        file.setFileName("历史头像.png");
        file.setFileType("image/png");
        file.setFileSize((long) bytes.length);
        file.setStoragePath(path);
        file.setStorageType(0);
        file.setUrl("/files/" + path.substring("public/".length()));
        file.setBizType(FileBizType.IMAGE);
        file.setBizId(problemId);
        file.setIsPublic(1);
        file.setCreatedTime(LocalDateTime.now());
        file.setUpdatedTime(file.getCreatedTime());
        assertTrue(files.save(file));
        return file;
    }

    private void concurrentUpload(CountDownLatch start, String name) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发启动超时");
            }
            testData.upload(problemId, pair(name, name + "-input", name + "-output"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String read(Long versionId, String path) throws Exception {
        try (InputStream input = testData.openFile(problemId, versionId, path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private long versionCount() {
        return versions.lambdaQuery()
                .eq(TestDataVersion::getProblemId, problemId)
                .count();
    }

    private long fileCount() {
        return files.lambdaQuery()
                .eq(File::getBizType, FileBizType.TEST_DATA)
                .eq(File::getBizId, problemId)
                .count();
    }

    private long storedObjectCount() throws Exception {
        Path privateDirectory = directory.resolve("private");
        if (!Files.exists(privateDirectory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.list(privateDirectory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private MultipartFile[] pair(String name, String input, String output) {
        return new MultipartFile[]{new MockMultipartFile("file", name + ".in", "text/plain",
                input.getBytes(StandardCharsets.UTF_8)), new MockMultipartFile("file", name + ".out", "text/plain",
                output.getBytes(StandardCharsets.UTF_8))};
    }

    private Path legacyPair(String name, String input, String output) throws Exception {
        Path root = directory.resolve("private/problem/" + problemId);
        Files.createDirectories(root.resolve("in"));
        Files.createDirectories(root.resolve("out"));
        Files.writeString(root.resolve("in/" + name + ".in"), input, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("out/" + name + ".out"), output, StandardCharsets.UTF_8);
        return root;
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = FileMapper.class)
    @Import({FileEntityServiceImpl.class, ProblemEntityServiceImpl.class, TestDataVersionEntityServiceImpl.class,
            StorageRegistry.class, StoredFileService.class, TestDataServiceImpl.class})
    static class DatabaseConfiguration {

        @Bean
        DataSource dataSource() {
            return preparedDataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig databaseConfiguration = new GlobalConfig.DbConfig();
            databaseConfiguration.setTableUnderline(true);
            if (!temporarySchemaCreated) {
                verifyTablePrefix();
                databaseConfiguration.setTablePrefix(tablePrefix);
            }
            globalConfig.setDbConfig(databaseConfiguration);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(globalConfig);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        @Primary
        StorageProperties storageProperties() {
            StorageProperties properties = new StorageProperties();
            properties.setActive("local");
            properties.setProfiles(new HashMap<>());
            return properties;
        }
    }
}

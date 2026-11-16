package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.dao.impl.FileEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ProblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.TestDataVersionEntityServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.impl.FileServiceImpl;
import com.ymcloud.web.service.oj.TestDataService;
import com.ymcloud.web.service.oj.impl.TestDataServiceImpl;
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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 在本机独立临时数据库中验证测试数据发布、事务回滚和固定版本读取
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
    static JdbcTemplate jdbc;
    static DriverManagerDataSource adminDataSource;
    static DruidDataSource preparedDataSource;
    static DatabaseCredentials credentials;
    static String temporarySchema;
    static boolean temporarySchemaCreated;
    Long problemId;

    /**
     * 创建独立测试库并装配真实事务服务；没有建库权限时直接失败
     *
     * @throws Exception 配置读取或数据库初始化失败
     */
    @BeforeAll
    static void openDatabase() throws Exception {
        credentials = readCredentials();
        adminDataSource = dataSource(credentials.url(), credentials);
        JdbcTemplate administration = new JdbcTemplate(adminDataSource);
        assertEquals(1, administration.queryForObject("SELECT 1", Integer.class));
        temporarySchema = "ymcloud_storage_test_" + UUID.randomUUID().toString().replace("-", "");
        verifyTemporarySchema();
        administration.execute("CREATE DATABASE `" + temporarySchema + "` CHARACTER SET utf8mb4");
        temporarySchemaCreated = true;
        preparedDataSource = pooledDataSource(temporaryUrl(), credentials);
        try (Connection connection = preparedDataSource.getConnection()) {
            assertEquals(temporarySchema, connection.getCatalog());
            String schema = InitializationSqlTestSupport.storageSchema();
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(
                    schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
        new JdbcTemplate(preparedDataSource).update("INSERT INTO storage_config "
                + "(id,name,driver_type,public_bucket,private_bucket,is_default,created_time,updated_time) "
                + "VALUES ('local','本地文件','LOCAL','public','private',1,NOW(),NOW())");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("storage-it",
                Map.of("ymcloud.file.base-dir", directory.toString())));
        context.getBeanFactory().registerSingleton("redisCache",
                org.mockito.Mockito.mock(com.ymcloud.common.utils.RedisCache.class));
        context.getBeanFactory().registerSingleton("contestEntityService",
                org.mockito.Mockito.mock(com.ymcloud.core.web.dao.ContestEntityService.class));
        context.register(DatabaseConfiguration.class);
        context.refresh();
        assertIsolatedTables();
        testData = context.getBean(TestDataService.class);
        problems = context.getBean(ProblemEntityService.class);
        files = context.getBean(FileEntityService.class);
        versions = context.getBean(TestDataVersionEntityService.class);
        storage = context.getBean(StorageRegistry.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertEquals(temporarySchema, jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name='test_data_version_id'",
                Integer.class, "problem"));
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

    /**
     * 删除本次题目及版本记录，按精确位置清理测试对象
     */
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
            storage.client(file.getStorageKey()).delete(file.getBucket(), file.getObjectKey());
        }
    }

    /**
     * 关闭连接并删除本次创建的独立数据库
     */
    @AfterAll
    static void closeDatabase() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (preparedDataSource != null) {
                preparedDataSource.close();
            }
            if (temporarySchemaCreated) {
                verifyTemporarySchema();
                new JdbcTemplate(adminDataSource).execute("DROP DATABASE `" + temporarySchema + "`");
            }
            TableInfoHelper.remove(File.class);
            TableInfoHelper.remove(Problem.class);
            TableInfoHelper.remove(TestDataVersion.class);
        }
    }

    /**
     * 确认实体映射使用独立数据库中的正式表名
     */
    private static void assertIsolatedTables() {
        assertEquals("file", TableInfoHelper.getTableInfo(File.class).getTableName());
        assertEquals("problem", TableInfoHelper.getTableInfo(Problem.class).getTableName());
        assertEquals("test_data_version", TableInfoHelper.getTableInfo(TestDataVersion.class).getTableName());
    }

    /**
     * 从开发配置读取本机数据库凭据，禁止访问远程数据库
     *
     * @return 本机数据库连接信息
     * @throws Exception 配置不存在或无法读取
     */
    private static DatabaseCredentials readCredentials() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("sql/ymcloud.sql"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("无法定位项目开发配置");
        }
        try (InputStream input = Files.newInputStream(root.resolve("ymcloud-web/src/main/resources/application-dev.yml"))) {
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
            if (!("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()))
                    || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("集成测试只允许使用本机 MySQL");
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
        jdbc.execute("CREATE TRIGGER reject_version BEFORE INSERT ON test_data_version "
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
            jdbc.execute("DROP TRIGGER reject_version");
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

    /**
     * 真实 S3 发布失败时同时回滚数据库与本次上传对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_S3_TESTS", matches = "true")
    void realSeaweedPublicationFailureRollsBackDatabaseAndObjectsTogether() throws Exception {
        StorageProperties properties = context.getBean(StorageProperties.class);
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setDriverType(StorageDriverType.S3);
        profile.setEndpoint(System.getenv("YMCLOUD_S3_ENDPOINT"));
        profile.setAccessKey(System.getenv("YMCLOUD_S3_ACCESS_KEY"));
        profile.setSecretKey(System.getenv("YMCLOUD_S3_SECRET_KEY"));
        properties.getProfiles().put("seaweedfs", profile);
        properties.setActive("seaweedfs");
        jdbc.update("INSERT INTO storage_config (id,name,driver_type,public_bucket,private_bucket,created_time,updated_time) "
                + "VALUES ('seaweedfs','集成测试对象存储','S3',?,?,NOW(),NOW())",
                profile.getPublicBucket(), profile.getPrivateBucket());
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
            jdbc.execute("CREATE TRIGGER reject_version BEFORE INSERT ON test_data_version "
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
                jdbc.execute("DROP TRIGGER reject_version");
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

    /**
     * 递归统计本次测试的私有对象，覆盖命名空间下的回滚清理
     * @return 实际普通对象文件数量
     * @throws Exception 遍历对象目录失败
     */
    private long storedObjectCount() throws Exception {
        Path privateDirectory = directory.resolve("private");
        if (!Files.exists(privateDirectory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(privateDirectory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private MultipartFile[] pair(String name, String input, String output) {
        return new MultipartFile[]{new MockMultipartFile("file", name + ".in", "text/plain",
                input.getBytes(StandardCharsets.UTF_8)), new MockMultipartFile("file", name + ".out", "text/plain",
                output.getBytes(StandardCharsets.UTF_8))};
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = FileMapper.class)
    @Import({FileEntityServiceImpl.class, ProblemEntityServiceImpl.class, TestDataVersionEntityServiceImpl.class,
            StorageRegistry.class, FileServiceImpl.class, TestDataServiceImpl.class})
    static class DatabaseConfiguration {

        @Bean
        DataSource dataSource() {
            return preparedDataSource;
        }

        /**
         * 使用真实实体映射访问独立测试库
         *
         * @param dataSource 测试库数据源
         * @return MyBatis 会话工厂
         * @throws Exception 会话工厂初始化失败
         */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
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

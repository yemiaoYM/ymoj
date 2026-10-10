package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.core.storage.FileDigests;
import com.ymcloud.core.storage.StorageDirectoryResolver;
import com.ymcloud.core.storage.StoragePaths;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.storage.TestDataServiceImpl;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.impl.FileEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ProblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.TestDataVersionEntityServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 显式运维工具，不被应用自动运行。仅 apply 模式执行增量迁移；不启动 MQ/定时任务。
 * 只读加载 Spring 运行配置并解析占位符，凭据仅保留在内存；报告保存在忽略的 target 中。
 */
public final class StorageMigrationTool {

    private static ConfigurableEnvironment configurationEnvironment;
    private static Path baseDirectory;
    private static DruidDataSource pooledDataSource;

    private StorageMigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        try {
            execute(args);
        } finally {
            if (pooledDataSource != null) {
                pooledDataSource.close();
            }
        }
    }

    private static void execute(String[] args) throws Exception {
        String mode = args.length == 1 ? args[0] : "plan";
        if (!List.of("plan", "apply", "verify", "smoke-create", "smoke-cleanup").contains(mode)) {
            throw new IllegalArgumentException("仅支持 plan、apply、verify、smoke-create、smoke-cleanup");
        }
        configurationEnvironment = loadConfigurationEnvironment();
        baseDirectory = StorageDirectoryResolver.resolve(
                configurationEnvironment.getRequiredProperty("ymcloud.file.base-dir"));
        if (mode.startsWith("smoke-")) {
            runSmokeArtifact(mode);
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(createDataSource());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("database", jdbc.queryForObject("SELECT DATABASE()", String.class));
        Path output = Path.of("target/migration-backup/" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(output);
        report.put("backupDirectory", output.toAbsolutePath().toString());
        backup(jdbc, output);
        if ("plan".equals(mode)) {
            write(output.resolve("report.json"), report);
            System.out.println("Read-only inventory prepared: " + output.toAbsolutePath());
            return;
        }
        if ("apply".equals(mode)) {
            applySql(jdbc, migrationPath());
            report.put("schemaApplied", true);
        }
        try (AnnotationConfigApplicationContext context = migrationContext()) {
            StoredFileService storedFiles = context.getBean(StoredFileService.class);
            TestDataService testData = context.getBean(TestDataService.class);
            ProblemEntityService problems = context.getBean(ProblemEntityService.class);
            List<Map<String, Object>> fileResults = new ArrayList<>();
            if ("apply".equals(mode)) {
                for (File file : storedFiles.legacyInventory()) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", file.getId());
                    try {
                        storedFiles.migrateLocal(file.getId(), false);
                        File migrated = storedFiles.migrateLocal(file.getId(), true);
                        result.put("sha256", migrated.getSha256());
                        result.put("storageProfile", migrated.getStorageProfile());
                        result.put("ok", true);
                    } catch (RuntimeException e) {
                        result.put("ok", false);
                        result.put("error", e.getMessage());
                    }
                    fileResults.add(result);
                }
            }
            report.put("files", fileResults);
            List<Map<String, Object>> verifiedFiles = new ArrayList<>();
            for (File file : context.getBean(FileEntityService.class).lambdaQuery()
                    .orderByAsc(File::getId)
                    .list()) {
                Map<String, Object> verified = new LinkedHashMap<>();
                verified.put("id", file.getId());
                try (InputStream input = storedFiles.open(file)) {
                    java.security.MessageDigest digest = FileDigests.digest();
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    long bytes = 0;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count;
                        digest.update(buffer, 0, count);
                    }
                    String hash = java.util.HexFormat.of().formatHex(digest.digest());
                    if (file.getFileSize() != null && file.getFileSize() != bytes) {
                        throw new IllegalStateException("迁移后文件大小不一致");
                    }
                    if (file.getSha256() != null && !file.getSha256().equals(hash)) {
                        throw new IllegalStateException("迁移后文件内容哈希不一致");
                    }
                    if (file.getStorageProfile() != null && file.getSha256() == null) {
                        throw new IllegalStateException("对象文件缺少内容哈希");
                    }
                    verified.put("sha256", hash);
                    verified.put("ok", true);
                } catch (Exception e) {
                    verified.put("ok", false);
                    verified.put("error", e.getMessage());
                }
                verifiedFiles.add(verified);
            }
            report.put("verifiedFiles", verifiedFiles);
            List<TestDataMigrationReport> testResults = new ArrayList<>();
            List<Problem> allProblems = problems.lambdaQuery()
                    .orderByAsc(Problem::getId)
                    .list();
            int processed = 0;
            for (Problem problem : allProblems) {
                try {
                    TestDataMigrationReport inventory = testData.migrateLegacy(problem.getId(), false);
                    if (inventory.getFileCount() > 0 || !inventory.getErrors().isEmpty()) {
                        TestDataMigrationReport result = "apply".equals(mode)
                                ? testData.migrateLegacy(problem.getId(), true) : inventory;
                        if (result.getVersionId() != null && result.getErrors().isEmpty()) {
                            verifyVersion(testData, problem.getId(), result.getVersionId());
                        }
                        testResults.add(result);
                    }
                } catch (Exception e) {
                    TestDataMigrationReport failed = new TestDataMigrationReport();
                    failed.setProblemId(problem.getId());
                    failed.getErrors().add(e.getMessage());
                    testResults.add(failed);
                }
                processed++;
                if (processed % 10 == 0) {
                    System.out.println("Verified problem metadata: " + processed + "/" + allProblems.size());
                }
            }
            report.put("testData", testResults);
            report.put("problemCount", allProblems.size());
            report.put("remainingLocalFiles", storedFiles.legacyInventory().size());
            report.put("errors", fileResults.stream().filter(result -> Boolean.FALSE.equals(result.get("ok"))).count()
                    + verifiedFiles.stream().filter(result -> Boolean.FALSE.equals(result.get("ok"))).count()
                    + testResults.stream().filter(result -> !result.getErrors().isEmpty()).count());
            report.put("orphanTestDirectories", orphanDirectories(allProblems));
            write(output.resolve("report.json"), report);
            System.out.println("Migration verification complete, errors=" + report.get("errors")
                    + ", report=" + output.resolve("report.json").toAbsolutePath());
            if (((Number) report.get("errors")).longValue() > 0) {
                throw new IllegalStateException("迁移存在数据异常，请核对已保存报告，未删除原始文件");
            }
        }
    }

    /** 只装配配置数据，不创建业务 Bean 或连接数据库、Redis、RabbitMQ。 */
    static ConfigurableEnvironment loadConfigurationEnvironment() {
        return loadConfigurationEnvironment(new StandardEnvironment());
    }

    static ConfigurableEnvironment loadConfigurationEnvironment(ConfigurableEnvironment environment) {
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    private static AnnotationConfigApplicationContext migrationContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("migration",
                Map.of("ymcloud.file.base-dir", baseDirectory.toString())));
        context.register(MigrationConfiguration.class);
        context.refresh();
        return context;
    }

    /**
     * 只创建无用户或题目关联的唯一测试文件；清理前逐字段核对，绝不删除历史记录。
     */
    private static void runSmokeArtifact(String mode) throws Exception {
        Path reportPath = Path.of("target/test-output/storage-http-probe.json");
        Files.createDirectories(reportPath.getParent());
        try (AnnotationConfigApplicationContext context = migrationContext()) {
            StoredFileService files = context.getBean(StoredFileService.class);
            if ("smoke-create".equals(mode)) {
                if (Files.exists(reportPath) && !JSON.parseObject(Files.readString(reportPath,
                        StandardCharsets.UTF_8)).getBooleanValue("cleaned")) {
                    throw new IllegalStateException("请先清理上一次 HTTP 测试文件，不能覆盖追踪记录");
                }
                byte[] bytes = new byte[64 * 1024];
                new java.util.Random(20261010L).nextBytes(bytes);
                String name = "storage-smoke-" + java.util.UUID.randomUUID() + ".bin";
                File file = files.save(new ByteArrayInputStream(bytes), bytes.length, name,
                        "application/octet-stream", FileBizType.GENERAL, null, true);
                Map<String, Object> probe = new LinkedHashMap<>();
                probe.put("id", file.getId());
                probe.put("path", file.getUrl());
                probe.put("fileName", name);
                probe.put("sha256", file.getSha256());
                probe.put("bytes", file.getFileSize());
                probe.put("storageProfile", file.getStorageProfile());
                probe.put("bucket", file.getBucket());
                probe.put("objectKey", file.getObjectKey());
                probe.put("cleaned", false);
                try {
                    write(reportPath, probe);
                } catch (Exception e) {
                    files.delete(file.getId());
                    throw e;
                }
                System.out.println("Tracked HTTP probe file prepared: " + reportPath.toAbsolutePath());
                return;
            }
            var probe = JSON.parseObject(Files.readString(reportPath, StandardCharsets.UTF_8));
            if (probe.getBooleanValue("cleaned")) {
                System.out.println("HTTP probe file was already cleaned.");
                return;
            }
            File file = files.get(probe.getLong("id"));
            if (file.getFileName() == null
                    || !file.getFileName().matches("storage-smoke-[a-f0-9-]{36}\\.bin")
                    || !Objects.equals(file.getFileName(), probe.getString("fileName"))
                    || file.getBizType() != FileBizType.GENERAL || file.getBizId() != null
                    || !Integer.valueOf(1).equals(file.getIsPublic())
                    || !"seaweedfs".equals(file.getStorageProfile())
                    || !Objects.equals(file.getBucket(), probe.getString("bucket"))
                    || !Objects.equals(file.getObjectKey(), probe.getString("objectKey"))
                    || !Objects.equals(file.getUrl(), probe.getString("path"))
                    || !Objects.equals(file.getSha256(), probe.getString("sha256"))
                    || !Long.valueOf(64 * 1024).equals(file.getFileSize())) {
                throw new IllegalStateException("测试文件身份核对失败，中止清理");
            }
            files.delete(file.getId());
            probe.put("cleaned", true);
            write(reportPath, probe);
            System.out.println("Tracked HTTP probe file cleaned: " + file.getId());
        }
    }

    private static void verifyVersion(TestDataService testData, Long problemId, Long versionId) throws Exception {
        TestDataManifest manifest = testData.getManifest(problemId, versionId);
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            for (String path : List.of(entry.getInput(), entry.getOutput())) {
                try (InputStream input = testData.openFile(problemId, versionId, path)) {
                    // 消费到 EOF，触发版本大小和哈希校验，避免将内容装入内存。
                    FileDigests.sha256(input);
                }
            }
        }
    }

    private static List<String> orphanDirectories(List<Problem> problems) throws Exception {
        Path root = baseDirectory.resolve("private/problem");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        var ids = problems.stream().map(problem -> problem.getId().toString()).collect(java.util.stream.Collectors.toSet());
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !ids.contains(name))
                    .sorted()
                    .toList();
        }
    }

    private static void backup(JdbcTemplate jdbc, Path output) throws Exception {
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String table : List.of("file", "problem", "judge")) {
            tables.put(table, jdbc.queryForMap("SHOW CREATE TABLE `" + table + "`"));
        }
        write(output.resolve("schema-before.json"), tables);
        write(output.resolve("files-before.json"), jdbc.queryForList("SELECT * FROM `file`"));
        write(output.resolve("problem-versions-before.json"), jdbc.queryForList(
                "SELECT * FROM `problem`"));
        List<Map<String, Object>> local = new ArrayList<>();
        if (Files.isDirectory(baseDirectory)) {
            try (Stream<Path> paths = Files.walk(baseDirectory)) {
                for (Path path : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String relative = baseDirectory.relativize(path).toString().replace('\\', '/');
                    if (relative.startsWith("tmp/") || relative.startsWith(".staging/")) {
                        continue;
                    }
                    path = StoragePaths.resolve(baseDirectory, relative);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("path", relative);
                    item.put("bytes", Files.size(path));
                    try (InputStream input = Files.newInputStream(path)) {
                        item.put("sha256", FileDigests.sha256(input));
                    }
                    local.add(item);
                }
            }
        }
        write(output.resolve("local-files-before.json"), local);
    }

    private static void applySql(JdbcTemplate jdbc, Path path) throws Exception {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("DELIMITER ")) {
                delimiter = line.trim().substring("DELIMITER ".length());
                continue;
            }
            statement.append(line).append('\n');
            String sql = statement.toString().stripTrailing();
            if (sql.endsWith(delimiter)) {
                jdbc.execute(sql.substring(0, sql.length() - delimiter.length()));
                statement.setLength(0);
            }
        }
    }

    private static Path migrationPath() {
        Path path = Path.of("sql/migrations/20261010_object_storage.sql");
        return Files.isRegularFile(path) ? path : Path.of("../sql/migrations/20261010_object_storage.sql");
    }

    private static void write(Path file, Object content) throws Exception {
        Files.writeString(file, JSON.toJSONString(content), StandardCharsets.UTF_8);
    }

    private static DataSource createDataSource() {
        if (pooledDataSource != null) {
            return pooledDataSource;
        }
        DruidDataSource source = new DruidDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(configurationEnvironment.getRequiredProperty("spring.datasource.url"));
        source.setUsername(configurationEnvironment.getRequiredProperty("spring.datasource.username"));
        source.setPassword(configurationEnvironment.getRequiredProperty("spring.datasource.password"));
        source.setInitialSize(1);
        source.setMinIdle(1);
        source.setMaxActive(4);
        source.setMaxWait(30000);
        source.setConnectTimeout(30000);
        source.setSocketTimeout(30000);
        source.setValidationQuery("SELECT 1");
        source.setTestOnBorrow(false);
        java.util.Properties connectionProperties = new java.util.Properties();
        connectionProperties.setProperty("openTelemetry", "DISABLED");
        source.setConnectProperties(connectionProperties);
        pooledDataSource = source;
        return source;
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = FileMapper.class)
    @Import({FileEntityServiceImpl.class, ProblemEntityServiceImpl.class, TestDataVersionEntityServiceImpl.class,
            StorageRegistry.class, StoredFileService.class, TestDataServiceImpl.class})
    static class MigrationConfiguration {

        @Bean
        DataSource dataSource() {
            return createDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            GlobalConfig global = new GlobalConfig();
            global.setDbConfig(new GlobalConfig.DbConfig());
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(global);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        @Primary
        StorageProperties storageProperties() {
            StorageProperties properties = new StorageProperties();
            StorageProperties.Profile profile = new StorageProperties.Profile();
            profile.setType("s3");
            profile.setEndpoint(System.getenv("YMCLOUD_S3_ENDPOINT"));
            String region = System.getenv("YMCLOUD_S3_REGION");
            if (region != null && !region.isBlank()) {
                profile.setRegion(region);
            }
            String publicBucket = System.getenv("YMCLOUD_S3_PUBLIC_BUCKET");
            if (publicBucket != null && !publicBucket.isBlank()) {
                profile.setPublicBucket(publicBucket);
            }
            String privateBucket = System.getenv("YMCLOUD_S3_PRIVATE_BUCKET");
            if (privateBucket != null && !privateBucket.isBlank()) {
                profile.setPrivateBucket(privateBucket);
            }
            profile.setAccessKey(System.getenv("YMCLOUD_S3_ACCESS_KEY"));
            profile.setSecretKey(System.getenv("YMCLOUD_S3_SECRET_KEY"));
            properties.getProfiles().put("seaweedfs", profile);
            properties.setActive("seaweedfs");
            return properties;
        }
    }
}

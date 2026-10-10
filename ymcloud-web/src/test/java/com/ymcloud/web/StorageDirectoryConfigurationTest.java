package com.ymcloud.web;

import com.ymcloud.core.storage.StorageDirectoryResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用 CLI 同一配置加载入口，只解析属性；不启动业务上下文或连接数据库、Redis、RabbitMQ。 */
class StorageDirectoryConfigurationTest {

    @TempDir
    private Path directory;

    @Test
    void mainConfigurationDefaultsToLocalFilesAndNestedJudgeWorkDirectory() {
        ConfigurableEnvironment environment = configuration(Map.of());
        assertEquals("./files", environment.getRequiredProperty("ymcloud.file.base-dir"));
        assertEquals("./files/tmp", environment.getRequiredProperty("ymcloud.docker.host-working-dir"));
        assertEquals("local", environment.getRequiredProperty("ymcloud.storage.active"));
        assertEquals("local", environment.getRequiredProperty("ymcloud.storage.profiles.local.type"));
        assertEquals("", environment.getProperty("ymcloud.storage.config-encryption-key"));
        assertTrue(List.of(environment.getActiveProfiles()).contains("dev"));
    }

    @Test
    void developmentAndProductionProfilesInheritStorageAndJudgeDirectories() throws Exception {
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        for (String profile : List.of("dev", "prod")) {
            for (var source : yaml.load("profile-directory-" + profile,
                    new ClassPathResource("application-" + profile + ".yml"))) {
                assertNull(source.getProperty("ymcloud.file.base-dir"),
                        "Profiles must inherit the file directory from main configuration.");
                assertNull(source.getProperty("ymcloud.docker.host-working-dir"),
                        "Profiles must inherit the judge directory from main configuration.");
            }
            ConfigurableEnvironment environment = configuration(Map.of("spring.profiles.active", profile));
            assertEquals("./files", environment.getRequiredProperty("ymcloud.file.base-dir"));
            assertEquals("./files/tmp", environment.getRequiredProperty("ymcloud.docker.host-working-dir"));
            assertTrue(List.of(environment.getActiveProfiles()).contains(profile));
        }
    }

    @Test
    void defaultRelativePathsResolveToRepositoryFilesAndTmpForCliAndWeb() throws Exception {
        Path repository = repository();
        Path moduleDirectory = Files.createDirectories(repository.resolve("ymcloud-web/target/classes"));
        ConfigurableEnvironment environment = configuration(Map.of("spring.profiles.active", "prod"));
        assertEquals(repository.resolve("files"), StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.file.base-dir"), moduleDirectory, moduleDirectory));
        assertEquals(repository.resolve("files/tmp"), StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.docker.host-working-dir"), moduleDirectory, moduleDirectory));
        assertFalse(Files.exists(repository.resolve("files")), "Configuration and path loading must remain read-only.");
    }

    @Test
    void explicitFileEnvironmentOverrideAlsoChangesDefaultNestedJudgeDirectory() throws Exception {
        Path fileDirectory = directory.resolve("explicit-files").toAbsolutePath().normalize();
        Path unrelatedWorkingDirectory = Files.createDirectories(directory.resolve("unrelated-cwd"));
        ConfigurableEnvironment environment = configuration(Map.of("YMCLOUD_FILE_BASE_DIR", fileDirectory.toString()));
        assertEquals(fileDirectory.toString(), environment.getRequiredProperty("ymcloud.file.base-dir"));
        assertEquals(fileDirectory, StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.file.base-dir"), unrelatedWorkingDirectory, null));
        assertEquals(fileDirectory.resolve("tmp"), StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.docker.host-working-dir"), unrelatedWorkingDirectory, null));
        assertFalse(Files.exists(fileDirectory));
    }

    @Test
    void explicitJudgeEnvironmentOverrideIsIndependentOfFileStorageRoot() throws Exception {
        Path repository = repository();
        Path judgeDirectory = directory.resolve("separate-judge-work").toAbsolutePath().normalize();
        ConfigurableEnvironment environment = configuration(Map.of("YMCLOUD_JUDGE_WORK_DIR", judgeDirectory.toString()));
        assertEquals("./files", environment.getRequiredProperty("ymcloud.file.base-dir"));
        assertEquals(judgeDirectory.toString(), environment.getRequiredProperty("ymcloud.docker.host-working-dir"));
        assertEquals(repository.resolve("files"), StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.file.base-dir"), repository, repository));
        assertEquals(judgeDirectory, StorageDirectoryResolver.resolve(
                environment.getRequiredProperty("ymcloud.docker.host-working-dir"), repository, repository));
        assertFalse(Files.exists(judgeDirectory));
    }

    @Test
    void optionalImportedConfigurationAndItsPlaceholdersUseSameCliLoadingRules() throws Exception {
        Path imported = directory.resolve("application-storage-test.yml");
        Files.writeString(imported, """
                ymcloud:
                  file:
                    base-dir: ${YMCLOUD_FILE_BASE_DIR:./imported-files}
                  docker:
                    host-working-dir: ${YMCLOUD_JUDGE_WORK_DIR:${ymcloud.file.base-dir}/tmp}
                """, StandardCharsets.UTF_8);
        ConfigurableEnvironment environment = configuration(Map.of("YMCLOUD_STORAGE_CONFIG", configurationPath(imported)));
        assertEquals("./imported-files", environment.getRequiredProperty("ymcloud.file.base-dir"));
        assertEquals("./imported-files/tmp", environment.getRequiredProperty("ymcloud.docker.host-working-dir"));

        Path fileDirectory = directory.resolve("import-file-override").toAbsolutePath().normalize();
        Path judgeDirectory = directory.resolve("import-judge-override").toAbsolutePath().normalize();
        ConfigurableEnvironment overridden = configuration(Map.of(
                "YMCLOUD_STORAGE_CONFIG", configurationPath(imported),
                "YMCLOUD_FILE_BASE_DIR", fileDirectory.toString(),
                "YMCLOUD_JUDGE_WORK_DIR", judgeDirectory.toString()));
        assertEquals(fileDirectory.toString(), overridden.getRequiredProperty("ymcloud.file.base-dir"));
        assertEquals(judgeDirectory.toString(), overridden.getRequiredProperty("ymcloud.docker.host-working-dir"));
        assertFalse(Files.exists(fileDirectory));
        assertFalse(Files.exists(judgeDirectory));
    }

    private ConfigurableEnvironment configuration(Map<String, Object> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        // 隔离宿主机参数和真实秘密配置文件，不修改 System properties 或进程环境变量。
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.location", "classpath:/application.yml");
        properties.put("YMCLOUD_STORAGE_CONFIG", configurationPath(directory.resolve("missing-storage.yml")));
        properties.putAll(overrides);
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-directory-configuration", properties));
        ConfigurableEnvironment loaded = StorageMigrationTool.loadConfigurationEnvironment(environment);
        assertSame(environment, loaded);
        return loaded;
    }

    private Path repository() throws IOException {
        Path root = Files.createDirectories(directory.resolve("checkout"));
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <artifactId>ymcloud</artifactId>
                  <packaging>pom</packaging>
                  <modules>
                    <module>ymcloud-core</module>
                    <module>ymcloud-web</module>
                    <module>ymcloud-judge</module>
                  </modules>
                </project>
                """, StandardCharsets.UTF_8);
        for (String module : List.of("ymcloud-core", "ymcloud-web", "ymcloud-judge")) {
            Files.createDirectories(root.resolve(module));
        }
        return root.toAbsolutePath().normalize();
    }

    private String configurationPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}

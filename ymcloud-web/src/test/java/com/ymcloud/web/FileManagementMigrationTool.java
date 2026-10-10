package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson2.JSON;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * 显式执行文件管理增量迁移；不启动 Web、判题消费者或定时任务。
 * 迁移前保存菜单与授权快照，重复执行校验不增加重复记录，不复制用户密码。
 */
public final class FileManagementMigrationTool {

    private FileManagementMigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"apply".equals(args[0])) {
            throw new IllegalArgumentException("仅支持显式 apply 模式");
        }
        Path migration = Path.of("sql/migrations/20261010_file_management.sql");
        if (!Files.isRegularFile(migration)) {
            throw new IllegalStateException("从项目根目录运行，迁移文件必须存在");
        }
        Map<?, ?> configuration;
        try (InputStream input = new ClassPathResource("application-dev.yml").getInputStream()) {
            configuration = new Yaml().load(input);
        }
        Map<?, ?> database = (Map<?, ?>) ((Map<?, ?>) configuration.get("spring")).get("datasource");
        try (DruidDataSource source = new DruidDataSource()) {
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(Objects.toString(database.get("url")));
            source.setUsername(Objects.toString(database.get("username")));
            source.setPassword(Objects.toString(database.get("password")));
            source.setMaxActive(2);
            source.setConnectTimeout(15000);
            source.setSocketTimeout(30000);
            Properties properties = new Properties();
            properties.setProperty("openTelemetry", "DISABLED");
            source.setConnectProperties(properties);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            Path output = Path.of("target/migration-backup/file-management-" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                    + UUID.randomUUID().toString().substring(0, 8));
            Files.createDirectories(output);
            write(output.resolve("auth-before.json"), jdbc.queryForList("SELECT * FROM auth ORDER BY id"));
            write(output.resolve("role-auth-before.json"), jdbc.queryForList("SELECT * FROM role_auth ORDER BY role_id,auth_id"));
            long originalFiles = jdbc.queryForObject("SELECT COUNT(*) FROM file", Long.class);
            long originalVersions = jdbc.queryForObject("SELECT COUNT(*) FROM test_data_version", Long.class);
            long originalJudges = jdbc.queryForObject("SELECT COUNT(*) FROM judge", Long.class);
            execute(source, migration);
            long authCount = jdbc.queryForObject("SELECT COUNT(*) FROM auth", Long.class);
            long grantsCount = jdbc.queryForObject("SELECT COUNT(*) FROM role_auth", Long.class);
            execute(source, migration);
            if (authCount != jdbc.queryForObject("SELECT COUNT(*) FROM auth", Long.class)
                    || grantsCount != jdbc.queryForObject("SELECT COUNT(*) FROM role_auth", Long.class)
                    || originalFiles != jdbc.queryForObject("SELECT COUNT(*) FROM file", Long.class)
                    || originalVersions != jdbc.queryForObject("SELECT COUNT(*) FROM test_data_version", Long.class)
                    || originalJudges != jdbc.queryForObject("SELECT COUNT(*) FROM judge", Long.class)) {
                throw new IllegalStateException("迁移重复执行或业务数据保留检查失败，核对迁移前快照");
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("applied", true);
            report.put("idempotent", true);
            report.put("filesPreserved", originalFiles);
            report.put("versionsPreserved", originalVersions);
            report.put("judgesPreserved", originalJudges);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM auth WHERE code IN "
                    + "('file:file:list','file:storage:list') AND icon IS NOT NULL", Integer.class) != 0) {
                throw new IllegalStateException("新增子菜单不得设置图标");
            }
            report.put("menus", jdbc.queryForList("SELECT id,code,path,component,icon FROM auth WHERE code LIKE 'file:%' ORDER BY id"));
            report.put("storageState", jdbc.queryForList("SELECT id,active_key,revision FROM storage_config_state"));
            write(output.resolve("report.json"), report);
            System.out.println("File management migration verified; report=" + output.resolve("report.json").toAbsolutePath());
        }
    }

    private static void execute(DruidDataSource source, Path migration) throws Exception {
        try (Connection connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource(migration), StandardCharsets.UTF_8));
        }
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.toJSONString(value), StandardCharsets.UTF_8);
    }
}

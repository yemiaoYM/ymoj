package com.ymcloud.web;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.Constants;
import com.ymcloud.core.config.FastJson2JsonRedisSerializer;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_ABSENT;

/**
 * 显式运行的本地 HTTP / SeaweedFS / 数据库 / 浏览器集成检查。
 * 常规验证仅创建短期测试会话与随机命名通用文件/配置，finally 精确清理；不更改默认存储或用户授权。
 * 额外设置 YMCLOUD_STORAGE_ADMIN_SETUP=true 时，执行用户授权的后台重配并保留 SeaweedFS 配置。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_FILE_ADMIN_HTTP_IT", matches = "true")
class FileManagementRuntimeIT {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Long> ownFiles = new ArrayList<>();
    private final List<String> ownFileNames = new ArrayList<>();
    private final List<byte[]> ownSessions = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> checks = new ArrayList<>();
    private final Path projectRoot = projectRoot();
    private String admin;
    private String ownProfile;

    @Test
    void actualWebsiteStoragePermissionsCrudAndBrowser() throws Exception {
        Map<?, ?> config = yaml(new ClassPathResource("application-dev.yml").getInputStream());
        Map<?, ?> spring = (Map<?, ?>) config.get("spring");
        Map<?, ?> database = (Map<?, ?>) spring.get("datasource");
        Map<?, ?> redis = (Map<?, ?>) ((Map<?, ?>) spring.get("data")).get("redis");
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                Objects.toString(redis.get("host")), Integer.parseInt(Objects.toString(redis.get("port"))));
        redisConfig.setDatabase(Integer.parseInt(Objects.toString(redis.get("database"))));
        redisConfig.setPassword(Objects.toString(redis.get("password"), ""));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(5)).build());
        factory.afterPropertiesSet();
        factory.start();
        try (DruidDataSource source = databaseSource(database); RedisConnection connection = factory.getConnection()) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            try {
                admin = token(jdbc, connection, 1L);
                Long normalId = jdbc.queryForObject("SELECT MIN(u.id) FROM user u WHERE u.id<>1 AND u.status=1 "
                        + "AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role_auth ra ON ra.role_id=ur.role_id "
                        + "JOIN auth a ON a.id=ra.auth_id WHERE ur.user_id=u.id AND a.code IN "
                        + "('admin:access','file:file:list','problem:testcase:query'))", Long.class);
                assertNotNull(normalId, "至少需要一个已有普通账号进行真实权限验证");
                String normal = token(jdbc, connection, normalId);
                if ("true".equals(System.getenv("YMCLOUD_STORAGE_ADMIN_SETUP"))) {
                    List<Map<String, Object>> before = fileLocations(jdbc);
                    long versions = jdbc.queryForObject("SELECT COUNT(*) FROM test_data_version", Long.class);
                    setupSeaweedBrowser();
                    assertTrue(before.equals(fileLocations(jdbc)), "后台重配不得改变已有文件的位置、大小、哈希或业务关联");
                    assertEquals(versions, jdbc.queryForObject("SELECT COUNT(*) FROM test_data_version", Long.class));
                    Map<String, Object> setup = Map.of("filesPreserved", before.size(), "versionsPreserved", versions,
                            "referencedObjectsVerified", jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM file WHERE storage_profile='seaweedfs'", Long.class));
                    report.put("adminSetup", setup);
                    Path setupReport = projectRoot.resolve("target/test-output/storage-admin-setup-preservation.json");
                    Files.createDirectories(setupReport.getParent());
                    Files.writeString(setupReport, JSON.toJSONString(setup), StandardCharsets.UTF_8);
                    checks.add("local-only-startup-and-explicit-admin-seaweed-setup");
                }
                denied("GET", "/admin/file/list", null, null, 401);
                denied("GET", "/admin/storage/config", null, null, 401);
                denied("GET", "/admin/file/list", normal, null, 403);
                denied("GET", "/admin/storage/config", normal, null, 403);
                checks.add("anonymous-and-normal-user-denied");
                JSONObject listing = success("GET", "/admin/file/list?pageNum=1&pageSize=10", admin, null);
                assertTrue(listing.getJSONObject("data").getLongValue("total") >= 1,
                        "集成验证需要已有题目测试数据文件");
                String safeListing = listing.toJSONString();
                assertFalse(safeListing.contains("storagePath"));
                assertFalse(safeListing.contains("legacyPath"));
                assertFalse(safeListing.contains("objectKey"));
                checks.add("safe-file-metadata");
                Long testcaseId = jdbc.queryForObject("SELECT MIN(id) FROM file WHERE biz_type=5", Long.class);
                assertNotNull(testcaseId, "集成验证需要至少一个已有题目测试数据文件");
                denied("GET", "/admin/file/" + testcaseId + "/download", normal, null, 403);
                HttpResponse<byte[]> testcase = request("GET", "/admin/file/" + testcaseId + "/download", admin, null, null);
                assertEquals(200, testcase.statusCode());
                assertEquals("no-store", testcase.headers().firstValue("Cache-Control").orElse(""));
                assertTrue(testcase.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"));
                denied("DELETE", "/admin/file/" + testcaseId, admin, null, 403);
                checks.add("testcase-admin-download-and-delete-protection");
                byte[] payload = new byte[65536];
                new java.security.SecureRandom().nextBytes(payload);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
                JSONObject publicFile = upload(payload, 1);
                JSONObject privateFile = upload(payload, 0);
                assertEquals(hash, publicFile.getString("sha256"));
                assertEquals(hash, privateFile.getString("sha256"));
                assertNull(privateFile.getString("url"));
                assertArrayEquals(payload, request("GET", publicFile.getString("url"), null, null, null).body());
                denied("GET", "/admin/file/" + privateFile.getLong("id") + "/download", null, null, 401);
                denied("GET", "/admin/file/" + privateFile.getLong("id") + "/download", normal, null, 403);
                assertArrayEquals(payload, request("GET", "/admin/file/" + privateFile.getLong("id")
                        + "/download", admin, null, null).body());
                checks.add("public-and-private-real-s3-roundtrip");
                performance(publicFile.getString("url"), payload);
                JSONObject configs = success("GET", "/admin/storage/config", admin, null);
                assertFalse(configs.toJSONString().contains("secretKey"));
                assertFalse(configs.toJSONString().contains("accessKey"));
                checks.add("storage-list-no-credentials");
                {
                    Map<String, String> environment = localStorageEnvironment();
                    ownProfile = "admin-it-" + UUID.randomUUID().toString().substring(0, 8);
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("key", ownProfile);
                    dto.put("name", "受控文件管理集成测试");
                    dto.put("provider", "seaweedfs");
                    dto.put("endpoint", environment.get("YMCLOUD_S3_ENDPOINT"));
                    dto.put("region", environment.get("YMCLOUD_S3_REGION"));
                    dto.put("publicBucket", environment.get("YMCLOUD_S3_PUBLIC_BUCKET"));
                    dto.put("privateBucket", environment.get("YMCLOUD_S3_PRIVATE_BUCKET"));
                    dto.put("pathStyle", true);
                    dto.put("accessKey", environment.get("YMCLOUD_S3_ACCESS_KEY"));
                    dto.put("secretKey", environment.get("YMCLOUD_S3_SECRET_KEY"));
                    success("POST", "/admin/storage/config", admin, JSON.toJSONString(dto));
                    success("POST", "/admin/storage/config/" + ownProfile + "/test", admin, null);
                    Map<String, Object> encrypted = jdbc.queryForMap("SELECT encrypted_access_key,encrypted_secret_key "
                            + "FROM storage_config WHERE id=?", ownProfile);
                    assertFalse(encrypted.containsValue(dto.get("accessKey")));
                    assertFalse(encrypted.containsValue(dto.get("secretKey")));
                    dto.remove("accessKey");
                    dto.remove("secretKey");
                    dto.put("remark", "空凭据保留原值");
                    success("PUT", "/admin/storage/config/" + ownProfile, admin, JSON.toJSONString(dto));
                    success("POST", "/admin/storage/config/" + ownProfile + "/test", admin, null);
                    checks.add("storage-create-encryption-empty-secret-edit-and-real-connectivity-test");
                }
                if ("true".equals(System.getenv("YMCLOUD_FILE_ADMIN_BROWSER_IT"))) {
                    browser();
                    checks.add("real-browser-file-and-storage-pages");
                }
            } finally {
                List<String> cleanupFailures = new ArrayList<>();
                if (admin != null) {
                    // A successful server commit may have a lost HTTP response; recover only exact random identities.
                    for (String name : ownFileNames) {
                        try {
                            List<Map<String, Object>> matches = jdbc.queryForList(
                                    "SELECT id,biz_type,biz_id,uploader_id FROM file WHERE file_name=?", name);
                            for (Map<String, Object> match : matches) {
                                assertEquals(0, ((Number) match.get("biz_type")).intValue());
                                assertNull(match.get("biz_id"));
                                assertEquals(1L, ((Number) match.get("uploader_id")).longValue());
                                Long id = ((Number) match.get("id")).longValue();
                                if (!ownFiles.contains(id)) {
                                    ownFiles.add(id);
                                }
                            }
                        } catch (Exception | AssertionError e) {
                            cleanupFailures.add("unconfirmed-file:" + name);
                        }
                    }
                    for (Long id : ownFiles) {
                        try {
                            success("DELETE", "/admin/file/" + id, admin, null);
                            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file WHERE id=?", Integer.class, id));
                        } catch (Exception | AssertionError e) {
                            cleanupFailures.add("file:" + id);
                        }
                    }
                    if (ownProfile != null) {
                        try {
                            if (jdbc.queryForObject("SELECT COUNT(*) FROM storage_config WHERE id=?", Integer.class, ownProfile) > 0) {
                                assertEquals("受控文件管理集成测试", jdbc.queryForObject(
                                        "SELECT name FROM storage_config WHERE id=?", String.class, ownProfile));
                                success("DELETE", "/admin/storage/config/" + ownProfile, admin, null);
                            }
                            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM storage_config WHERE id=?", Integer.class, ownProfile));
                        } catch (Exception | AssertionError e) {
                            cleanupFailures.add("profile:" + ownProfile);
                        }
                    }
                }
                for (byte[] key : ownSessions) {
                    try {
                        connection.keyCommands().del(key);
                    } catch (RuntimeException e) {
                        cleanupFailures.add("temporary-session");
                    }
                }
                if (cleanupFailures.isEmpty()) {
                    checks.add("owned-files-config-and-sessions-cleaned");
                }
                report.put("cleanupFailures", cleanupFailures);
                report.put("checks", checks);
                Path output = projectRoot.resolve("target/test-output/file-management-runtime.json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, JSON.toJSONString(report), StandardCharsets.UTF_8);
                assertTrue(cleanupFailures.isEmpty(), "测试对象未全部清理，核对本地报告中的精确 ID");
            }
        } finally {
            factory.destroy();
        }
    }

    private JSONObject upload(byte[] bytes, int visibility) throws Exception {
        String boundary = "ymcloud-" + UUID.randomUUID();
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        String name = "admin-http-" + UUID.randomUUID() + ".bin";
        ownFileNames.add(name);
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(bytes);
        multipart.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"isPublic\"\r\n\r\n"
                + visibility + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpResponse<byte[]> response = request("POST", "/admin/file/upload", admin, multipart.toByteArray(),
                "multipart/form-data; boundary=" + boundary);
        JSONObject body = JSON.parseObject(response.body());
        assertEquals(200, body.getIntValue("code"), "受控文件上传失败");
        JSONObject file = body.getJSONObject("data");
        ownFiles.add(file.getLong("id"));
        return file;
    }

    private void performance(String url, byte[] expected) throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Double>> reads = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                reads.add(() -> {
                    long started = System.nanoTime();
                    assertArrayEquals(expected, request("GET", url, null, null, null).body());
                    return (System.nanoTime() - started) / 1_000_000.0;
                });
            }
            List<Double> samples = new ArrayList<>();
            for (var future : executor.invokeAll(reads)) {
                samples.add(future.get());
            }
            samples.sort(Double::compareTo);
            report.put("publicDownload", Map.of("bytes", expected.length, "concurrency", 4, "requests", 20,
                    "p50Ms", samples.get(9), "p95Ms", samples.get(18), "errors", 0));
            checks.add("four-concurrent-twenty-downloads-hash-verified");
        } finally {
            executor.shutdownNow();
        }
    }

    private JSONObject success(String method, String path, String token, String body) throws Exception {
        HttpResponse<byte[]> response = request(method, path, token,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8), body == null ? null : "application/json");
        JSONObject result = JSON.parseObject(response.body());
        assertEquals(200, result.getIntValue("code"), method + " " + path + " 失败（不输出响应或凭据）");
        return result;
    }

    private void denied(String method, String path, String token, String body, int code) throws Exception {
        HttpResponse<byte[]> response = request(method, path, token,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8), body == null ? null : "application/json");
        assertEquals(code, JSON.parseObject(response.body()).getIntValue("code"), "权限或受保护操作检查失败：" + path);
    }

    private HttpResponse<byte[]> request(String method, String path, String token, byte[] body, String contentType) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9091" + path))
                .timeout(Duration.ofSeconds(45))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String token(JdbcTemplate jdbc, RedisConnection redis, Long userId) throws Exception {
        User user = jdbc.queryForObject("SELECT id,username,status FROM user WHERE id=?", (row, index) -> {
            assertEquals(1, row.getInt("status"), "测试身份必须是已有正常账号");
            User found = new User();
            found.setId(row.getLong("id"));
            found.setUsername(row.getString("username"));
            found.setStatus(UserStatus.OK);
            return found;
        }, userId);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        LoginUser login = new LoginUser(user, List.of(), List.of());
        login.setToken(uuid);
        login.setLoginTime(System.currentTimeMillis());
        login.setExpireTime(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
        byte[] key = (CacheConstants.LOGIN_TOKEN_KEY + uuid).getBytes(StandardCharsets.UTF_8);
        ownSessions.add(key);
        assertTrue(Boolean.TRUE.equals(redis.stringCommands().set(key,
                new FastJson2JsonRedisSerializer<>(LoginUser.class).serialize(login), Expiration.seconds(900), SET_IF_ABSENT)));
        Map<?, ?> application = yaml(new ClassPathResource("application.yml").getInputStream());
        String secret = Objects.toString(((Map<?, ?>) application.get("token")).get("secret"));
        return Jwts.builder().setClaims(Map.of(Constants.LOGIN_USER_KEY, uuid, Constants.JWT_USERNAME, user.getUsername()))
                .signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    private void browser() throws Exception {
        runBrowser("verify_file_management.cjs", "file-management-browser-console.log", Map.of(), 150);
    }

    private void setupSeaweedBrowser() throws Exception {
        runBrowser("configure_seaweed.cjs", "storage-admin-setup-console.log", localStorageEnvironment(), 240);
    }

    private void runBrowser(String scriptName, String outputName, Map<String, String> environment, int timeout) throws Exception {
        Path script = projectRoot.resolve("seaweedfs").resolve(scriptName);
        ProcessBuilder process = new ProcessBuilder(Objects.requireNonNull(System.getenv("YMCLOUD_BROWSER_NODE")), script.toString());
        process.directory(projectRoot.toFile());
        process.environment().putAll(environment);
        process.environment().put("YMCLOUD_BROWSER_TEST_TOKEN", admin);
        process.redirectErrorStream(true);
        Path output = projectRoot.resolve("target/test-output").resolve(outputName);
        Files.createDirectories(output.getParent());
        process.redirectOutput(output.toFile());
        Process browser = process.start();
        try {
            assertTrue(browser.waitFor(timeout, TimeUnit.SECONDS), "浏览器验证超时");
            assertEquals(0, browser.exitValue(), "浏览器验证失败：" + Files.readString(output, StandardCharsets.UTF_8));
        } finally {
            if (browser.isAlive()) {
                browser.destroyForcibly();
            }
        }
    }

    private Map<String, String> localStorageEnvironment() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(projectRoot.resolve("seaweedfs/.env"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] entry = line.split("=", 2);
            if (entry.length != 2 || !entry[0].matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalStateException("本地存储环境文件格式不正确");
            }
            if (entry[0].startsWith("YMCLOUD_S3_")) {
                result.put(entry[0], entry[1]);
            }
        }
        return result;
    }

    private List<Map<String, Object>> fileLocations(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT id,storage_profile,bucket,object_key,file_size,sha256,is_public,biz_type,biz_id "
                + "FROM file ORDER BY id");
    }

    private static Map<?, ?> yaml(InputStream input) throws Exception {
        try (input) {
            return new Yaml().load(input);
        }
    }

    private static Path projectRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(directory.resolve("ymcloud-web")) ? directory : directory.getParent();
    }

    private static DruidDataSource databaseSource(Map<?, ?> database) {
        DruidDataSource source = new DruidDataSource();
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
        return source;
    }
}

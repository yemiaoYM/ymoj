package com.ymcloud.web;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.core.storage.FileDigests;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实际 SeaweedFS + AWS SDK 测试。文件数据库为 mock；真实事务由 DatabaseIT 单独验证。
 * 显式 YMCLOUD_RUN_S3_TESTS=true 才运行，避免默认构建连接外部服务。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_S3_TESTS", matches = "true")
class StorageS3IntegrationTest {

    @TempDir
    Path root;

    @Test
    void actualSdkRoundtripAtUploadLimitAndConcurrentServicePerformance() throws Exception {
        StorageProperties properties = new StorageProperties();
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setType("s3");
        profile.setEndpoint(System.getenv("YMCLOUD_S3_ENDPOINT"));
        profile.setAccessKey(System.getenv("YMCLOUD_S3_ACCESS_KEY"));
        profile.setSecretKey(System.getenv("YMCLOUD_S3_SECRET_KEY"));
        properties.getProfiles().put("seaweedfs", profile);
        properties.setActive("seaweedfs");
        FileEntityService entities = mock(FileEntityService.class);
        AtomicLong ids = new AtomicLong();
        when(entities.save(any(File.class))).thenAnswer(invocation -> {
            invocation.<File>getArgument(0).setId(ids.incrementAndGet());
            return true;
        });
        StorageRegistry registry = new StorageRegistry(properties, root.toString());
        StoredFileService files = new StoredFileService(entities, registry);
        List<File> created = java.util.Collections.synchronizedList(new ArrayList<>());
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Path small = generate(root.resolve("small.bin"), 1024 * 1024);
            Path large = generate(root.resolve("large.bin"), 50 * 1024 * 1024);
            roundtrip(files, small, created);
            long largeStart = System.nanoTime();
            roundtrip(files, large, created);
            report.put("maxUploadBytes", Files.size(large));
            report.put("maxUploadRoundtripMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - largeStart));
            var executor = Executors.newFixedThreadPool(4);
            List<Long> durations = new ArrayList<>();
            long start = System.nanoTime();
            try {
                List<Callable<Long>> operations = new ArrayList<>();
                for (int index = 0; index < 16; index++) {
                    operations.add(() -> {
                        long operationStart = System.nanoTime();
                        roundtrip(files, small, created);
                        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStart);
                    });
                }
                for (var future : executor.invokeAll(operations, 120, TimeUnit.SECONDS)) {
                    assertFalse(future.isCancelled(), "S3 workload exceeded 120 seconds");
                    durations.add(future.get());
                }
            } finally {
                executor.shutdownNow();
            }
            durations.sort(Comparator.naturalOrder());
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            report.put("concurrency", 4);
            report.put("samples", durations.size());
            report.put("payloadBytes", Files.size(small));
            report.put("operations", "stage + SHA256 + MD5 + PUT + verification GET + consumer GET + SHA256");
            report.put("p50Ms", durations.get(durations.size() / 2));
            report.put("p95Ms", durations.get((int) Math.ceil(durations.size() * .95) - 1));
            report.put("roundtripsPerSecond", durations.size() / seconds);
            report.put("verifiedPayloadMiBPerSecond", durations.size() / seconds);
            report.put("errors", 0);
            report.put("database", "mock; timings exclude SQL and HTTP controller");
            Path output = Path.of("target/test-output/sdk-storage-performance.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, JSON.toJSONString(report), StandardCharsets.UTF_8);
            assertEquals(18, created.size());
        } finally {
            for (File file : created) {
                registry.client(file.getStorageProfile()).delete(file.getBucket(), file.getObjectKey());
            }
            registry.close();
        }
    }

    private void roundtrip(StoredFileService files, Path source, List<File> created) throws Exception {
        File file;
        try (InputStream input = Files.newInputStream(source)) {
            file = files.save(input, Files.size(source), "payload.bin", "application/octet-stream",
                    FileBizType.GENERAL, null, false);
        }
        created.add(file);
        assertEquals("ymcloud-private", file.getBucket());
        assertNull(file.getUrl());
        try (InputStream input = files.open(file)) {
            assertEquals(file.getSha256(), FileDigests.sha256(input));
        }
    }

    private Path generate(Path path, int size) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        new java.util.Random(173L).nextBytes(buffer);
        try (OutputStream output = Files.newOutputStream(path)) {
            for (int left = size; left > 0; left -= buffer.length) {
                output.write(buffer, 0, Math.min(left, buffer.length));
            }
        }
        return path;
    }
}

package com.ymcloud.judge.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 使用实际临时文件验证不可变缓存、并发下载、容量引用、安全路径与重启恢复
 */
class TestDataCacheTest {

    @TempDir
    Path directory;

    @Test
    void coldAndWarmCacheKeepOrderAndOriginalBytes() throws Exception {
        MemorySource source = source(7, "  first\r\n\n", "answer\n", "second", "");
        try (TestDataCache cache = cache(source)) {
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                assertEquals(List.of(1, 2), lease.entries().stream().map(TestDataCache.CachedCase::id).toList());
                assertArrayEquals(bytes("  first\r\n\n"), read(lease.entries().get(0).openInput()));
                assertArrayEquals(bytes(""), read(lease.entries().get(1).openOutput()));
            }
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                assertArrayEquals(bytes("second"), read(lease.entries().get(1).openInput()));
            }
            assertEquals(1, source.manifestReads.get());
            assertEquals(4, source.fileReads.get());
            assertEquals(1, cache.statistics().hits());
            assertEquals(1, cache.statistics().misses());
        }
    }

    /**
     * 同一题目的不同数据版本同时被引用时保持各自原始内容。
     * @throws Exception 缓存准备或读取失败
     */
    @Test
    void queuedOldVersionNeverReadsOrReplacesNewVersion() throws Exception {
        MemorySource source = source(7, "old\n", "answer");
        source.add(8, "new\n", "different");
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease old = cache.acquire(10L, 7L, source.hash(7));
             TestDataCache.Lease newer = cache.acquire(10L, 8L, source.hash(8))) {
            assertArrayEquals(bytes("old\n"), read(old.entries().get(0).openInput()));
            assertArrayEquals(bytes("new\n"), read(newer.entries().get(0).openInput()));
        }
    }

    @Test
    void concurrentRequestsMergeOneVersionDownload() throws Exception {
        MemorySource source = source(7, "input", "output");
        source.manifestDelayMillis = 100;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (TestDataCache cache = cache(source)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<byte[]>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                        return read(lease.entries().get(0).openInput());
                    }
                }));
            }
            start.countDown();
            for (Future<byte[]> result : results) {
                assertArrayEquals(bytes("input"), result.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, source.manifestReads.get());
            assertEquals(2, source.fileReads.get());
            assertEquals(7, cache.statistics().hits());
            assertEquals(0, cache.statistics().reservedBytes());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 大文件下载使用有界缓冲区，热缓存复用已校验文件而不重新下载。
     * @throws Exception 清单生成、缓存准备或流读取失败
     */
    @Test
    void largeVersionUsesBoundedStreamingBuffersAndWarmCacheAvoidsDownloads() throws Exception {
        long fileSize = 8L * 1024 * 1024;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] chunk = new byte[64 * 1024];
        Arrays.fill(chunk, (byte) 'x');
        for (long count = 0; count < fileSize; count += chunk.length) {
            digest.update(chunk);
        }
        String fileHash = HexFormat.of().formatHex(digest.digest());
        MemorySource manifestSource = source(7, "input", "output");
        TestDataManifest manifest = manifestSource.manifest(7);
        TestDataManifest.Entry entry = manifest.getEntries().get(0);
        entry.setInputSize(fileSize);
        entry.setOutputSize(fileSize);
        entry.setInputSha256(fileHash);
        entry.setOutputSha256(fileHash);
        manifestSource.store(7, manifest);
        AtomicInteger downloads = new AtomicInteger();
        TestDataSource source = new TestDataSource() {
            @Override
            public byte[] fetchManifest(Long problemId, Long versionId) throws IOException {
                return manifestSource.fetchManifest(problemId, versionId);
            }

            @Override
            public InputStream openFile(Long problemId, Long versionId, Long fileId) {
                downloads.incrementAndGet();
                return new InputStream() {
                    private long remaining = fileSize;

                    @Override
                    public int read() {
                        if (remaining == 0) {
                            return -1;
                        }
                        remaining--;
                        return 'x';
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) {
                        assertTrue(length <= 64 * 1024, "测试文件必须使用有界缓冲区读取");
                        if (remaining == 0) {
                            return -1;
                        }
                        int read = (int) Math.min(length, remaining);
                        Arrays.fill(buffer, offset, offset + read, (byte) 'x');
                        remaining -= read;
                        return read;
                    }

                    @Override
                    public byte[] readAllBytes() {
                        throw new AssertionError("不能一次读取整个测试数据文件");
                    }
                };
            }
        };
        try (TestDataCache cache = new TestDataCache(source, properties())) {
            try (TestDataCache.Lease ignored = cache.acquire(10L, 7L, manifestSource.hash(7))) {
                assertEquals(fileSize, Files.size(directory.resolve("p10-v7/701.data")));
            }
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, manifestSource.hash(7));
                 InputStream input = lease.entries().get(0).openInput()) {
                long total = 0;
                int read;
                while ((read = input.read(chunk)) != -1) {
                    total += read;
                }
                assertEquals(fileSize, total);
            }
            assertEquals(2, downloads.get());
            assertEquals(1, manifestSource.manifestReads.get());
            assertEquals(1, cache.statistics().hits());
        }
    }

    @Test
    void failedIntegrityCheckNeverPublishesAndRetryDownloadsFreshFiles() throws Exception {
        MemorySource source = source(7, "input", "output");
        source.payloads.put(701L, bytes("wrong"));
        try (TestDataCache cache = cache(source)) {
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            assertFalse(Files.exists(directory.resolve("p10-v7")));
            assertEquals(0, cache.statistics().reservedBytes());
            assertEquals(0, cache.statistics().readyBytes());
            assertFalse(temporaryDirectoriesExist());
            source.payloads.put(701L, bytes("input"));
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            }
            assertEquals(2, source.manifestReads.get());
            assertEquals(1, cache.statistics().downloadFailures());
            assertTrue(cache.statistics().verificationFailures() >= 1);
        }
    }

    @Test
    void oversizedAndTruncatedFilesAreRejected() throws Exception {
        for (String invalid : List.of("too much data", "x")) {
            MemorySource source = source(7, "input", "output");
            source.payloads.put(701L, bytes(invalid));
            try (TestDataCache cache = cache(source)) {
                assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
                assertFalse(Files.exists(directory.resolve("p10-v7")));
            }
        }
    }

    @Test
    void badManifestHashProblemVersionAndFileMetadataFailBeforeDownload() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source)) {
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, "0".repeat(64)));
            assertThrows(IOException.class, () -> cache.acquire(11L, 7L, source.hash(7)));
            TestDataManifest manifest = source.manifest(7);
            manifest.getEntries().get(0).setInputFileId(0L);
            source.store(7, manifest);
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            assertEquals(0, source.fileReads.get());
            assertEquals(0, cache.statistics().readyBytes());
        }
    }

    @Test
    void traversalDuplicateCaseAndUnpairedLogicalPathsAreRejected() throws Exception {
        MemorySource source = source(7, "input", "output", "two", "two");
        byte[] original = source.manifests.get(7L);
        try (TestDataCache cache = cache(source)) {
            for (String unsafe : List.of("/in/../a.in", "/in/a\\b.in", "/in/a\n.in", "/in/.in")) {
                source.manifests.put(7L, original);
                TestDataManifest manifest = source.manifest(7);
                manifest.getEntries().get(0).setInput(unsafe);
                source.store(7, manifest);
                assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            }
            source.manifests.put(7L, original);
            TestDataManifest manifest = source.manifest(7);
            manifest.getEntries().get(1).setId(1);
            source.store(7, manifest);
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            source.manifests.put(7L, original);
            manifest = source.manifest(7);
            manifest.getEntries().get(0).setOutput("/out/other.out");
            source.store(7, manifest);
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            assertEquals(0, source.fileReads.get());
        }
    }

    /**
     * 合法逻辑文件名不参与磁盘寻址，缓存文件仅按服务端文件 ID 命名。
     * @throws Exception 清单修改或缓存准备失败
     */
    @Test
    void legalWebFilenamesWithMultipleDotsUseOnlyFileIdsOnDisk() throws Exception {
        MemorySource source = source(7, "input", "output");
        TestDataManifest manifest = source.manifest(7);
        manifest.getEntries().get(0).setInput("/in/case..in");
        manifest.getEntries().get(0).setOutput("/out/case..out");
        source.store(7, manifest);
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            TestDataCache.CachedCase testCase = lease.entries().get(0);
            assertTrue(Files.isRegularFile(directory.resolve("p10-v7/701.data")));
            assertTrue(Files.isRegularFile(directory.resolve("p10-v7/702.data")));
            assertArrayEquals(bytes("input"), read(testCase.openInput()));
        }
    }

    @Test
    void duplicateJsonKeysFractionalIdsAndTrailingJsonAreRejected() throws Exception {
        MemorySource source = source(7, "input", "output");
        String original = new String(source.manifests.get(7L), StandardCharsets.UTF_8);
        try (TestDataCache cache = cache(source)) {
            for (String invalid : List.of(original.replace("\"problemId\":10", "\"problemId\":11,\"problemId\":10"),
                    original.replace("\"problemId\":10", "\"problemId\":10.1"), original + " {}")) {
                source.manifests.put(7L, bytes(invalid));
                assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            }
            assertEquals(0, source.fileReads.get());
        }
    }

    /**
     * 无活动租约的缓存文件损坏后重新下载，避免复用错误输入。
     * @throws Exception 缓存准备、故障注入或读取失败
     */
    @Test
    void damagedWarmCacheDownloadsAgainAfterLeaseRelease() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source)) {
            Path input;
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                input = directory.resolve("p10-v7/701.data");
            }
            Files.write(input, bytes("wrong"));
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            }
            assertEquals(2, source.manifestReads.get());
            assertEquals(1, cache.statistics().versions());
        }
    }

    @Test
    void incorrectTaskHashCannotRemoveValidCachedVersion() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source)) {
            try (TestDataCache.Lease ignored = cache.acquire(10L, 7L, source.hash(7))) {
                assertEquals(1, cache.statistics().versions());
            }
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, "0".repeat(64)));
            assertTrue(Files.exists(directory.resolve("p10-v7")));
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
                assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            }
            assertEquals(1, source.manifestReads.get());
        }
    }

    /**
     * 运行中的缓存损坏不删除在用目录，读取仍执行完整性校验。
     * @throws Exception 缓存准备或故障注入失败
     */
    @Test
    void damagedRunningCacheIsNotDeletedAndRuntimeStreamChecksHash() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            TestDataCache.CachedCase testCase = lease.entries().get(0);
            Path input = directory.resolve("p10-v7/701.data");
            Files.write(input, bytes("wrong"));
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            assertTrue(Files.exists(input));
            assertThrows(IOException.class, () -> read(testCase.openInput()));
            assertEquals(1, source.manifestReads.get());
        }
    }

    /**
     * 提前关闭标准答案流仍核验未读部分，不能绕过完整性检查。
     * @throws Exception 缓存准备或故障注入失败
     */
    @Test
    void closingPartialStreamStillVerifiesEntireFile() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            TestDataCache.CachedCase testCase = lease.entries().get(0);
            Files.write(directory.resolve("p10-v7/702.data"), bytes("wrong!"));
            InputStream output = testCase.openOutput();
            output.read();
            assertThrows(IOException.class, output::close);
        }
    }

    @Test
    void capacityEvictsOnlyIdleVersionsAndReleasedLeaseCannotRead() throws Exception {
        MemorySource source = source(7, "input", "output");
        source.add(8, "newer", "answer");
        TestDataCacheProperties properties = properties();
        properties.setMaxBytes(Math.max(source.versionBytes(7), source.versionBytes(8)));
        try (TestDataCache cache = new TestDataCache(source, properties)) {
            TestDataCache.Lease old = cache.acquire(10L, 7L, source.hash(7));
            TestDataCache.CachedCase oldCase = old.entries().get(0);
            assertThrows(IOException.class, () -> cache.acquire(10L, 8L, source.hash(8)));
            assertArrayEquals(bytes("input"), read(oldCase.openInput()));
            old.close();
            old.close();
            assertThrows(IOException.class, old::entries);
            assertThrows(IOException.class, oldCase::openInput);
            try (TestDataCache.Lease newer = cache.acquire(10L, 8L, source.hash(8))) {
                assertArrayEquals(bytes("newer"), read(newer.entries().get(0).openInput()));
                assertFalse(Files.exists(directory.resolve("p10-v7")));
            }
            assertEquals(1, cache.statistics().versions());
        }
    }

    @Test
    void simultaneousDownloadsReserveCapacityBeforeWriting() throws Exception {
        MemorySource source = source(7, "input", "output");
        source.add(8, "newer", "answer");
        source.fileEntered = new CountDownLatch(1);
        source.releaseFile = new CountDownLatch(1);
        TestDataCacheProperties properties = properties();
        properties.setMaxBytes(Math.max(source.versionBytes(7), source.versionBytes(8)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TestDataCache cache = new TestDataCache(source, properties)) {
            Future<TestDataCache.Lease> first = executor.submit(() -> cache.acquire(10L, 7L, source.hash(7)));
            assertTrue(source.fileEntered.await(5, TimeUnit.SECONDS));
            assertEquals(source.versionBytes(7), cache.statistics().reservedBytes());
            assertThrows(IOException.class, () -> cache.acquire(10L, 8L, source.hash(8)));
            source.releaseFile.countDown();
            try (TestDataCache.Lease lease = first.get(5, TimeUnit.SECONDS)) {
                assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            }
            assertEquals(0, cache.statistics().reservedBytes());
        } finally {
            source.releaseFile.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void openStreamPinsVersionEvenIfLeaseClosesEarly() throws Exception {
        MemorySource source = source(7, "input", "output");
        source.add(8, "newer", "answer");
        TestDataCacheProperties properties = properties();
        properties.setMaxBytes(Math.max(source.versionBytes(7), source.versionBytes(8)));
        try (TestDataCache cache = new TestDataCache(source, properties)) {
            TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7));
            try (InputStream input = lease.entries().get(0).openInput()) {
                lease.close();
                assertThrows(IOException.class, () -> cache.acquire(10L, 8L, source.hash(8)));
                assertTrue(Files.exists(directory.resolve("p10-v7")));
                assertArrayEquals(bytes("input"), input.readAllBytes());
            }
            try (TestDataCache.Lease newer = cache.acquire(10L, 8L, source.hash(8))) {
                assertArrayEquals(bytes("newer"), read(newer.entries().get(0).openInput()));
            }
        }
    }

    @Test
    void restartReusesCompleteCacheAndRemovesAbandonedDownloadsOnly() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease ignored = cache.acquire(10L, 7L, source.hash(7))) {
            assertEquals(1, cache.statistics().versions());
        }
        Path abandoned = Files.createDirectory(directory.resolve(".download-" + UUID.randomUUID()));
        Files.write(abandoned.resolve("unfinished.data"), bytes("not trusted"));
        Path untouched = directory.resolve("unrelated.txt");
        Files.write(untouched, bytes("unrelated"));
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            assertEquals(1, source.manifestReads.get());
            assertFalse(Files.exists(abandoned));
            assertTrue(Files.exists(untouched));
            assertEquals(1, cache.statistics().hits());
        }
    }

    /**
     * 重启恢复缓存时收紧目录及文件权限，避免其他本机用户读取隐藏数据。
     * @throws Exception 缓存准备或 POSIX 权限操作失败
     */
    @Test
    void restartTightensPermissionsOfRecoveredVersionAndItsFiles() throws Exception {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "当前平台不提供 POSIX 文件权限");
        MemorySource source = source(7, "input", "output");
        Path version;
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            version = directory.resolve("p10-v7");
        }
        Files.setPosixFilePermissions(version, PosixFilePermissions.fromString("rwxr-xr-x"));
        try (var paths = Files.list(version)) {
            for (Path path : paths.toList()) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
            }
        }
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            assertEquals(1, source.manifestReads.get());
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(version));
            try (var paths = Files.list(version)) {
                for (Path path : paths.toList()) {
                    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path));
                }
            }
        }
    }

    /**
     * 重启丢弃损坏文件，并继续使用任务固定的清单摘要验证缓存。
     * @throws Exception 缓存准备或故障注入失败
     */
    @Test
    void restartDropsDamagedCacheAndValidatesPinnedHashBeforeReuse() throws Exception {
        MemorySource source = source(7, "input", "output");
        Path input;
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            input = directory.resolve("p10-v7/701.data");
        }
        Files.write(input, bytes("wrong"));
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            assertEquals(2, source.manifestReads.get());
        }
        TestDataManifest manifest = source.manifest(7);
        manifest.getEntries().get(0).setInput("/in/changed.in");
        manifest.getEntries().get(0).setOutput("/out/changed.out");
        byte[] changed = source.mapper.writeValueAsBytes(manifest);
        Files.write(directory.resolve("p10-v7/manifest.json"), changed);
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            assertEquals(3, source.manifestReads.get());
        }
    }

    @Test
    void secondProcessCannotShareCacheAndShutdownKeepsActiveLease() throws Exception {
        MemorySource source = source(7, "input", "output");
        TestDataCache first = cache(source);
        TestDataCache.Lease lease = first.acquire(10L, 7L, source.hash(7));
        assertThrows(IOException.class, () -> cache(source));
        first.close();
        assertThrows(IOException.class, () -> first.acquire(10L, 7L, source.hash(7)));
        assertThrows(IOException.class, () -> cache(source));
        assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
        lease.close();
        try (TestDataCache second = cache(source);
             TestDataCache.Lease other = second.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(other.entries().get(0).openInput()));
        }
    }

    @Test
    void manifestFileVersionAndCaseLimitsAreEnforced() throws Exception {
        MemorySource source = source(7, "input", "output", "two", "two");
        for (int limit = 0; limit < 4; limit++) {
            TestDataCacheProperties properties = properties();
            if (limit == 0) {
                properties.setMaxManifestBytes(1);
            } else if (limit == 1) {
                properties.setMaxFileBytes(1);
            } else if (limit == 2) {
                properties.setMaxVersionBytes(source.versionBytes(7) - 1);
            } else {
                properties.setMaxCases(1);
            }
            try (TestDataCache cache = new TestDataCache(source, properties)) {
                assertThrows(IOException.class, () -> cache.acquire(10L, 7L, source.hash(7)));
            }
        }
        assertEquals(0, source.fileReads.get());
    }

    @Test
    void invalidIdsHashAndCacheConfigurationAreRejected() throws Exception {
        MemorySource source = source(7, "input", "output");
        try (TestDataCache cache = cache(source)) {
            assertThrows(IOException.class, () -> cache.acquire(-1L, 7L, source.hash(7)));
            assertThrows(IOException.class, () -> cache.acquire(10L, null, source.hash(7)));
            assertThrows(IOException.class, () -> cache.acquire(10L, 7L, "not a hash"));
            assertEquals(0, source.manifestReads.get());
        }
        TestDataCacheProperties invalid = properties();
        invalid.setMaxBytes(0);
        assertThrows(IllegalArgumentException.class, () -> new TestDataCache(source, invalid));
        TestDataCacheProperties tooManyCases = properties();
        tooManyCases.setMaxCases(10_001);
        assertThrows(IllegalArgumentException.class, () -> new TestDataCache(source, tooManyCases));
    }

    /**
     * 拒绝缓存根目录和数据文件符号链接，恢复时保留链接外部目标。
     * @throws Exception 测试文件准备或缓存恢复失败
     */
    @Test
    void symbolicLinksCannotExposeOrDeleteOutsideFiles() throws Exception {
        MemorySource source = source(7, "input", "output");
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path outsideFile = outside.resolve("untouched.data");
        Files.write(outsideFile, bytes("outside secret"));
        Path linked = directory.resolve("linked-cache");
        try {
            Files.createSymbolicLink(linked, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "当前平台不支持创建测试符号链接");
        }
        TestDataCacheProperties linkedProperties = properties();
        linkedProperties.setDirectory(linked.toString());
        assertThrows(IOException.class, () -> new TestDataCache(source, linkedProperties));
        Path cachedFile;
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            cachedFile = directory.resolve("p10-v7/701.data");
        }
        Files.delete(cachedFile);
        Files.createSymbolicLink(cachedFile, outsideFile);
        try (TestDataCache cache = cache(source);
             TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash(7))) {
            assertArrayEquals(bytes("input"), read(lease.entries().get(0).openInput()));
            assertArrayEquals(bytes("outside secret"), Files.readAllBytes(outsideFile));
        }
    }

    private TestDataCache cache(MemorySource source) throws IOException {
        return new TestDataCache(source, properties());
    }

    private TestDataCacheProperties properties() {
        TestDataCacheProperties properties = new TestDataCacheProperties();
        properties.setDirectory(directory.toString());
        return properties;
    }

    private boolean temporaryDirectoriesExist() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith(".download-"));
        }
    }

    private static MemorySource source(long version, String... content) throws Exception {
        MemorySource source = new MemorySource();
        source.add(version, content);
        return source;
    }

    private static byte[] read(InputStream input) throws IOException {
        try (input) {
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class MemorySource implements TestDataSource {

        private final ObjectMapper mapper = new ObjectMapper();
        private final Map<Long, byte[]> manifests = new ConcurrentHashMap<>();
        private final Map<Long, byte[]> payloads = new ConcurrentHashMap<>();
        private final AtomicInteger manifestReads = new AtomicInteger();
        private final AtomicInteger fileReads = new AtomicInteger();
        private long manifestDelayMillis;
        private CountDownLatch fileEntered;
        private CountDownLatch releaseFile;

        private void add(long version, String... content) throws Exception {
            TestDataManifest manifest = new TestDataManifest();
            manifest.setProblemId(10L);
            manifest.setVersionId(version);
            List<TestDataManifest.Entry> entries = new ArrayList<>();
            for (int index = 0; index < content.length; index += 2) {
                int id = index / 2 + 1;
                long inputId = version * 100 + index + 1;
                long outputId = inputId + 1;
                byte[] input = bytes(content[index]);
                byte[] output = bytes(content[index + 1]);
                payloads.put(inputId, input);
                payloads.put(outputId, output);
                TestDataManifest.Entry entry = new TestDataManifest.Entry();
                entry.setId(id);
                entry.setInput("/in/" + String.format("%02d", id) + ".in");
                entry.setOutput("/out/" + String.format("%02d", id) + ".out");
                entry.setInputFileId(inputId);
                entry.setOutputFileId(outputId);
                entry.setInputSize((long) input.length);
                entry.setOutputSize((long) output.length);
                entry.setInputSha256(TestDataCacheTest.hash(input));
                entry.setOutputSha256(TestDataCacheTest.hash(output));
                entries.add(entry);
            }
            manifest.setEntries(entries);
            store(version, manifest);
        }

        private void store(long version, TestDataManifest manifest) throws Exception {
            manifests.put(version, mapper.writeValueAsBytes(manifest));
        }

        private TestDataManifest manifest(long version) throws Exception {
            return mapper.readValue(manifests.get(version), TestDataManifest.class);
        }

        private String hash(long version) throws Exception {
            return TestDataCacheTest.hash(manifests.get(version));
        }

        private long versionBytes(long version) throws Exception {
            TestDataManifest manifest = manifest(version);
            return manifests.get(version).length + 64L + manifest.getEntries().stream()
                    .mapToLong(entry -> entry.getInputSize() + entry.getOutputSize())
                    .sum();
        }

        @Override
        public byte[] fetchManifest(Long problemId, Long versionId) throws IOException {
            manifestReads.incrementAndGet();
            if (manifestDelayMillis > 0) {
                try {
                    Thread.sleep(manifestDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            return manifests.get(versionId);
        }

        @Override
        public InputStream openFile(Long problemId, Long versionId, Long fileId) throws IOException {
            fileReads.incrementAndGet();
            if (fileEntered != null) {
                fileEntered.countDown();
                try {
                    if (!releaseFile.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("测试下载未释放");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            return new ByteArrayInputStream(payloads.get(fileId));
        }
    }
}

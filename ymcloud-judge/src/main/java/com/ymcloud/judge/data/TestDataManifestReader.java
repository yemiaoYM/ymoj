package com.ymcloud.judge.data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ymcloud.pojo.contract.judge.TestDataManifest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将受限版本清单解析为经过校验的不可变描述，不处理缓存目录或并发状态。
 */
final class TestDataManifestReader {

    private final ObjectMapper mapper;
    private final int maxManifestBytes;
    private final long maxVersionBytes;
    private final long maxFileBytes;
    private final int maxCases;

    TestDataManifestReader(TestDataCacheProperties properties) {
        this.maxManifestBytes = properties.getMaxManifestBytes();
        this.maxVersionBytes = properties.getMaxVersionBytes();
        this.maxFileBytes = properties.getMaxFileBytes();
        this.maxCases = properties.getMaxCases();
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(16)
                        .maxStringLength(1024)
                        .maxNumberLength(20)
                        .build())
                .build();
        this.mapper = JsonMapper.builder(factory)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    VerifiedManifest read(Long problemId, Long versionId, byte[] raw, String expectedHash) throws IOException {
        if (raw == null || raw.length == 0 || raw.length > maxManifestBytes
                || !expectedHash.equals(hash(raw))) {
            throw new IOException("测试数据清单大小或 SHA-256 校验失败");
        }
        TestDataManifest manifest = mapper.readValue(raw, TestDataManifest.class);
        if (manifest == null || !Objects.equals(problemId, manifest.getProblemId())
                || !Objects.equals(versionId, manifest.getVersionId())
                || manifest.getEntries() == null || manifest.getEntries().isEmpty()
                || manifest.getEntries().size() > maxCases) {
            throw new IOException("测试数据清单与任务的题目或版本不一致");
        }
        Map<Long, FileSpec> files = new LinkedHashMap<>();
        List<CaseSpec> cases = new ArrayList<>();
        String previous = null;
        long bytes = raw.length + 64L;
        for (int index = 0; index < manifest.getEntries().size(); index++) {
            TestDataManifest.Entry entry = manifest.getEntries().get(index);
            if (entry == null || !Integer.valueOf(index + 1).equals(entry.getId())) {
                throw new IOException("测试数据清单的测试点顺序无效");
            }
            String input = logicalName(entry.getInput(), "/in/", ".in");
            String output = logicalName(entry.getOutput(), "/out/", ".out");
            if (!input.equals(output) || (previous != null && previous.compareTo(entry.getInput()) >= 0)) {
                throw new IOException("测试数据清单的文件配对或顺序无效");
            }
            previous = entry.getInput();
            FileSpec inputFile = fileSpec(entry.getInputFileId(), entry.getInputSize(), entry.getInputSha256());
            FileSpec outputFile = fileSpec(entry.getOutputFileId(), entry.getOutputSize(), entry.getOutputSha256());
            bytes = addFile(files, inputFile, bytes);
            bytes = addFile(files, outputFile, bytes);
            if (bytes > maxVersionBytes) {
                throw new IOException("测试数据版本超过 Worker 单版本缓存限制");
            }
            cases.add(new CaseSpec(entry.getId(), inputFile, outputFile));
        }
        return new VerifiedManifest(bytes, Collections.unmodifiableMap(files), List.copyOf(cases));
    }

    private FileSpec fileSpec(Long id, Long size, String sha256) throws IOException {
        if (id == null || id <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("测试数据文件 ID 或 SHA-256 无效");
        }
        if (size == null || size < 0 || size > maxFileBytes) {
            throw new IOException("测试数据文件大小无效或超过限制");
        }
        return new FileSpec(id, size, sha256);
    }

    private static long addFile(Map<Long, FileSpec> files, FileSpec file, long bytes) throws IOException {
        FileSpec previous = files.putIfAbsent(file.id(), file);
        if (previous != null && !previous.equals(file)) {
            throw new IOException("同一文件 ID 存在不一致的测试数据元信息");
        }
        try {
            return previous == null ? Math.addExact(bytes, file.size()) : bytes;
        } catch (ArithmeticException e) {
            throw new IOException("测试数据清单声明的大小溢出", e);
        }
    }

    private static String logicalName(String path, String prefix, String extension) throws IOException {
        if (path == null || !path.startsWith(prefix) || !path.endsWith(extension)) {
            throw new IOException("测试数据逻辑路径无效");
        }
        String name = path.substring(prefix.length());
        if (name.length() <= extension.length() || name.length() > 200 || name.contains("/")
                || name.contains("\\") || name.contains(":")
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("测试数据逻辑文件名无效");
        }
        String stem = name.substring(0, name.length() - extension.length());
        if (stem.isBlank() || ".".equals(stem) || "..".equals(stem)) {
            throw new IOException("测试数据逻辑文件名无效");
        }
        return stem;
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    record VerifiedManifest(long bytes, Map<Long, FileSpec> files, List<CaseSpec> cases) {
    }

    record FileSpec(Long id, long size, String sha256) {

        String filename() {
            return id + ".data";
        }
    }

    record CaseSpec(int id, FileSpec input, FileSpec output) {
    }
}

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
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 将受限版本清单解析为经过校验的不可变描述，不处理缓存目录或并发状态。
 */
final class TestDataManifestReader {

    private final ObjectMapper mapper;
    private final int maxManifestBytes;
    private final long maxVersionBytes;
    private final long maxFileBytes;
    private final int maxCases;

    /**
     * 固定解析预算并创建严格 JSON 映射器。
     * @param properties 缓存容量及测试点限制
     */
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

    /**
     * 核对原始摘要、版本身份、顺序和文件配对。
     * @param problemId 任务题目 ID
     * @param versionId 任务版本 ID
     * @param raw 有界原始清单
     * @param expectedHash 任务 SHA-256
     * @return 只含已验证字段的不可变清单
     * @throws IOException 清单格式、容量或完整性非法
     */
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

    /**
     * 校验单个文件的 ID、大小与摘要。
     * @param id 正数文件 ID
     * @param size 非负字节数
     * @param sha256 小写 SHA-256
     * @return 已验证文件描述
     * @throws IOException 元数据非法或超过容量
     */
    private FileSpec fileSpec(Long id, Long size, String sha256) throws IOException {
        if (id == null || id <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("测试数据文件 ID 或 SHA-256 无效");
        }
        if (size == null || size < 0 || size > maxFileBytes) {
            throw new IOException("测试数据文件大小无效或超过限制");
        }
        return new FileSpec(id, size, sha256);
    }

    /**
     * 去重累计容量，同一文件 ID 必须对应相同内容。
     * @param files 文件索引，原地更新
     * @param file 待加入描述
     * @param bytes 已累计字节数
     * @return 去重后的总字节数
     * @throws IOException 同 ID 元数据冲突或总量溢出
     */
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

    /**
     * 验证输入和输出的逻辑文件名，不作为真实路径使用。
     * @param path 逻辑路径
     * @param prefix 固定方向前缀
     * @param extension 固定扩展名
     * @return 去掉前缀和扩展名的名称
     * @throws IOException 格式非法或包含目录片段
     */
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

    /**
     * 计算原始清单摘要。
     * @param value 原始清单字节
     * @return 小写 SHA-256
     */
    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    record VerifiedManifest(long bytes, Map<Long, FileSpec> files, List<CaseSpec> cases) {}

    record FileSpec(Long id, long size, String sha256) {

        /**
         * 以文件 ID 生成缓存文件名，避免外部路径进入文件系统。
         * @return 固定后缀的本地文件名
         */
        String filename() {
            return id + ".data";
        }
    }

    record CaseSpec(int id, FileSpec input, FileSpec output) {}
}

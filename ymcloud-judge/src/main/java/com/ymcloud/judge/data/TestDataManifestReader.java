package com.ymcloud.judge.data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ymcloud.pojo.bo.problem.ProblemCaseManifest;
import lombok.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 将受限版本清单解析为经过校验的不可变描述，不处理缓存目录或并发状态
 */
final class TestDataManifestReader {

    private final ObjectMapper mapper;
    private final int maxManifestBytes;
    private final long maxVersionBytes;
    private final long maxFileBytes;
    private final int maxCases;

    /**
     * 固定解析预算并创建严格 JSON 映射器
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
                // 旧清单的 fileId 使用十进制字符串，已发布的数据和 Worker 缓存仍需可读
                .withCoercionConfig(Long.class, config -> config.setCoercion(CoercionInputShape.String, CoercionAction.TryConvert))
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /**
     * 校验清单大小、文件配对和执行顺序
     * @param raw 服务端返回的原始清单
     * @return 当前执行持有的不可变测试点视图
     * @throws IOException 清单为空、非法或超过容量
     */
    VerifiedManifest read(byte[] raw) throws IOException {
        if (raw == null || raw.length == 0 || raw.length > maxManifestBytes) {
            throw new IOException("测试清单大小无效");
        }
        ProblemCaseManifest manifest = mapper.readValue(raw, ProblemCaseManifest.class);
        if (manifest == null || manifest.getCases() == null || manifest.getCases().isEmpty()
                || manifest.getCases().size() > maxCases) {
            throw new IOException("题目没有可执行的测试点或数量超过限制");
        }
        Map<Long, FileSpec> files = new LinkedHashMap<>();
        List<CaseSpec> cases = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long bytes = raw.length;
        for (int index = 0; index < manifest.getCases().size(); index++) {
            ProblemCaseManifest.Entry entry = manifest.getCases().get(index);
            if (entry == null || entry.getInput() == null || entry.getOutput() == null) {
                throw new IOException("测试文件未配对");
            }
            String name = entry.getName();
            if (name == null || name.isBlank() || name.length() > 196 || name.equals(".") || name.equals("..")
                    || name.contains("/") || name.contains("\\") || name.contains(":")
                    || name.chars().anyMatch(Character::isISOControl)) {
                throw new IOException("测试点名称无效");
            }
            if (!names.add(name)) {
                throw new IOException("测试点名称重复");
            }
            FileSpec inputFile = fileSpec(entry.getInput());
            FileSpec outputFile = fileSpec(entry.getOutput());
            bytes = addFile(files, inputFile, bytes);
            bytes = addFile(files, outputFile, bytes);
            if (bytes > maxVersionBytes) {
                throw new IOException("测试数据版本超过 Worker 单版本缓存限制");
            }
            cases.add(new CaseSpec(index + 1, inputFile, outputFile));
        }
        return new VerifiedManifest(bytes, Collections.unmodifiableMap(files), List.copyOf(cases));
    }

    /**
     * 校验单个文件的 ID、大小与摘要
     * @param reference 清单中的不可变文件引用
     * @return 已验证文件描述
     * @throws IOException 元数据非法或超过容量
     */
    private FileSpec fileSpec(ProblemCaseManifest.FileRef reference) throws IOException {
        Long id = reference.getFileId();
        Integer size = reference.getSize();
        String sha256 = reference.getSha256();
        if (id == null || id <= 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("测试数据文件 ID 或 SHA-256 无效");
        }
        if (size == null || size < 0 || size > maxFileBytes) {
            throw new IOException("测试数据文件大小无效或超过限制");
        }
        return new FileSpec(id, size, sha256);
    }

    /**
     * 去重累计容量，同一文件 ID 必须对应相同内容
     * @param files 文件索引，原地更新
     * @param file 待加入描述
     * @param bytes 已累计字节数
     * @return 去重后的总字节数
     * @throws IOException 同 ID 元数据冲突或总量溢出
     */
    private static long addFile(Map<Long, FileSpec> files, FileSpec file, long bytes) throws IOException {
        FileSpec previous = files.putIfAbsent(file.getId(), file);
        if (previous != null && !previous.equals(file)) {
            throw new IOException("同一文件 ID 存在不一致的测试数据元信息");
        }
        try {
            return previous == null ? Math.addExact(bytes, file.getSize()) : bytes;
        } catch (ArithmeticException e) {
            throw new IOException("测试数据清单声明的大小溢出", e);
        }
    }

    @Value
    static class VerifiedManifest {

        /**
         * 清单原文和去重后的文件合计占用的字节数
         */
        long bytes;

        /**
         * 按不可变文件主键索引的文件描述
         */
        Map<Long, FileSpec> files;

        /**
         * 按本次执行顺序排列的测试点
         */
        List<CaseSpec> cases;
    }

    @Value
    static class FileSpec {

        /**
         * 清单引用的测试文件主键
         */
        Long id;

        /**
         * 用于校验下载内容的原始字节数
         */
        long size;

        /**
         * 用于校验下载内容的 SHA-256 十六进制摘要
         */
        String sha256;

        /**
         * 以文件 ID 生成缓存文件名，避免外部路径进入文件系统
         * @return 固定后缀的本地文件名
         */
        String filename() {
            return id + ".data";
        }
    }

    @Value
    static class CaseSpec {

        /**
         * 测试点在本次执行中的序号，从 1 开始
         */
        int id;

        /**
         * 本次执行读取的输入文件
         */
        FileSpec input;

        /**
         * 本次执行用于比对的预期输出文件
         */
        FileSpec output;
    }
}

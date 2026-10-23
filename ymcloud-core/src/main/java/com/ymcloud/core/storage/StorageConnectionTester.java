package com.ymcloud.core.storage;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.pojo.entity.File;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-11
 * @Description: 随机对象连接测试与历史对象只读校验，不返回远端异常内容。
 */
@Component
public class StorageConnectionTester {

    private final StorageRegistry registry;

    public StorageConnectionTester(StorageRegistry registry) {
        this.registry = registry;
    }

    /** 全量验证历史引用后才允许恢复配置；绝不上传、覆盖或删除历史对象。 */
    public void verifyReferencedFiles(StorageProperties.Profile profile, List<File> files) {
        StorageConfigValidation.validate(profile, true);
        ObjectStorageService driver = null;
        boolean passed = false;
        boolean closed = true;
        try {
            if (files == null || files.isEmpty() || "local".equals(profile.getType())) {
                throw new IllegalArgumentException();
            }
            for (File file : files) {
                validateReference(profile, file);
            }
            driver = registry.createClient(profile);
            byte[] buffer = new byte[64 * 1024];
            for (File file : files) {
                MessageDigest digest = FileDigests.digest();
                long size = 0;
                try (InputStream input = driver.open(file.getBucket(), file.getObjectKey())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > file.getFileSize() - size) {
                            throw new IllegalArgumentException();
                        }
                        size += read;
                        digest.update(buffer, 0, read);
                    }
                }
                if (size != file.getFileSize() || !file.getSha256().equalsIgnoreCase(
                        HexFormat.of().formatHex(digest.digest()))) {
                    throw new IllegalArgumentException();
                }
            }
            passed = true;
        } catch (RuntimeException | IOException e) {
            passed = false;
        } finally {
            if (driver != null) {
                try {
                    driver.close();
                } catch (RuntimeException e) {
                    closed = false;
                }
            }
        }
        if (!passed || !closed) {
            throw new FileException("已有文件完整性校验失败，未恢复存储配置；请检查存储桶、对象及历史大小和哈希", HttpStatus.BAD_REQUEST);
        }
    }

    public void test(StorageProperties.Profile profile) {
        StorageConfigValidation.validate(profile, true);
        Path staged = null;
        ObjectStorageService driver = null;
        String key = ".ymcloud-probe/" + UUID.randomUUID();
        List<String> attemptedBuckets = new ArrayList<>();
        boolean passed = false;
        boolean cleaned = true;
        try {
            byte[] bytes = new byte[1024];
            new SecureRandom().nextBytes(bytes);
            String hash = HexFormat.of().formatHex(FileDigests.digest().digest(bytes));
            staged = Files.createTempFile("ymcloud-storage-probe-", ".bin");
            Files.write(staged, bytes);
            driver = registry.createClient(profile);
            List<String> buckets = "local".equals(profile.getType()) ? List.of(FileConstants.DIR_PUBLIC, FileConstants.DIR_PRIVATE)
                    : List.of(profile.getPublicBucket(), profile.getPrivateBucket());
            for (String bucket : buckets) {
                // 即使 put 的响应丢失也补偿，不能只清理确认上传成功的对象。
                attemptedBuckets.add(bucket);
                driver.put(bucket, key, staged, "application/octet-stream", hash);
                try (InputStream input = driver.open(bucket, key)) {
                    byte[] result = input.readNBytes(bytes.length + 1);
                    if (result.length != bytes.length || !hash.equals(HexFormat.of().formatHex(
                            FileDigests.digest().digest(result)))) {
                        throw new FileException("连接测试对象校验失败");
                    }
                }
            }
            passed = true;
        } catch (RuntimeException | IOException e) {
            passed = false;
        } finally {
            if (driver != null) {
                for (String bucket : attemptedBuckets) {
                    try {
                        driver.delete(bucket, key);
                    } catch (RuntimeException e) {
                        cleaned = false;
                    }
                }
                try {
                    driver.close();
                } catch (RuntimeException e) {
                    cleaned = false;
                }
            }
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException e) {
                    cleaned = false;
                }
            }
        }
        if (!passed || !cleaned) {
            throw new FileException("存储连接测试失败，请检查连接、凭据及双桶读写删除权限", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateReference(StorageProperties.Profile profile, File file) {
        if (file == null || file.getIsPublic() == null || (file.getIsPublic() != FileConstants.ACCESS_PRIVATE
                && file.getIsPublic() != FileConstants.ACCESS_PUBLIC)
                || file.getFileSize() == null || file.getFileSize() < 0 || file.getSha256() == null
                || !file.getSha256().matches("[a-fA-F0-9]{64}") || file.getObjectKey() == null
                || file.getObjectKey().isBlank()
                || file.getObjectKey().chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException();
        }
        String expectedBucket = file.getIsPublic() == FileConstants.ACCESS_PUBLIC ? profile.getPublicBucket() : profile.getPrivateBucket();
        if (!expectedBucket.equals(file.getBucket())) {
            throw new IllegalArgumentException();
        }
    }
}

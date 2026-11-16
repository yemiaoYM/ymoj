package com.ymcloud.web.service.file.impl;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageConfigValidation;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.StorageConnectionTester;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 随机对象连接测试与历史对象只读校验，不返回远端异常内容
 */
@Service
public class StorageConnectionTesterImpl implements StorageConnectionTester {

    @Autowired
    private StorageRegistry registry;

    /**
     * 只读校验全部历史引用的大小与摘要，全部通过后才允许恢复配置
     * @param profile 待恢复的客户端配置
     * @param files 待核对的非空历史文件引用列表
     * @throws FileException 引用非法、内容不一致、远端读取或客户端关闭失败
     */
    @Override
    public void verifyReferencedFiles(StorageProperties.Profile profile, List<File> files) {
        StorageConfigValidation.validate(profile, true);
        if (files.isEmpty() || profile.getDriverType() == StorageDriverType.LOCAL) {
            throw new FileException("恢复配置需要非空的远端文件引用", HttpStatus.BAD_REQUEST);
        }
        for (File file : files) {
            validateReference(profile, file);
        }
        try (ObjectStorageService driver = registry.createClient(profile)) {
            byte[] buffer = new byte[64 * 1024];
            for (File file : files) {
                MessageDigest digest = FileDigests.digest();
                long size = 0;
                try (InputStream input = driver.open(file.getBucket(), file.getObjectKey())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > file.getFileSize() - size) {
                            throw new IOException("历史对象大小不一致");
                        }
                        size += read;
                        digest.update(buffer, 0, read);
                    }
                }
                if (size != file.getFileSize() || !file.getSha256().equalsIgnoreCase(
                        HexFormat.of().formatHex(digest.digest()))) {
                    throw new IOException("历史对象内容不一致");
                }
            }
        } catch (RuntimeException | IOException e) {
            throw new FileException("已有文件完整性校验失败", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 使用独立客户端测试随机对象并清理探测资源
     * @param profile 待验证的内部客户端配置
     * @throws FileException 双桶读写删除、内容校验或探测资源清理失败
     */
    @Override
    public void test(StorageProperties.Profile profile) {
        StorageConfigValidation.validate(profile, true);
        Path staged = null;
        ObjectStorageService driver = null;
        String key = ".ymcloud-probe/" + UUID.randomUUID();
        List<String> attemptedBuckets = new ArrayList<>();
        Exception failure = null;
        try {
            byte[] bytes = new byte[1024];
            new SecureRandom().nextBytes(bytes);
            String hash = HexFormat.of().formatHex(FileDigests.digest().digest(bytes));
            staged = Files.createTempFile("ymcloud-storage-probe-", ".bin");
            Files.write(staged, bytes);
            driver = registry.createClient(profile);
            List<String> buckets = profile.getDriverType() == StorageDriverType.LOCAL ? List.of(FileConstants.DIR_PUBLIC, FileConstants.DIR_PRIVATE)
                    : List.of(profile.getPublicBucket(), profile.getPrivateBucket());
            for (String bucket : buckets) {
                // 即使 put 的响应丢失也补偿，不能只清理确认上传成功的对象
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
        } catch (RuntimeException | IOException e) {
            failure = e;
        } finally {
            if (driver != null) {
                for (String bucket : attemptedBuckets) {
                    try {
                        driver.delete(bucket, key);
                    } catch (RuntimeException e) {
                        if (failure == null) {
                            failure = e;
                        } else {
                            failure.addSuppressed(e);
                        }
                    }
                }
                try {
                    driver.close();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        }
        if (failure != null) {
            throw new FileException("存储连接测试失败", HttpStatus.BAD_REQUEST, failure);
        }
    }

    /**
     * 校验历史记录具有可验证的大小、摘要和对象位置，并属于预期公开或私有桶
     * @param profile 待恢复的客户端配置
     * @param file 待核对的历史文件记录
     * @throws FileException 历史记录缺失完整性字段或存储桶与访问级别不符
     */
    private void validateReference(StorageProperties.Profile profile, File file) {
        if (file == null || file.getIsPublic() == null || (file.getIsPublic() != FileConstants.ACCESS_PRIVATE
                && file.getIsPublic() != FileConstants.ACCESS_PUBLIC)
                || file.getFileSize() == null || file.getFileSize() < 0 || file.getSha256() == null
                || !file.getSha256().matches("[a-fA-F0-9]{64}") || file.getObjectKey() == null
                || file.getObjectKey().isBlank()
                || file.getObjectKey().chars().anyMatch(Character::isISOControl)) {
            throw new FileException("历史文件引用信息不完整或不一致", HttpStatus.BAD_REQUEST);
        }
        String expectedBucket = file.getIsPublic() == FileConstants.ACCESS_PUBLIC ? profile.getPublicBucket() : profile.getPrivateBucket();
        if (!expectedBucket.equals(file.getBucket())) {
            throw new FileException("历史文件引用信息不完整或不一致", HttpStatus.BAD_REQUEST);
        }
    }
}

package com.ymcloud.core.storage;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件元数据与对象生命周期管理
 * 业务权限由调用方检查
 */
@Service
@Slf4j
public class StoredFileService {

    private final FileEntityService files;
    private final StorageRegistry storage;

    public StoredFileService(FileEntityService files, StorageRegistry storage) {
        this.files = files;
        this.storage = storage;
    }

    @Transactional(rollbackFor = Exception.class)
    public File save(InputStream input, long size, String originalName, String contentType,
                     FileBizType bizType, Long bizId, boolean publicAccess) {
        File file = new File();
        file.setFileName(safeName(originalName));
        file.setFileType(safeContentType(contentType));
        file.setBizType(bizType);
        file.setBizId(bizId);
        file.setIsPublic(publicAccess ? FileConstants.ACCESS_PUBLIC : FileConstants.ACCESS_PRIVATE);
        return save(input, size, file);
    }

    @Transactional(rollbackFor = Exception.class)
    public File save(InputStream input, long size, File file) {
        file.setFileName(safeName(file.getFileName()));
        file.setFileType(safeContentType(file.getFileType()));
        if (file.getBizType() == FileBizType.TEST_DATA && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("测试数据必须为私有文件");
        }
        if (size < 0 || size > storage.maxFileSize()) {
            throw new FileException("文件大小超出限制");
        }
        Path staged = stage(input, size);
        ObjectStorageService client = null;
        boolean registered = false;
        try {
            client = prepareObject(file, staged, storage.active());
            if (Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
                file.setUrl("/files/public/" + file.getStorageProfile() + "/" + file.getObjectKey());
            } else {
                file.setUrl(null);
            }
            file.setCreatedTime(LocalDateTime.now());
            file.setUpdatedTime(file.getCreatedTime());
            try {
                if (!files.save(file)) {
                    throw new FileException("保存文件记录失败");
                }
            } catch (RuntimeException e) {
                cleanup(file, client);
                throw e;
            }
            registerRollback(file, client);
            registered = true;
            return file;
        } finally {
            if (!registered && client != null) {
                client.close();
            }
            removeStaged(staged);
        }
    }

    public File get(Long id) {
        File file = files.lambdaQuery()
                .eq(File::getId, id)
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        return file;
    }

    public File resolvePublicUrl(String url) {
        return files.lambdaQuery()
                .eq(File::getUrl, url)
                .eq(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                .one();
    }

    public InputStream open(File file) {
        if (file.getStorageProfile() == null) {
            if (!Integer.valueOf(FileConstants.STORAGE_LOCAL).equals(file.getStorageType())) {
                throw new FileException("历史远程文件缺少存储配置，需先人工核对");
            }
            return openLegacy(file.getStoragePath());
        }
        return storage.client(file.getStorageProfile()).open(file.getBucket(), file.getObjectKey());
    }

    public InputStream openLegacyPublic(String relativePath) {
        return openLegacy("public/" + relativePath);
    }

    private InputStream openLegacy(String path) {
        try {
            return Files.newInputStream(legacyLocalPath(path));
        } catch (IOException | IllegalArgumentException e) {
            throw new FileException("历史本地文件不存在或路径不合法", e);
        }
    }

    /**
     * 文件管理主动删除普通文件，数据库提交后删除固定位置的对象
     * @param id 待删除文件 ID，业务授权及引用检查由调用方完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        File file = files.lambdaQuery()
                .eq(File::getId, id)
                .last("FOR UPDATE")
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            throw new FileException("测试数据由版本管理，不允许直接删除");
        }
        File object = objectSnapshot(file);
        ObjectStorageService client = file.getStorageProfile() == null ? null : storage.lease(file.getStorageProfile());
        boolean registered = false;
        try {
            if (!files.lambdaUpdate()
                    .eq(File::getId, id)
                    .remove()) {
                throw new FileException("删除文件记录失败");
            }
            registerDelete(object, client);
            registered = true;
        } finally {
            if (!registered && client != null) {
                client.close();
            }
        }
    }

    public List<File> legacyInventory() {
        return files.lambdaQuery()
                .eq(File::getStorageType, FileConstants.STORAGE_LOCAL)
                .isNull(File::getStorageProfile)
                .orderByAsc(File::getId)
                .list();
    }

    /**
     * 保留原 ID、URL 和原始字节，迁移可重跑
     * 历史路径写入 legacyPath
     * @param id 待迁移文件 ID
     * @param apply 是否执行迁移，false 时仅检查并返回文件
     * @return 保留业务标识的文件元数据
     */
    @Transactional(rollbackFor = Exception.class)
    public File migrateLocal(Long id, boolean apply) {
        File file = files.lambdaQuery()
                .eq(File::getId, id)
                .last("FOR UPDATE")
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        if (file.getStorageProfile() != null) {
            return file;
        }
        if (!Integer.valueOf(FileConstants.STORAGE_LOCAL).equals(file.getStorageType())) {
            throw new FileException("仅支持迁移历史本地文件");
        }
        Path staged;
        try (InputStream input = open(file)) {
            long actualSize = inputSize(file);
            staged = stage(input, actualSize);
        } catch (IOException e) {
            throw new FileException("读取历史文件失败", e);
        }
        ObjectStorageService client = null;
        boolean registered = false;
        try {
            String hash = hash(staged);
            if (file.getSha256() != null && !file.getSha256().equals(hash)) {
                throw new FileException("历史文件哈希校验失败");
            }
            if (!apply) {
                file.setSha256(hash);
                return file;
            }
            file.setLegacyPath(file.getStoragePath());
            client = prepareObject(file, staged, storage.active());
            try {
                if (!files.lambdaUpdate()
                        .eq(File::getId, id)
                        .set(File::getStorageProfile, file.getStorageProfile())
                        .set(File::getStorageType, file.getStorageType())
                        .set(File::getBucket, file.getBucket())
                        .set(File::getObjectKey, file.getObjectKey())
                        .set(File::getStoragePath, file.getStoragePath())
                        .set(File::getSha256, file.getSha256())
                        .set(File::getLegacyPath, file.getLegacyPath())
                        .set(File::getFileSize, file.getFileSize())
                        .set(File::getFileExt, file.getFileExt())
                        .set(File::getUpdatedTime, LocalDateTime.now())
                        .update()) {
                    throw new FileException("更新迁移文件记录失败");
                }
            } catch (RuntimeException e) {
                cleanup(file, client);
                throw e;
            }
            registerRollback(file, client);
            registered = true;
            return file;
        } finally {
            if (!registered && client != null) {
                client.close();
            }
            removeStaged(staged);
        }
    }

    private long inputSize(File file) {
        try {
            long actual = Files.size(legacyLocalPath(file.getStoragePath()));
            if (file.getFileSize() != null && file.getFileSize() != actual) {
                throw new FileException("历史文件大小与记录不一致");
            }
            return actual;
        } catch (IOException | IllegalArgumentException e) {
            throw new FileException("无法核对历史文件大小", e);
        }
    }

    private ObjectStorageService prepareObject(File file, Path staged, String profileName) {
        StorageProperties.Profile profile = storage.profile(profileName);
        boolean publicAccess = Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic());
        String bucket = "local".equals(profile.getType()) ? (publicAccess ? FileConstants.DIR_PUBLIC : FileConstants.DIR_PRIVATE)
                : (publicAccess ? profile.getPublicBucket() : profile.getPrivateBucket());
        String extension = extension(file.getFileName());
        String key = UUID.randomUUID().toString().replace("-", "") + extension;
        String sha256 = hash(staged);
        file.setStorageProfile(profileName);
        file.setBucket(bucket);
        file.setObjectKey(key);
        file.setStoragePath(bucket + "/" + key);
        file.setStorageType("local".equals(profile.getType()) ? FileConstants.STORAGE_LOCAL
                : ("oss".equals(profile.getType()) ? FileConstants.STORAGE_OSS : FileConstants.STORAGE_S3));
        file.setSha256(sha256);
        file.setFileExt(extension.isEmpty() ? "" : extension.substring(1));
        ObjectStorageService client = null;
        try {
            file.setFileSize(Files.size(staged));
            client = storage.lease(profileName);
            client.put(bucket, key, staged, safeContentType(file.getFileType()), sha256);
            try (InputStream uploaded = client.open(bucket, key)) {
                if (!sha256.equals(FileDigests.sha256(uploaded))) {
                    throw new FileException("上传文件哈希校验失败");
                }
            }
            return client;
        } catch (Exception e) {
            if (client != null) {
                cleanup(file, client);
                client.close();
            }
            throw new FileException("对象保存或校验失败", e);
        }
    }

    private Path stage(InputStream input, long expectedSize) {
        if (expectedSize < 0 || expectedSize > storage.maxFileSize()) {
            throw new FileException("文件大小超出限制");
        }
        Path staged = null;
        try {
            Path staging = StoragePaths.resolve(storage.localRoot(), ".staging/files");
            Files.createDirectories(staging);
            staged = Files.createTempFile(staging, "upload-", ".part");
            try (OutputStream output = Files.newOutputStream(staged)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > expectedSize || total > storage.maxFileSize()) {
                        throw new FileException("文件大小与上传声明不一致或超出限制");
                    }
                    output.write(buffer, 0, count);
                }
                if (total != expectedSize) {
                    throw new FileException("文件大小与上传声明不一致");
                }
            }
            return staged;
        } catch (IOException | RuntimeException e) {
            removeStaged(staged);
            throw new FileException("暂存上传文件失败", e);
        }
    }

    private String hash(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return FileDigests.sha256(input);
        } catch (IOException e) {
            throw new FileException("文件哈希计算失败", e);
        }
    }

    private void registerRollback(File file, ObjectStorageService client) {
        File object = objectSnapshot(file);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        if (status != STATUS_COMMITTED) {
                            cleanup(object, client);
                        }
                    } finally {
                        client.close();
                    }
                }
            });
        } else {
            client.close();
        }
    }

    private File objectSnapshot(File file) {
        File snapshot = new File();
        snapshot.setId(file.getId());
        snapshot.setStorageProfile(file.getStorageProfile());
        snapshot.setBucket(file.getBucket());
        snapshot.setObjectKey(file.getObjectKey());
        snapshot.setStoragePath(file.getStoragePath());
        snapshot.setLegacyPath(file.getLegacyPath());
        return snapshot;
    }

    private Path legacyLocalPath(String path) {
        Path root = storage.localRoot();
        Path supplied = Path.of(path);
        String relative = supplied.isAbsolute()
                ? root.relativize(supplied.toAbsolutePath().normalize()).toString().replace('\\', '/')
                : path.replace('\\', '/');
        return StoragePaths.resolve(root, relative);
    }

    /**
     * 清理主动删除的普通文件及历史本地副本，迁移回滚不调用此方法
     * @param file 删除时固定的文件位置
     * @param client 删除时租用的存储客户端，历史本地文件可为空
     */
    private void cleanupDeleted(File file, ObjectStorageService client) {
        cleanup(file, client);
        String legacy = file.getStorageProfile() == null ? file.getStoragePath() : file.getLegacyPath();
        if (legacy != null) {
            try {
                Files.deleteIfExists(legacyLocalPath(legacy));
            } catch (Exception e) {
                log.error("历史文件删除失败，需核对文件 ID {}", file.getId(), e);
            }
        }
    }

    /**
     * 按既有文件删除流程在提交后清理对象，并在事务完成时释放固定客户端
     * @param file 删除时固定的文件位置
     * @param client 已租用的存储客户端，历史本地文件可为空
     */
    private void registerDelete(File file, ObjectStorageService client) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 文件登记删除提交后清理实际对象
                 */
                @Override
                public void afterCommit() {
                    try {
                        cleanupDeleted(file, client);
                    } finally {
                        if (client != null) {
                            client.close();
                        }
                    }
                }

                /**
                 * 提交或回滚完成后释放固定客户端
                 * @param status 事务完成状态
                 */
                @Override
                public void afterCompletion(int status) {
                    if (client != null) {
                        client.close();
                    }
                }
            });
        } else {
            try {
                cleanupDeleted(file, client);
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }
    }

    private void cleanup(File file, ObjectStorageService client) {
        try {
            if (file.getStorageProfile() == null || client == null) {
                // 历史原始文件保留，迁移后仍可回退。
                return;
            }
            client.delete(file.getBucket(), file.getObjectKey());
        } catch (Exception e) {
            log.error("对象清理失败，需核对文件 ID {}", file.getId());
        }
    }

    private void removeStaged(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            log.warn("清理上传暂存文件失败", e);
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new FileException("非法文件名");
        }
        return name;
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    private String safeContentType(String type) {
        return type != null && type.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")
                ? type : "application/octet-stream";
    }
}

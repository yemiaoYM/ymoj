package com.ymcloud.web.service.file.impl;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.ExceptionLogUtils;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.core.storage.util.StoragePaths;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.StoredFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class StoredFileServiceImpl implements StoredFileService {

    @Autowired
    private FileEntityService files;

    @Autowired
    private StorageRegistry storage;

    /**
     * 创建文件登记参数并交给统一上传入口校验和保存
     * @param input 调用方负责关闭的上传流
     * @param size 声明的字节数，暂存时核对实际长度
     * @param originalName 原始文件名，不得包含路径
     * @param contentType 上传媒体类型
     * @param bizType 调用方已授权的业务类型
     * @param bizId 业务标识，无关联时可为空
     * @param publicAccess 是否允许公开存储，测试数据必须为私有
     * @return 已保存的文件元数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public File save(InputStream input, long size, String originalName, String contentType,
                     FileBizType bizType, Long bizId, boolean publicAccess) {
        File file = new File();
        file.setFileName(originalName);
        file.setFileType(contentType);
        file.setBizType(bizType);
        file.setBizId(bizId);
        file.setIsPublic(publicAccess ? FileConstants.ACCESS_PUBLIC : FileConstants.ACCESS_PRIVATE);
        return save(input, size, file);
    }

    /**
     * 保存对象和文件记录，将对象回滚清理绑定到当前事务
     * @param input 调用方负责关闭的上传流
     * @param size 声明的字节数，由暂存入口统一校验
     * @param file 包含已授权业务归属的元数据，成功后填充对象位置
     * @return 已保存的文件元数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public File save(InputStream input, long size, File file) {
        file.setFileName(safeName(file.getFileName()));
        file.setFileType(safeContentType(file.getFileType()));
        if (file.getBizType() == FileBizType.TEST_DATA && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("测试数据必须为私有文件");
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

    /**
     * 按内部编号获取文件，不存在时抛出文件异常
     * @param id 内部文件编号
     * @return 已存在的文件元数据
     */
    @Override
    public File get(Long id) {
        File file = files.lambdaQuery()
                .eq(File::getId, id)
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        return file;
    }

    /**
     * 按已登记公开 URL 查询文件
     * @param url 已登记的公开 URL
     * @return 公开文件元数据，不存在时为空
     */
    @Override
    public File resolvePublicUrl(String url) {
        return files.lambdaQuery()
                .eq(File::getUrl, url)
                .eq(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                .one();
    }

    /**
     * 打开已授权文件，由调用方关闭流
     * @param file 已授权的文件元数据
     * @return 调用方负责关闭的文件流
     */
    @Override
    public InputStream open(File file) {
        if (file.getStorageProfile() == null) {
            if (!Integer.valueOf(FileConstants.STORAGE_LOCAL).equals(file.getStorageType())) {
                throw new FileException("历史远程文件缺少存储配置");
            }
            return openLegacy(file.getStoragePath());
        }
        return storage.client(file.getStorageProfile()).open(file.getBucket(), file.getObjectKey());
    }

    /**
     * 打开迁移期间保留的历史公开文件
     * @param relativePath 历史公开目录内的相对路径
     * @return 调用方负责关闭的历史文件流
     */
    @Override
    public InputStream openLegacyPublic(String relativePath) {
        return openLegacy("public/" + relativePath);
    }

    /**
     * 在本地根目录边界内打开历史文件
     * @param path 历史记录中的绝对或相对存储路径
     * @return 由调用方关闭的文件流
     * @throws FileException 文件不存在、路径非法或无法读取
     */
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
    @Override
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

    /**
     * 列出尚未迁移的本地文件记录
     * @return 待迁移的本地文件列表
     */
    @Override
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
    @Override
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

    /**
     * 读取历史文件实际大小并核对已有数据库记录
     * @param file 待迁移的历史文件记录
     * @return 实际文件字节数
     * @throws FileException 文件不可读或实际大小与已有记录不符
     */
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

    /**
     * 分配对象位置、上传并回读验证摘要，失败时清理新对象
     * @param file 待填充对象位置与完整性信息的文件记录
     * @param staged 已暂存的完整上传文件
     * @param profileName 目标存储配置标识
     * @return 固定目标配置的客户端租约，由调用方移交事务回调或关闭
     */
    private ObjectStorageService prepareObject(File file, Path staged, String profileName) {
        StorageProperties.Profile profile = storage.profile(profileName);
        boolean publicAccess = Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic());
        String bucket = profile.getDriverType() == StorageDriverType.LOCAL ? (publicAccess ? FileConstants.DIR_PUBLIC : FileConstants.DIR_PRIVATE)
                : (publicAccess ? profile.getPublicBucket() : profile.getPrivateBucket());
        String extension = extension(file.getFileName());
        String key = UUID.randomUUID().toString().replace("-", "") + extension;
        String sha256 = hash(staged);
        file.setStorageProfile(profileName);
        file.setBucket(bucket);
        file.setObjectKey(key);
        file.setStoragePath(bucket + "/" + key);
        file.setStorageType(switch (profile.getDriverType()) {
            case LOCAL -> FileConstants.STORAGE_LOCAL;
            case S3 -> FileConstants.STORAGE_S3;
            case ALIYUN_OSS -> FileConstants.STORAGE_OSS;
        });
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
        } catch (IOException | RuntimeException e) {
            if (client != null) {
                cleanup(file, client);
                client.close();
            }
            if (e instanceof RuntimeException failure) {
                throw failure;
            }
            throw new FileException("对象保存或校验失败", e);
        }
    }

    /**
     * 限量读取上传流到本地暂存文件，严格核对声明大小
     * @param input 由调用方负责关闭的上传流
     * @param expectedSize 声明的文件字节数，必须在上传限制内
     * @return 暂存文件路径，由调用方在上传结束后清理
     * @throws FileException 声明大小非法、实际大小不符或暂存失败
     */
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
            if (e instanceof RuntimeException failure) {
                throw failure;
            }
            throw new FileException("暂存上传文件失败", e);
        }
    }

    /**
     * 计算本地文件的 SHA-256，并关闭读取流
     * @param path 待计算的本地文件路径
     * @return 小写十六进制 SHA-256 摘要
     * @throws FileException 文件读取失败
     */
    private String hash(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return FileDigests.sha256(input);
        } catch (IOException e) {
            throw new FileException("文件哈希计算失败", e);
        }
    }

    /**
     * 将新对象清理绑定到事务回滚，并在事务完成后释放客户端
     * @param file 已保存对象的位置与文件身份
     * @param client 固定上传配置的客户端，本方法接管关闭责任
     */
    private void registerRollback(File file, ObjectStorageService client) {
        File object = objectSnapshot(file);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 事务未提交时补偿对象，所有完成状态均释放客户端
                 * @param status 事务完成状态
                 */
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

    /**
     * 复制事务回调所需的位置字段，避免业务记录后续修改影响清理目标
     * @param file 当前文件元数据
     * @return 仅包含身份及对象、历史位置的独立快照
     */
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

    /**
     * 将历史绝对或相对路径转换为本地根目录内的安全路径
     * @param path 历史记录中的文件路径
     * @return 经过越界与符号链接检查的绝对路径
     */
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
                log.error("历史文件删除失败 fileId={}", file.getId(), ExceptionLogUtils.forLogging(e));
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

    /**
     * 补偿删除新对象，失败时记录待核对文件编号，不覆盖原始业务异常
     * @param file 固定的对象位置快照
     * @param client 固定配置客户端，历史文件或未取得客户端时可为空
     */
    private void cleanup(File file, ObjectStorageService client) {
        try {
            if (file.getStorageProfile() == null || client == null) {
                // 历史原始文件保留，迁移后仍可回退
                return;
            }
            client.delete(file.getBucket(), file.getObjectKey());
        } catch (Exception e) {
            log.error("对象清理失败 fileId={}", file.getId(), ExceptionLogUtils.forLogging(e));
        }
    }

    /**
     * 删除本次上传暂存文件，失败时记录清理告警
     * @param staged 暂存文件路径，暂存尚未创建时可为空
     */
    private void removeStaged(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            log.warn("上传暂存文件清理失败", ExceptionLogUtils.forLogging(e));
        }
    }

    /**
     * 校验原始文件名，拒绝路径分隔符与控制字符
     * @param name 原始文件名，必须为非空且最多 255 个字符
     * @return 通过校验的原始文件名
     * @throws FileException 文件名不合法
     */
    private String safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new FileException("非法文件名");
        }
        return name;
    }

    /**
     * 从文件名提取允许的短扩展名，用于随机对象键
     * @param name 文件名，历史记录可为空
     * @return 含点的小写扩展名，不符合 1～10 位字母数字规则时为空字符串
     */
    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    /**
     * 保留合法媒体类型，缺失或不合法时使用二进制类型
     * @param type 声明的媒体类型，可为空
     * @return 合法媒体类型或 application/octet-stream
     */
    private String safeContentType(String type) {
        return type != null && type.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")
                ? type : "application/octet-stream";
    }
}

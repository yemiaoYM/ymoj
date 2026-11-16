package com.ymcloud.web.service.file.impl;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.ExceptionLogUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.core.storage.util.StoragePaths;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件元数据与对象生命周期管理
 * 业务权限由调用方检查
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private FileEntityService files;

    @Autowired
    private StorageRegistry storage;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private ContestEntityService contests;

    /**
     * 根据创建命令保存对象和元数据，将对象清理绑定到当前事务
     * @param command 业务已授权的创建参数，物理对象键与 publicId 均由本服务生成
     * @param input 调用方负责关闭的上传流
     * @param size 声明字节数，由暂存入口核对实际长度
     * @return 已保存且具有稳定身份和对象位置的文件记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public File create(CreateStoredFileCommand command, InputStream input, long size) {
        File file = new File();
        file.setFileName(command.fileName());
        file.setFileType(command.contentType());
        file.setBizType(command.bizType());
        file.setBizId(command.bizId());
        file.setIsPublic(command.publicAccess() ? FileConstants.ACCESS_PUBLIC : FileConstants.ACCESS_PRIVATE);
        file.setUploaderId(command.uploaderId());
        file.setUploaderUsername(command.uploaderUsername());
        file.setFileName(safeName(file.getFileName()));
        file.setFileType(safeContentType(file.getFileType()));
        if (file.getBizType() == FileBizType.TEST_DATA && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("测试数据必须为私有文件");
        }
        Path staged = stage(input, size);
        ObjectStorageService client = null;
        boolean registered = false;
        try {
            file.setPublicId(UUID.randomUUID().toString().replace("-", ""));
            client = prepareObject(file, staged, storage.active(), command.namespace());
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
     * 按对外标识查询文件，授权由业务调用方执行
     * @param publicId 32 位小写 UUIDv4 标识
     * @return 已登记文件
     * @throws FileException 标识非法或文件不存在
     */
    @Override
    public File getByPublicId(String publicId) {
        if (publicId == null || !publicId.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}")) {
            throw new FileException("文件标识不合法", HttpStatus.BAD_REQUEST);
        }
        File file = files.lambdaQuery()
                .eq(File::getPublicId, publicId)
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
        if (url == null || !url.matches("/files/public/[0-9a-f]{32}")) {
            return null;
        }
        return files.lambdaQuery()
                .eq(File::getPublicId, url.substring(FileConstants.PUBLIC_URL_PREFIX.length()))
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
        return storage.client(file.getStorageKey()).open(file.getBucket(), file.getObjectKey());
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
        ObjectStorageService client = storage.lease(file.getStorageKey());
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
            if (!registered) {
                client.close();
            }
        }
    }

    /**
     * 分配对象位置、上传并回读验证摘要，失败时清理新对象
     * @param file 待填充对象位置与完整性信息的文件记录
     * @param staged 已暂存的完整上传文件
     * @param profileName 目标存储配置标识
     * @param namespace 通过对象键校验的业务相对目录
     * @return 固定目标配置的客户端租约，由调用方移交事务回调或关闭
     */
    private ObjectStorageService prepareObject(File file, Path staged, String profileName, String namespace) {
        StorageProperties.Profile profile = storage.profile(profileName);
        boolean publicAccess = Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic());
        String bucket = profile.getDriverType() == StorageDriverType.LOCAL ? (publicAccess ? FileConstants.DIR_PUBLIC : FileConstants.DIR_PRIVATE)
                : (publicAccess ? profile.getPublicBucket() : profile.getPrivateBucket());
        String extension = extension(file.getFileName());
        String key = namespace + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
        StoragePaths.validateObjectKey(key);
        String sha256 = hash(staged);
        file.setStorageKey(profileName);
        file.setBucket(bucket);
        file.setObjectKey(key);
        file.setSha256(sha256);
        file.setFileExt(extension.isEmpty() ? "" : extension.substring(1));
        ObjectStorageService client = null;
        try {
            file.setFileSize(Files.size(staged));
            client = storage.lease(profileName);
            client.put(bucket, key, staged, file.getFileType(), sha256);
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
     * @return 仅包含身份及对象位置的独立快照
     */
    private File objectSnapshot(File file) {
        File snapshot = new File();
        snapshot.setId(file.getId());
        snapshot.setStorageKey(file.getStorageKey());
        snapshot.setBucket(file.getBucket());
        snapshot.setObjectKey(file.getObjectKey());
        return snapshot;
    }

    /**
     * 按既有文件删除流程在提交后清理对象，并在事务完成时释放固定客户端
     * @param file 删除时固定的文件位置
     * @param client 已租用的存储客户端，事务完成后关闭
     */
    private void registerDelete(File file, ObjectStorageService client) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 文件登记删除提交后清理实际对象
                 */
                @Override
                public void afterCommit() {
                    cleanup(file, client);
                }

                /**
                 * 提交或回滚完成后释放固定客户端
                 * @param status 事务完成状态
                 */
                @Override
                public void afterCompletion(int status) {
                    client.close();
                }
            });
        } else {
            try {
                cleanup(file, client);
            } finally {
                client.close();
            }
        }
    }

    /**
     * 补偿删除新对象，失败时记录待核对文件编号，不覆盖原始业务异常
     * @param file 固定的对象位置快照
     * @param client 已租用的固定配置客户端
     */
    private void cleanup(File file, ObjectStorageService client) {
        try {
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
     * @param name 已通过入口校验的文件名
     * @return 含点的小写扩展名，不符合 1～10 位字母数字规则时为空字符串
     */
    private String extension(String name) {
        int dot = name.lastIndexOf('.');
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

    /**
     * 校验公开路径和文件记录，拒绝私有文件及测试数据
     * @param path 公开 URL 前缀后的路径，包含开头的斜杠
     * @return 已登记且允许公开读取的文件元数据，仅供服务端使用
     * @throws FileException 路径非法、记录不可公开或缺少存储配置标识
     */
    @Override
    public File resolvePublicFile(String path) {
        if (path == null || !path.matches("/[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}")) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        File file = resolvePublicUrl("/files/public" + path);
        if (file == null || file.getBizType() == FileBizType.TEST_DATA) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        if (file.getBizType() == FileBizType.PROBLEM_ATTACHMENT) {
            Problem problem = problems.getById(file.getBizId());
            if (problem == null || problem.getVisibility() != ProblemConstants.PUBLIC
                    || problems.hasActiveContestReference(problem.getId(), LocalDateTime.now())) {
                throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
            }
        } else if (file.getBizType() == FileBizType.CONTEST_ATTACHMENT) {
            Contest contest = contests.getById(file.getBizId());
            if (contest == null || contest.getVisibility() != ContestConstants.VISIBILITY_PUBLIC
                    || LocalDateTime.now().isBefore(contest.getStartTime())) {
                throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
            }
        }
        return file;
    }

    /**
     * 保存上传内容及文件记录，读取结束后关闭上传流
     * @param file 待保存的上传文件
     * @param fileRecord 已设置业务类型和访问范围的文件记录
     * @return 已保存的头像文件记录
     * @throws FileException 上传内容读取失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public File saveFile(MultipartFile file, File fileRecord) {
        try (InputStream input = file.getInputStream()) {
            return create(new CreateStoredFileCommand(fileRecord.getFileName(), fileRecord.getFileType(), FileConstants.SUB_DIR_AVATAR,
                    fileRecord.getBizType(), fileRecord.getBizId(), true, fileRecord.getUploaderId(), fileRecord.getUploaderUsername()),
                    input, file.getSize());
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
    }

    /**
     * 删除公开文件；图片删除提交后失效轮播列表，避免保留失效引用
     * @param url 已登记的公开文件 URL；未找到时不做操作
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileByUrl(String url) {
        File file = resolvePublicUrl(url);
        if (file != null) {
            delete(file.getId());
            if (file.getBizType() == FileBizType.IMAGE) {
                evictCarouselAfterCommit();
            }
        }
    }

    /**
     * 图片删除提交后删除公开缓存；外层事务回滚时保留旧缓存
     * Redis 故障继续上抛，不能把清理失败伪装为全部成功
     */
    private void evictCarouselAfterCommit() {
        String key = CacheConstants.CACHE_NAME + ":" + CacheConstants.HOME_CAROUSEL_KEY;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 数据库成功提交后，删除与 Spring Cache 相同命名的键
                 */
                @Override
                public void afterCommit() {
                    redisCache.deleteObject(key);
                }
            });
        } else {
            redisCache.deleteObject(key);
        }
    }
}

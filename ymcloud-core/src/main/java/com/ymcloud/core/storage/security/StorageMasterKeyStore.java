package com.ymcloud.core.storage.security;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.util.StorageDirectoryResolver;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.pojo.entity.StorageConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: OJ 自行管理凭据加密主密钥，不依赖任何对象存储安装脚本
 */
@Component
public class StorageMasterKeyStore {

    private static final ConcurrentMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final Path keyFile;
    private final Path legacyKeyFile;
    private final StorageConfigEntityService configs;

    /**
     * 固定当前密钥路径及历史密钥路径，关联动态配置用于恢复校验
     * @param localRoot 本地文件根目录配置
     * @param configs 动态存储配置实体服务
     */
    public StorageMasterKeyStore(@Value("${ymcloud.file.base-dir}") String localRoot, StorageConfigEntityService configs) {
        this.keyFile = StorageDirectoryResolver.resolve(localRoot).resolve(".secrets/storage-config.key");
        this.legacyKeyFile = StorageDirectoryResolver.resolve(localRoot).resolveSibling("seaweedfs")
                .resolve("secrets/storage-config.key");
        this.configs = configs;
    }

    /**
     * 首次保存可初始化，解密只读取，缺失的历史主密钥绝不自动替换
     * @param allowCreate 是否允许在不存在历史配置时创建主密钥
     * @return 主密钥字节，调用方负责使用后清零
     * @throws FileException 密钥缺失、格式非法或无法安全读取和保存
     */
    public byte[] load(boolean allowCreate) {
        synchronized (LOCKS.computeIfAbsent(keyFile, ignored -> new Object())) {
            try {
                validatePath(keyFile);
                if (!allowCreate && !Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(legacyKeyFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw missingKey();
                }
                Path directory = keyFile.getParent();
                Files.createDirectories(directory, directoryAttributes(directory));
                protect(directory, true);
                Path lockFile = keyFile.resolveSibling(keyFile.getFileName() + ".lock");
                validatePath(lockFile);
                try (FileChannel channel = FileChannel.open(lockFile,
                        Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                        fileAttributes(directory))) {
                    protect(lockFile, false);
                    try (FileLock ignored = channel.lock()) {
                        validatePath(keyFile);
                        if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
                            if (Files.exists(legacyKeyFile, LinkOption.NOFOLLOW_LINKS)) {
                                importLegacy(directory);
                            } else if (!allowCreate || configs.lambdaQuery()
                                    .exists()) {
                                throw missingKey();
                            } else {
                                generate(directory);
                            }
                        }
                        protect(keyFile, false);
                        return read(keyFile);
                    }
                }
            } catch (IOException e) {
                throw new FileException("存储主密钥读写失败", e);
            }
        }
    }

    /**
     * 生成 32 字节随机主密钥并安全写入，结束后清零内存副本
     * @param directory 主密钥所属目录
     * @throws IOException 创建或写入密钥失败
     */
    private void generate(Path directory) throws IOException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            write(directory, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 校验数据库密文后将旧安装已有的密钥写入 OJ 自有目录
     * @param directory 当前密钥所属目录
     * @throws IOException 读取旧密钥或写入新文件失败
     * @throws FileException 旧密钥无法解密已有配置
     */
    private void importLegacy(Path directory) throws IOException {
        validatePath(legacyKeyFile);
        byte[] key = read(legacyKeyFile);
        try {
            StorageProperties original = new StorageProperties();
            original.setConfigEncryptionKey(Base64.getEncoder().encodeToString(key));
            StorageCredentialCipher probe = new StorageCredentialCipher(original);
            for (StorageConfig config : configs.lambdaQuery()
                    .list()) {
                probe.decrypt(config.getId(), "accessKey", config.getEncryptedAccessKey());
                probe.decrypt(config.getId(), "secretKey", config.getEncryptedSecretKey());
            }
            write(directory, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * 以仅创建方式写入主密钥并强制刷新到磁盘，不覆盖已有密钥
     * @param directory 用于确定文件权限的父目录
     * @param key 待写入的 32 字节主密钥
     * @throws IOException 创建文件、设置权限或写入失败
     */
    private void write(Path directory, byte[] key) throws IOException {
        byte[] encoded = Base64.getEncoder().encode(key);
        try (FileChannel channel = FileChannel.open(keyFile,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                fileAttributes(directory))) {
            protect(keyFile, false);
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    /**
     * 读取有大小上限的密钥文件并校验 Base64 格式与密钥长度
     * @param path 已校验的密钥文件路径
     * @return 32 字节主密钥，调用方负责使用后清零
     * @throws IOException 文件读取失败
     * @throws FileException 文件过大、编码错误或密钥长度不正确
     */
    private byte[] read(Path path) throws IOException {
        if (Files.size(path) > 128) {
            throw invalidKey();
        }
        try {
            byte[] key = Base64.getDecoder().decode(Files.readString(path, StandardCharsets.UTF_8).trim());
            if (key.length != 32) {
                Arrays.fill(key, (byte) 0);
                throw invalidKey();
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new FileException("存储主密钥无效", e);
        }
    }

    /**
     * 检查主密钥路径及祖先的类型，拒绝符号链接
     * @param path 预期为普通文件的密钥或锁文件路径
     * @throws IOException 路径校验期间发生文件系统错误
     * @throws FileException 路径包含符号链接或已有节点类型不符
     */
    private void validatePath(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) {
                throw new FileException("OJ 存储主密钥路径不允许符号链接");
            }
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && (cursor.equals(path) ? !Files.isRegularFile(cursor, LinkOption.NOFOLLOW_LINKS)
                    : !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS))) {
                throw new FileException("OJ 存储主密钥路径类型不合法");
            }
        }
    }

    /**
     * 获取新建密钥目录的初始权限
     * @param directory 待创建目录
     * @return POSIX 系统的仅所有者访问权限，其他系统返回空属性数组
     * @throws IOException 无法确定所属文件系统
     */
    private FileAttribute<?>[] directoryAttributes(Path directory) throws IOException {
        return supportsPosix(directory)
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new FileAttribute<?>[0];
    }

    /**
     * 获取新建密钥及锁文件的初始权限
     * @param directory 文件所属目录
     * @return POSIX 系统的仅所有者读写权限，其他系统返回空属性数组
     * @throws IOException 无法确定所属文件系统
     */
    private FileAttribute<?>[] fileAttributes(Path directory) throws IOException {
        return supportsPosix(directory)
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }

    /**
     * 通过最近存在的祖先确定路径所属文件系统是否支持 POSIX 权限
     * @param directory 待检查路径，可以尚不存在
     * @return 是否支持 POSIX 文件属性
     * @throws IOException 无法读取文件系统信息
     */
    private boolean supportsPosix(Path directory) throws IOException {
        Path existing = directory;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        return Files.getFileStore(existing).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    /**
     * 限制密钥目录或文件访问权限，Windows 仅授权当前用户与 SYSTEM
     * @param path 已存在的目录或文件
     * @param directory 是否需要目录执行权限或 ACL 继承
     * @throws IOException 设置权限或解析用户身份失败
     * @throws FileException 文件系统不支持所需的权限限制
     */
    private void protect(Path path, boolean directory) throws IOException {
        if (supportsPosix(path)) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new FileException("文件系统不支持主密钥权限限制");
        }
        var lookup = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal owner = lookup.lookupPrincipalByName(System.getProperty("user.name"));
        UserPrincipal system = lookup.lookupPrincipalByName("SYSTEM");
        view.setAcl(List.of(rule(owner, directory), rule(system, directory)));
    }

    /**
     * 为指定身份构建完全控制 ACL，目录规则同时允许子项继承
     * @param principal 被授权的用户身份
     * @param directory 是否添加目录与文件继承标志
     * @return 对应身份的允许访问规则
     */
    private AclEntry rule(UserPrincipal principal, boolean directory) {
        AclEntry.Builder builder = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(principal)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) {
            builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        }
        return builder.build();
    }

    /**
     * 构造主密钥缺失错误，不自动重置历史凭据
     * @return 主密钥缺失的文件异常
     */
    private FileException missingKey() {
        return new FileException("存储主密钥未配置");
    }

    /**
     * 构造主密钥格式错误提示
     * @return 主密钥格式无效的文件异常
     */
    private FileException invalidKey() {
        return new FileException("存储主密钥无效");
    }
}

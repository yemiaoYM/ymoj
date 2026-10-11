package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;
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

/** OJ 自行管理凭据加密主密钥，不依赖任何对象存储安装脚本。 */
@Component
public class StorageMasterKeyStore {

    private static final ConcurrentMap<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private final Path keyFile;
    private final Path legacyKeyFile;
    private final StorageConfigEntityService configs;

    public StorageMasterKeyStore(@Value("${ymcloud.file.base-dir}") String localRoot, StorageConfigEntityService configs) {
        this.keyFile = StorageDirectoryResolver.resolve(localRoot).resolve(".secrets/storage-config.key");
        this.legacyKeyFile = StorageDirectoryResolver.resolve(localRoot).resolveSibling("seaweedfs")
                .resolve("secrets/storage-config.key");
        this.configs = configs;
    }

    /** 首次保存可初始化；解密只读取，缺失的历史主密钥绝不自动替换。 */
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
                throw new FileException("无法读取或保存 OJ 存储配置主密钥，请检查本地目录权限");
            }
        }
    }

    private void generate(Path directory) throws IOException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            write(directory, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** 只迁移旧安装已有的密钥，校验数据库密文后写入 OJ 自己的目录；不导入旧 YAML。 */
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
            throw invalidKey();
        }
    }

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

    private FileAttribute<?>[] directoryAttributes(Path directory) throws IOException {
        return supportsPosix(directory)
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new FileAttribute<?>[0];
    }

    private FileAttribute<?>[] fileAttributes(Path directory) throws IOException {
        return supportsPosix(directory)
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }

    private boolean supportsPosix(Path directory) throws IOException {
        Path existing = directory;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        return Files.getFileStore(existing).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    private void protect(Path path, boolean directory) throws IOException {
        if (supportsPosix(path)) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new FileException("文件系统不支持限制 OJ 主密钥访问权限，请配置安全的密钥目录");
        }
        var lookup = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal owner = lookup.lookupPrincipalByName(System.getProperty("user.name"));
        UserPrincipal system = lookup.lookupPrincipalByName("SYSTEM");
        view.setAcl(List.of(rule(owner, directory), rule(system, directory)));
    }

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

    private FileException missingKey() {
        return new FileException("OJ 存储配置主密钥缺失，请恢复原密钥备份；如需重新开始，先设本地存储为默认并删除全部无引用旧配置，再新增配置。已有文件引用的配置须恢复原密钥");
    }

    private FileException invalidKey() {
        return new FileException("OJ 存储主密钥文件无效，请恢复原 Base64 编码的 32 字节密钥");
    }
}

package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.security.StorageCredentialCipher;
import com.ymcloud.core.storage.security.StorageMasterKeyStore;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.pojo.entity.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 验证主密钥初始化、已有密钥保护和多实例并发安全
 */
class StorageMasterKeyStoreTest {

    @TempDir
    private Path root;

    @Test
    void firstSaveCreatesPrivateKeyAndAnotherInstanceCanDecryptWithoutConfigurationOrRestart() throws Exception {
        StorageProperties properties = new StorageProperties();
        StorageCredentialCipher first = cipher(properties, configs(false));
        assertFalse(Files.exists(keyFile()));
        String encrypted = first.encrypt("external-seaweed", "secretKey", "private-credential");
        assertNotEquals("private-credential", encrypted);
        byte[] original = Files.readAllBytes(keyFile());
        assertEquals(32, Base64.getDecoder().decode(original).length);
        assertPrivate(keyFile(), false);
        assertPrivate(keyFile().getParent(), true);
        StorageCredentialCipher next = cipher(new StorageProperties(), configs(true));
        assertEquals("private-credential", next.decrypt("external-seaweed", "secretKey", encrypted));
        assertArrayEquals(original, Files.readAllBytes(keyFile()));
        assertEquals(null, properties.getConfigEncryptionKey());
    }

    @Test
    void missingKeyForExistingDatabaseAndDecryptNeverGenerateReplacement() {
        StorageCredentialCipher cipher = cipher(new StorageProperties(), configs(true));
        assertThrows(FileException.class, cipher::requireKey);
        assertFalse(Files.exists(keyFile()));
        StorageCredentialCipher emptyDatabase = cipher(new StorageProperties(), configs(false));
        assertThrows(FileException.class, () -> emptyDatabase.decrypt("minio", "secretKey", "value"));
        assertFalse(Files.exists(keyFile()));
    }

    @Test
    void invalidKeyIsNotOverwrittenAndDoesNotAppearInError() throws Exception {
        Files.createDirectories(keyFile().getParent());
        Files.writeString(keyFile(), "invalid-sensitive-key", StandardCharsets.UTF_8);
        FileException error = assertThrows(FileException.class,
                () -> cipher(new StorageProperties(), configs(false)).requireKey());
        assertFalse(error.getMessage().contains("invalid-sensitive-key"));
        assertEquals("invalid-sensitive-key", Files.readString(keyFile()));
    }

    @Test
    void explicitOriginalKeyTakesPrecedenceWithoutCreatingFilesOrQueryingDatabase() {
        StorageProperties properties = new StorageProperties();
        properties.setConfigEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        StorageConfigEntityService configs = mock(StorageConfigEntityService.class);
        StorageCredentialCipher cipher = cipher(properties, configs);
        assertEquals("value", cipher.decrypt("minio", "secretKey", cipher.encrypt("minio", "secretKey", "value")));
        assertFalse(Files.exists(keyFile()));
        verifyNoInteractions(configs);
    }

    @Test
    void sharedFileRootReusesKeyAndExistingRootPermissionsAreUnchanged() throws Exception {
        Path shared = Files.createDirectory(root.resolve("shared"));
        var before = Files.getFileAttributeView(shared, PosixFileAttributeView.class);
        var permissions = before == null ? null : before.readAttributes().permissions();
        StorageMasterKeyStore first = new StorageMasterKeyStore(shared.toString(), configs(false));
        StorageMasterKeyStore second = new StorageMasterKeyStore(shared.toString(), configs(true));
        assertArrayEquals(first.load(true), second.load(false));
        assertPrivate(shared.resolve(".secrets/storage-config.key"), false);
        if (permissions != null) {
            assertEquals(permissions, Files.getPosixFilePermissions(shared));
        }
    }

    @Test
    void concurrentInstancesGenerateOneKey() throws Exception {
        StorageConfigEntityService configs = configs(false);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Callable<byte[]> task = () -> new StorageMasterKeyStore(root.toString(), configs).load(true);
            var results = executor.invokeAll(List.of(task, task, task, task));
            byte[] expected = results.get(0).get(10, TimeUnit.SECONDS);
            for (var result : results) {
                assertArrayEquals(expected, result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void legacyKeyMigratesWithoutYamlImportAndOnlyIfItMatchesStoredCredentials() throws Exception {
        Path files = root.resolve("files");
        Path originalFile = root.resolve("seaweedfs/secrets/storage-config.key");
        Files.createDirectories(originalFile.getParent());
        byte[] original = new byte[32];
        Files.writeString(originalFile, Base64.getEncoder().encodeToString(original), StandardCharsets.UTF_8);
        StorageProperties old = new StorageProperties();
        old.setConfigEncryptionKey(Base64.getEncoder().encodeToString(original));
        StorageCredentialCipher oldCipher = new StorageCredentialCipher(old);
        StorageConfig record = new StorageConfig();
        record.setId("legacy");
        record.setEncryptedAccessKey(oldCipher.encrypt("legacy", "accessKey", "access"));
        record.setEncryptedSecretKey(oldCipher.encrypt("legacy", "secretKey", "secret"));
        StorageConfigEntityService configs = configs(true);
        when(configs.lambdaQuery().list()).thenReturn(List.of(record));
        StorageMasterKeyStore store = new StorageMasterKeyStore(files.toString(), configs);
        assertArrayEquals(original, store.load(false));
        assertArrayEquals(original, Base64.getDecoder().decode(Files.readString(files.resolve(".secrets/storage-config.key"))));
        assertArrayEquals(original, Base64.getDecoder().decode(Files.readString(originalFile)));
    }

    @Test
    void unrelatedLegacyKeyDoesNotReplaceMissingDatabaseKey() throws Exception {
        Path files = root.resolve("files");
        Path legacy = root.resolve("seaweedfs/secrets/storage-config.key");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, Base64.getEncoder().encodeToString(new byte[32]), StandardCharsets.UTF_8);
        StorageConfig record = new StorageConfig();
        record.setId("legacy");
        record.setEncryptedAccessKey("invalid-ciphertext");
        StorageConfigEntityService configs = configs(true);
        when(configs.lambdaQuery().list()).thenReturn(List.of(record));
        assertThrows(FileException.class,
                () -> new StorageMasterKeyStore(files.toString(), configs).load(true));
        assertFalse(Files.exists(files.resolve(".secrets/storage-config.key")));
    }

    @Test
    void keyDirectoryCannotBeReplacedByOrdinaryFile() throws Exception {
        Files.writeString(root.resolve(".secrets"), "blocked", StandardCharsets.UTF_8);
        assertThrows(FileException.class, () -> cipher(new StorageProperties(), configs(false)).requireKey());
        assertEquals("blocked", Files.readString(root.resolve(".secrets")));
    }

    /**
     * 向加密组件注入隔离的本地密钥库
     *
     * @param properties 本用例的存储配置
     * @param configs 存储配置数据访问替身
     * @return 使用临时目录的加密组件
     */
    private StorageCredentialCipher cipher(StorageProperties properties, StorageConfigEntityService configs) {
        StorageCredentialCipher cipher = new StorageCredentialCipher(properties);
        ReflectionTestUtils.setField(cipher, "keyStore", new StorageMasterKeyStore(root.toString(), configs));
        return cipher;
    }

    /**
     * 模拟是否存在需要主密钥解密的非本地配置
     * @param existing 是否存在非本地配置
     * @return 存储配置数据访问替身
     */
    @SuppressWarnings("unchecked")
    private StorageConfigEntityService configs(boolean existing) {
        StorageConfigEntityService configs = mock(StorageConfigEntityService.class);
        LambdaQueryChainWrapper<StorageConfig> query = mock(LambdaQueryChainWrapper.class, org.mockito.Mockito.RETURNS_SELF);
        when(configs.lambdaQuery()).thenReturn(query);
        when(query.ne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.exists()).thenReturn(existing);
        return configs;
    }

    private Path keyFile() {
        return root.resolve(".secrets/storage-config.key");
    }

    private void assertPrivate(Path path, boolean directory) throws Exception {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            assertEquals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"),
                    Files.getPosixFilePermissions(path));
        } else {
            var view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            assertTrue(view.getAcl().stream().filter(entry -> entry.type() == AclEntryType.ALLOW)
                    .allMatch(entry -> entry.principal().getName().endsWith("\\" + System.getProperty("user.name"))
                            || entry.principal().getName().endsWith("\\SYSTEM")));
        }
    }
}

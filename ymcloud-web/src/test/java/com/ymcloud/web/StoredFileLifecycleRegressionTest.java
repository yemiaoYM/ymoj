package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.core.storage.LocalObjectStorageService;
import com.ymcloud.core.storage.ObjectStorageService;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 使用真实本地对象验证事务回调固定物理地址及历史迁移保留原件。
 */
class StoredFileLifecycleRegressionTest {

    @TempDir
    private Path root;

    private FileEntityService entities;
    private StoredFileService storedFiles;
    private StorageRegistry registry;

    @BeforeEach
    void setup() {
        entities = mock(FileEntityService.class);
        registry = new StorageRegistry(new StorageProperties(), root.toString());
        storedFiles = new StoredFileService(entities, registry);
        when(entities.save(any(File.class))).thenAnswer(invocation -> {
            invocation.<File>getArgument(0).setId(10L);
            return true;
        });
    }

    @AfterEach
    void cleanupTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        registry.close();
    }

    @Test
    void rollbackDeletesSavedObjectEvenWhenReturnedEntityLocationChanges() throws Exception {
        File unrelated = saveImage();
        Path unrelatedPath = root.resolve(unrelated.getStoragePath());
        TransactionSynchronizationManager.initSynchronization();
        File uploaded = saveImage();
        Path uploadedPath = root.resolve(uploaded.getStoragePath());

        uploaded.setStorageProfile(unrelated.getStorageProfile());
        uploaded.setBucket(unrelated.getBucket());
        uploaded.setObjectKey(unrelated.getObjectKey());
        uploaded.setStoragePath(unrelated.getStoragePath());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertFalse(Files.exists(uploadedPath));
        assertTrue(Files.exists(unrelatedPath));
    }

    @Test
    void committedSaveRetainsNewObject() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        File uploaded = saveImage();
        Path uploadedPath = root.resolve(uploaded.getStoragePath());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(Files.exists(uploadedPath));
    }

    @Test
    void deleteWaitsForCommitAndUsesOriginalPhysicalAddressDespiteEntityMutation() throws Exception {
        File original = saveImage();
        Path originalPath = root.resolve(original.getStoragePath());
        File unrelated = saveImage();
        Path unrelatedPath = root.resolve(unrelated.getStoragePath());
        configureDelete(original);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(10L);
        assertTrue(Files.exists(originalPath));
        original.setStorageProfile(unrelated.getStorageProfile());
        original.setBucket(unrelated.getBucket());
        original.setObjectKey(unrelated.getObjectKey());
        original.setStoragePath(unrelated.getStoragePath());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertFalse(Files.exists(originalPath));
        assertTrue(Files.exists(unrelatedPath));
    }

    @Test
    void rollbackDoesNotPhysicallyDeleteExistingObject() throws Exception {
        File original = saveImage();
        Path originalPath = root.resolve(original.getStoragePath());
        configureDelete(original);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(10L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(Files.exists(originalPath));
    }

    @Test
    void committedLegacyDeleteRemovesOriginalSoPublicFallbackCannotServeIt() throws Exception {
        File legacy = legacyFile();
        Path source = Path.of(legacy.getStoragePath());
        configureDelete(legacy);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(42L);
        assertTrue(Files.exists(source));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertFalse(Files.exists(source));
    }

    @Test
    void rolledBackLegacyDeleteRetainsOriginal() throws Exception {
        File legacy = legacyFile();
        Path source = Path.of(legacy.getStoragePath());
        configureDelete(legacy);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(42L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(Files.exists(source));
    }

    @Test
    void committedMigratedDeleteRemovesBothNewObjectAndLegacyOriginal() throws Exception {
        File legacy = legacyFile();
        Path source = Path.of(legacy.getStoragePath());
        configureMigration(legacy);
        File migrated = storedFiles.migrateLocal(42L, true);
        Path object = root.resolve(migrated.getStoragePath());
        configureDelete(migrated);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(42L);
        assertTrue(Files.exists(object));
        assertTrue(Files.exists(source));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertFalse(Files.exists(object));
        assertFalse(Files.exists(source));
    }

    @Test
    void failedMigrationTransactionRemovesNewCopyAndPreservesLegacyBytesIdAndUrl() throws Exception {
        File legacy = legacyFile();
        Path legacyPath = Path.of(legacy.getStoragePath());
        configureMigration(legacy);
        TransactionSynchronizationManager.initSynchronization();

        File migrated = storedFiles.migrateLocal(42L, true);
        Path newPath = root.resolve(migrated.getStoragePath());
        assertTrue(Files.exists(newPath));
        assertEquals(42L, migrated.getId());
        assertEquals("/files/public/legacy.png", migrated.getUrl());
        assertEquals(legacyPath.toString(), migrated.getLegacyPath());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertFalse(Files.exists(newPath));
        assertTrue(Files.exists(legacyPath));
        assertEquals(7, Files.readAllBytes(legacyPath)[0]);
    }

    @Test
    void migrationDryRunAndRepeatedApplyDoNotCreateAdditionalObjects() throws Exception {
        File legacy = legacyFile();
        Path source = Path.of(legacy.getStoragePath());
        LambdaUpdateChainWrapper<File> migration = configureMigration(legacy);

        storedFiles.migrateLocal(42L, false);
        assertEquals(source.toString(), legacy.getStoragePath());
        File migrated = storedFiles.migrateLocal(42L, true);
        String objectKey = migrated.getObjectKey();
        storedFiles.migrateLocal(42L, true);

        assertEquals(objectKey, legacy.getObjectKey());
        verify(migration).update();
        try (var paths = Files.walk(root)) {
            assertEquals(2L, paths.filter(Files::isRegularFile).count());
        }
        assertTrue(Files.exists(source));
    }

    @Test
    void uploadRollbackUsesFixedClientEvenWhenConfigurationChangesAndDisappears() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        TransactionSynchronizationManager.initSynchronization();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getStoragePath());
        Path collision = rotateAndRemoveConfig(dynamic, file);
        assertEquals(0, dynamic.closed().get());

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertFalse(Files.exists(original));
        assertTrue(Files.exists(collision));
        assertEquals(1, dynamic.closed().get());
    }

    @Test
    void uploadCommitReleasesFixedClientWithoutRemovingCommittedObject() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        TransactionSynchronizationManager.initSynchronization();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getStoragePath());
        rotateAndRemoveConfig(dynamic, file);
        assertEquals(0, dynamic.closed().get());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(Files.exists(original));
        assertEquals(1, dynamic.closed().get());
    }

    @Test
    void deleteCommitUsesFixedClientAfterLastReferenceConfigurationWasRemoved() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getStoragePath());
        configureDelete(file);
        TransactionSynchronizationManager.initSynchronization();
        storedFiles.delete(file.getId());
        Path collision = rotateAndRemoveConfig(dynamic, file);
        assertEquals(0, dynamic.closed().get());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertFalse(Files.exists(original));
        assertTrue(Files.exists(collision));
        assertEquals(1, dynamic.closed().get());
    }

    @Test
    void deleteRollbackReleasesFixedClientAndRetainsExistingObject() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getStoragePath());
        configureDelete(file);
        TransactionSynchronizationManager.initSynchronization();
        storedFiles.delete(file.getId());
        rotateAndRemoveConfig(dynamic, file);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(Files.exists(original));
        assertEquals(1, dynamic.closed().get());
    }

    private DynamicStorage dynamicStorage() {
        registry.close();
        StorageProperties properties = new StorageProperties();
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setEndpoint("first");
        properties.getProfiles().put("dynamic", profile);
        properties.setActive("dynamic");
        AtomicInteger closed = new AtomicInteger();
        registry = new StorageRegistry(properties, root.toString()) {
            @Override
            public ObjectStorageService createClient(StorageProperties.Profile value) {
                ObjectStorageService driver = new LocalObjectStorageService(root.resolve(value.getEndpoint()));
                return new ObjectStorageService() {
                    @Override
                    public void put(String bucket, String key, Path source, String type, String hash) {
                        driver.put(bucket, key, source, type, hash);
                    }

                    @Override
                    public InputStream open(String bucket, String key) {
                        return driver.open(bucket, key);
                    }

                    @Override
                    public void delete(String bucket, String key) {
                        driver.delete(bucket, key);
                    }

                    @Override
                    public void close() {
                        closed.incrementAndGet();
                        driver.close();
                    }
                };
            }
        };
        storedFiles = new StoredFileService(entities, registry);
        return new DynamicStorage(properties, profile, closed);
    }

    private Path rotateAndRemoveConfig(DynamicStorage dynamic, File file) throws Exception {
        dynamic.profile().setEndpoint("second");
        Path collision = root.resolve("second").resolve(file.getStoragePath());
        Files.createDirectories(collision.getParent());
        Files.write(collision, new byte[]{2});
        registry.client("dynamic").delete(file.getBucket(), "rotation-trigger");
        dynamic.properties().getProfiles().remove("dynamic");
        return collision;
    }

    private record DynamicStorage(StorageProperties properties, StorageProperties.Profile profile, AtomicInteger closed) {
    }

    private File saveImage() {
        return storedFiles.save(new ByteArrayInputStream(new byte[]{1}), 1L, "avatar.png", "image/png",
                FileBizType.IMAGE, 2L, true);
    }

    private File legacyFile() throws Exception {
        Path path = root.resolve("public/legacy.png");
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{7});
        File file = new File();
        file.setId(42L);
        file.setStorageType(FileConstants.STORAGE_LOCAL);
        file.setStoragePath(path.toString());
        file.setFileSize(1L);
        file.setFileName("legacy.png");
        file.setFileType("image/png");
        file.setIsPublic(FileConstants.ACCESS_PUBLIC);
        file.setBizType(FileBizType.IMAGE);
        file.setUrl("/files/public/legacy.png");
        return file;
    }

    @SuppressWarnings("unchecked")
    private void configureDelete(File file) {
        LambdaUpdateChainWrapper<File> update = mock(LambdaUpdateChainWrapper.class);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.remove()).thenReturn(true);
        when(entities.lambdaUpdate()).thenReturn(update);
        LambdaQueryChainWrapper<File> query = mock(LambdaQueryChainWrapper.class);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last(eq("FOR UPDATE"))).thenReturn(query);
        when(query.one()).thenReturn(file);
        when(entities.lambdaQuery()).thenReturn(query);
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateChainWrapper<File> configureMigration(File file) {
        LambdaQueryChainWrapper<File> query = mock(LambdaQueryChainWrapper.class);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last(eq("FOR UPDATE"))).thenReturn(query);
        when(query.one()).thenReturn(file);
        when(entities.lambdaQuery()).thenReturn(query);
        LambdaUpdateChainWrapper<File> update = mock(LambdaUpdateChainWrapper.class);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.set(any(), any())).thenReturn(update);
        when(update.update()).thenReturn(true);
        when(entities.lambdaUpdate()).thenReturn(update);
        return update;
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(status));
    }
}

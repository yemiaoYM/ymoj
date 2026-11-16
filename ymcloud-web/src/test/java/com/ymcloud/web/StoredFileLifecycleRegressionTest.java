package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.core.storage.client.LocalObjectStorageService;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.file.impl.FileServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 使用真实本地对象验证事务回调固定物理地址与客户端生命周期
 */
class StoredFileLifecycleRegressionTest {

    @TempDir
    private Path root;

    private FileEntityService entities;
    private FileService storedFiles;
    private StorageRegistry registry;

    /**
     * 装配临时目录存储及文件元数据依赖
     */
    @BeforeEach
    void setup() {
        entities = mock(FileEntityService.class);
        registry = new StorageRegistry(new StorageProperties(), root.toString());
        storedFiles = new FileServiceImpl();
        ReflectionTestUtils.setField(storedFiles, "files", entities);
        ReflectionTestUtils.setField(storedFiles, "storage", registry);
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

    /**
     * 上传回滚删除原始对象位置，不受返回实体修改影响
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void rollbackDeletesSavedObjectEvenWhenReturnedEntityLocationChanges() throws Exception {
        File unrelated = saveImage();
        Path unrelatedPath = root.resolve(unrelated.getBucket() + "/" + unrelated.getObjectKey());
        TransactionSynchronizationManager.initSynchronization();
        File uploaded = saveImage();
        Path uploadedPath = root.resolve(uploaded.getBucket() + "/" + uploaded.getObjectKey());

        uploaded.setStorageKey(unrelated.getStorageKey());
        uploaded.setBucket(unrelated.getBucket());
        uploaded.setObjectKey(unrelated.getObjectKey());

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertFalse(Files.exists(uploadedPath));
        assertTrue(Files.exists(unrelatedPath));
    }

    /**
     * 上传事务提交保留新对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void committedSaveRetainsNewObject() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        File uploaded = saveImage();
        Path uploadedPath = root.resolve(uploaded.getBucket() + "/" + uploaded.getObjectKey());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(Files.exists(uploadedPath));
    }

    /**
     * 删除等待事务提交并固定原始对象位置
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void deleteWaitsForCommitAndUsesOriginalPhysicalAddressDespiteEntityMutation() throws Exception {
        File original = saveImage();
        Path originalPath = root.resolve(original.getBucket() + "/" + original.getObjectKey());
        File unrelated = saveImage();
        Path unrelatedPath = root.resolve(unrelated.getBucket() + "/" + unrelated.getObjectKey());
        configureDelete(original);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(10L);
        assertTrue(Files.exists(originalPath));
        original.setStorageKey(unrelated.getStorageKey());
        original.setBucket(unrelated.getBucket());
        original.setObjectKey(unrelated.getObjectKey());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertFalse(Files.exists(originalPath));
        assertTrue(Files.exists(unrelatedPath));
    }

    /**
     * 删除事务回滚保留原对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void rollbackDoesNotPhysicallyDeleteExistingObject() throws Exception {
        File original = saveImage();
        Path originalPath = root.resolve(original.getBucket() + "/" + original.getObjectKey());
        configureDelete(original);
        TransactionSynchronizationManager.initSynchronization();

        storedFiles.delete(10L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(Files.exists(originalPath));
    }

    /**
     * 配置更换或移除后，上传回滚仍使用原客户端清理对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void uploadRollbackUsesFixedClientEvenWhenConfigurationChangesAndDisappears() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        TransactionSynchronizationManager.initSynchronization();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getBucket() + "/" + file.getObjectKey());
        Path collision = rotateAndRemoveConfig(dynamic, file);
        assertEquals(0, dynamic.closed().get());

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertFalse(Files.exists(original));
        assertTrue(Files.exists(collision));
        assertEquals(1, dynamic.closed().get());
    }

    /**
     * 上传提交释放固定客户端并保留已提交对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void uploadCommitReleasesFixedClientWithoutRemovingCommittedObject() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        TransactionSynchronizationManager.initSynchronization();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getBucket() + "/" + file.getObjectKey());
        rotateAndRemoveConfig(dynamic, file);
        assertEquals(0, dynamic.closed().get());

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(Files.exists(original));
        assertEquals(1, dynamic.closed().get());
    }

    /**
     * 主动文件删除在配置移除后仍使用固定客户端，不误删新配置中的同名对象
     * @throws Exception 临时文件操作失败
     */
    @Test
    void deleteCommitUsesFixedClientAfterLastReferenceConfigurationWasRemoved() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getBucket() + "/" + file.getObjectKey());
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

    /**
     * 删除回滚释放固定客户端且保留原对象
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void deleteRollbackReleasesFixedClientAndRetainsExistingObject() throws Exception {
        DynamicStorage dynamic = dynamicStorage();
        File file = saveImage();
        Path original = root.resolve("first").resolve(file.getBucket() + "/" + file.getObjectKey());
        configureDelete(file);
        TransactionSynchronizationManager.initSynchronization();
        storedFiles.delete(file.getId());
        rotateAndRemoveConfig(dynamic, file);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(Files.exists(original));
        assertEquals(1, dynamic.closed().get());
    }

    /**
     * 构造可切换物理根目录的存储，并统计客户端关闭次数
     * @return 动态配置、参数及关闭计数
     */
    private DynamicStorage dynamicStorage() {
        registry.close();
        StorageProperties properties = new StorageProperties();
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setDriverType(StorageDriverType.LOCAL);
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
        storedFiles = new FileServiceImpl();
        ReflectionTestUtils.setField(storedFiles, "files", entities);
        ReflectionTestUtils.setField(storedFiles, "storage", registry);
        return new DynamicStorage(properties, profile, closed);
    }

    /**
     * 轮换存储客户端并删除配置，构造同键对象验证租约隔离
     * @param dynamic 待轮换的动态存储配置
     * @param file 原客户端写入的文件
     * @return 新客户端目录中的同键文件
     * @throws Exception 测试对象写入失败
     */
    private Path rotateAndRemoveConfig(DynamicStorage dynamic, File file) throws Exception {
        dynamic.profile().setEndpoint("second");
        Path collision = root.resolve("second").resolve(file.getBucket() + "/" + file.getObjectKey());
        Files.createDirectories(collision.getParent());
        Files.write(collision, new byte[]{2});
        registry.client("dynamic").delete(file.getBucket(), "rotation-trigger");
        dynamic.properties().getProfiles().remove("dynamic");
        return collision;
    }

    private record DynamicStorage(StorageProperties properties, StorageProperties.Profile profile, AtomicInteger closed) {}

    /**
     * 写入公开图片并返回已登记的元数据
     * @return 本轮创建的图片记录
     */
    private File saveImage() {
        return storedFiles.create(new CreateStoredFileCommand("avatar.png", "image/png", "image", FileBizType.IMAGE, 2L, true, null, null),
                new ByteArrayInputStream(new byte[]{1}), 1L);
    }

    /**
     * 配置删除目标的行锁查询及元数据删除结果
     * @param file 待删除文件
     */
    @SuppressWarnings("unchecked")
    private void configureDelete(File file) {
        LambdaUpdateChainWrapper<File> update = mock(LambdaUpdateChainWrapper.class);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.remove()).thenReturn(true);
        when(entities.lambdaUpdate()).thenReturn(update);
        LambdaQueryChainWrapper<File> query = mock(LambdaQueryChainWrapper.class);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last("FOR UPDATE")).thenReturn(query);
        when(query.and(any())).thenReturn(query);
        when(query.one()).thenReturn(file);
        when(entities.lambdaQuery()).thenReturn(query);
    }

    /**
     * 触发事务完成回调，验证客户端释放和回滚保留文件
     * @param status 事务提交或回滚状态
     */
    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(status));
    }
}

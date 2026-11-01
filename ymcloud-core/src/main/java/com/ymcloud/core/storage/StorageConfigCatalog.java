package com.ymcloud.core.storage;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.dao.StorageConfigStateEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 数据库是动态配置和默认选择的唯一来源
 * 上传事务锁定状态，避免定位字段竞态
 */
@Service
public class StorageConfigCatalog {

    private final StorageConfigEntityService configs;
    private final StorageConfigStateEntityService states;
    private final FileEntityService files;
    private final StorageCredentialCipher cipher;
    private final StorageProperties properties;

    public StorageConfigCatalog(StorageConfigEntityService configs, StorageConfigStateEntityService states,
                                FileEntityService files, StorageCredentialCipher cipher, StorageProperties properties) {
        this.configs = configs;
        this.states = states;
        this.files = files;
        this.cipher = cipher;
        this.properties = properties;
    }

    public String active() {
        StorageConfigState state = readState(TransactionSynchronizationManager.isActualTransactionActive(), true);
        return state == null || state.getActiveKey() == null ? properties.getActive() : state.getActiveKey();
    }

    /**
     * 按配置名读取存储配置，业务事务中持有共享锁以稳定对象定位
     * @param key 存储配置名
     * @return 动态配置，不存在时返回空
     */
    public StorageConfig find(String key) {
        StorageConfigValidation.validateKey(key);
        boolean transaction = TransactionSynchronizationManager.isActualTransactionActive();
        // 上传事务共持共享锁，后台写入取独占锁
        // 普通历史读取只需读取单条配置 revision
        if (transaction) {
            readState(true, true);
        }
        try {
            if (transaction) {
                return exactKey(configs.getByIdForShare(key), key);
            }
            return exactKey(configs.lambdaQuery()
                    .eq(StorageConfig::getId, key)
                    .one(), key);
        } catch (DataAccessException e) {
            if (missingTable(e)) {
                return null;
            }
            throw new FileException("无法读取存储配置");
        }
    }

    public List<StorageConfig> list() {
        try {
            return configs.lambdaQuery()
                    .orderByAsc(StorageConfig::getId)
                    .list();
        } catch (DataAccessException e) {
            if (missingTable(e)) {
                return List.of();
            }
            throw new FileException("无法读取存储配置");
        }
    }

    /**
     * 配置状态锁不依赖密钥，密钥缺失时仍可切回本地并删除无引用的旧配置
     * @return 已锁定的存储配置状态
     */
    public StorageConfigState lockForWrite() {
        return readState(true, false, true);
    }

    /**
     * 检查存储配置是否仍被文件登记引用
     * @param key 已校验的配置名
     * @return 是否仍被业务文件引用
     */
    public boolean referenced(String key) {
        return files.lambdaQuery()
                .eq(File::getStorageProfile, key)
                .exists();
    }

    /**
     * 恢复缺失配置前读取全部引用，调用方持有配置状态独占锁
     */
    public List<File> referencedFiles(String key) {
        StorageConfigValidation.validateKey(key);
        return files.lambdaQuery()
                .eq(File::getStorageProfile, key)
                .orderByAsc(File::getId)
                .list();
    }

    public StorageProperties.Profile profile(StorageConfig config) {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setType("aliyun".equals(config.getProvider()) ? "oss" : "s3");
        profile.setEndpoint(config.getEndpoint());
        profile.setRegion(config.getRegion());
        profile.setPublicBucket(config.getPublicBucket());
        profile.setPrivateBucket(config.getPrivateBucket());
        profile.setPathStyle(Boolean.TRUE.equals(config.getPathStyle()));
        profile.setAccessKey(cipher.decrypt(config.getId(), "accessKey", config.getEncryptedAccessKey()));
        profile.setSecretKey(cipher.decrypt(config.getId(), "secretKey", config.getEncryptedSecretKey()));
        return profile;
    }

    public void save(StorageConfig config, boolean exists) {
        if (!exists) {
            if (!configs.save(config)) {
                throw new FileException("保存存储配置失败");
            }
            return;
        }
        if (!configs.lambdaUpdate()
                .eq(StorageConfig::getId, config.getId())
                .set(StorageConfig::getName, config.getName())
                .set(StorageConfig::getProvider, config.getProvider())
                .set(StorageConfig::getEndpoint, config.getEndpoint())
                .set(StorageConfig::getRegion, config.getRegion())
                .set(StorageConfig::getPublicBucket, config.getPublicBucket())
                .set(StorageConfig::getPrivateBucket, config.getPrivateBucket())
                .set(StorageConfig::getPathStyle, config.getPathStyle())
                .set(StorageConfig::getEncryptedAccessKey, config.getEncryptedAccessKey())
                .set(StorageConfig::getEncryptedSecretKey, config.getEncryptedSecretKey())
                .set(StorageConfig::getRemark, config.getRemark())
                .set(StorageConfig::getRevision, config.getRevision())
                .set(StorageConfig::getTestedRevision, config.getTestedRevision())
                .set(StorageConfig::getTestedAt, config.getTestedAt())
                .set(StorageConfig::getUpdatedTime, config.getUpdatedTime())
                .update()) {
            throw new FileException("保存存储配置失败");
        }
    }

    public void remove(String key) {
        if (!configs.lambdaUpdate()
                .eq(StorageConfig::getId, key)
                .remove()) {
            throw new FileException("删除存储配置失败");
        }
    }

    public void activate(StorageConfigState state, String key) {
        if (!states.lambdaUpdate()
                .eq(StorageConfigState::getId, 1)
                .set(StorageConfigState::getActiveKey, key)
                .set(StorageConfigState::getRevision, state.getRevision() + 1)
                .set(StorageConfigState::getUpdatedTime, LocalDateTime.now())
                .update()) {
            throw new FileException("切换存储配置失败");
        }
    }

    /**
     * 读取状态，事务读取按需获取共享锁
     * @param lock 是否加锁
     * @param allowMissing 是否允许尚未配置动态存储
     * @return 配置状态，允许缺失时可为空
     */
    private StorageConfigState readState(boolean lock, boolean allowMissing) {
        return readState(lock, allowMissing, false);
    }

    /**
     * 按读写用途锁定配置状态，保证配置切换和文件登记互斥
     * @param lock 是否加锁
     * @param allowMissing 是否允许尚未配置动态存储
     * @param exclusive 是否使用写入独占锁
     * @return 配置状态，允许缺失时可为空
     */
    private StorageConfigState readState(boolean lock, boolean allowMissing, boolean exclusive) {
        try {
            StorageConfigState state;
            if (lock) {
                state = states.getForLock(exclusive);
            } else {
                state = states.lambdaQuery()
                        .eq(StorageConfigState::getId, 1)
                        .one();
            }
            if (state == null && !allowMissing) {
                throw new FileException("请先执行存储配置数据库迁移");
            }
            return state;
        } catch (DataAccessException e) {
            if (allowMissing && missingTable(e)) {
                return null;
            }
            throw new FileException("无法读取存储配置状态，请检查数据库迁移");
        }
    }

    private boolean missingTable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql && (sql.getErrorCode() == 1146
                    || "42S02".equals(sql.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private StorageConfig exactKey(StorageConfig config, String key) {
        if (config != null && !key.equals(config.getId())) {
            throw new FileException("存储配置名区分大小写，请使用原配置名", HttpStatus.BAD_REQUEST);
        }
        return config;
    }
}

package com.ymcloud.web.service.file.impl;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageConfigValidation;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.security.StorageCredentialCipher;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.web.service.file.StorageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 数据库是动态配置和默认选择的唯一来源
 * 上传事务锁定状态，避免定位字段竞态
 */
@Service
public class StorageConfigServiceImpl implements StorageConfigService {

    @Autowired
    private StorageConfigEntityService configs;

    @Autowired
    private FileEntityService files;

    @Autowired
    private StorageCredentialCipher cipher;

    /**
     * 读取当前默认存储配置标识
     * @return 默认配置标识
     */
    @Override
    public String active() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            lockLocal(false);
        }
        var query = configs.lambdaQuery()
                .eq(StorageConfig::getIsDefault, true);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            query.last("LOCK IN SHARE MODE");
        }
        StorageConfig active = query.one();
        if (active == null) {
            throw new FileException("默认存储配置缺失");
        }
        return active.getId();
    }

    /**
     * 按配置名读取存储配置，业务事务中持有共享锁以稳定对象定位
     * @param key 存储配置名
     * @return 动态配置，不存在时返回空
     */
    @Override
    public StorageConfig find(String key) {
        StorageConfigValidation.validateKey(key);
        boolean transaction = TransactionSynchronizationManager.isActualTransactionActive();
        // 上传事务共持共享锁，后台写入取独占锁
        // 普通历史读取只需读取单条配置 revision
        if (transaction) {
            lockLocal(false);
        }
        if (transaction) {
            return exactKey(configs.getByIdForShare(key), key);
        }
        return exactKey(configs.lambdaQuery()
                .eq(StorageConfig::getId, key)
                .one(), key);
    }

    /**
     * 列出动态存储配置
     * @return 动态配置列表
     */
    @Override
    public List<StorageConfig> list() {
        return configs.lambdaQuery()
                .orderByAsc(StorageConfig::getId)
                .list();
    }

    /**
     * 配置状态锁不依赖密钥，密钥缺失时仍可切回本地并删除无引用的旧配置
     */
    @Override
    public void lockForWrite() {
        lockLocal(true);
    }

    /**
     * 检查存储配置是否仍被文件登记引用
     * @param key 已校验的配置名
     * @return 是否仍被业务文件引用
     */
    @Override
    public boolean referenced(String key) {
        return files.lambdaQuery()
                .eq(File::getStorageKey, key)
                .exists();
    }

    /**
     * 恢复缺失配置前读取全部引用，调用方持有配置状态独占锁
     * @param key 待恢复的配置标识
     * @return 按文件编号排序的引用记录，无引用时为空列表
     */
    @Override
    public List<File> referencedFiles(String key) {
        StorageConfigValidation.validateKey(key);
        return files.lambdaQuery()
                .eq(File::getStorageKey, key)
                .orderByAsc(File::getId)
                .list();
    }

    /**
     * 解密并组装内部客户端配置
     * @param config 待处理的动态存储配置
     * @return 仅内部使用的客户端配置
     */
    @Override
    public StorageProperties.Profile profile(StorageConfig config) {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setDriverType(config.getDriverType());
        profile.setProvider(config.getProvider());
        profile.setEndpoint(config.getEndpoint());
        profile.setRegion(config.getRegion());
        profile.setPublicBucket(config.getPublicBucket());
        profile.setPrivateBucket(config.getPrivateBucket());
        profile.setPathStyle(Boolean.TRUE.equals(config.getPathStyle()));
        if (config.getDriverType() != StorageDriverType.LOCAL) {
            profile.setAccessKey(cipher.decrypt(config.getId(), "accessKey", config.getEncryptedAccessKey()));
            profile.setSecretKey(cipher.decrypt(config.getId(), "secretKey", config.getEncryptedSecretKey()));
        }
        return profile;
    }

    /**
     * 保存动态存储配置，调用方持有配置状态锁
     * @param config 待处理的动态存储配置
     * @param exists 是否更新已有配置
     */
    @Override
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
                .set(StorageConfig::getDriverType, config.getDriverType())
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

    /**
     * 删除指定存储配置
     * @param key 存储配置标识
     */
    @Override
    public void remove(String key) {
        if (!configs.lambdaUpdate()
                .eq(StorageConfig::getId, key)
                .remove()) {
            throw new FileException("删除存储配置失败");
        }
    }

    /**
     * 在调用方持有本地配置写锁的事务内切换唯一默认配置
     * @param key 已验证且存在的目标配置标识
     */
    @Override
    public void activate(String key) {
        configs.lambdaUpdate()
                .eq(StorageConfig::getIsDefault, true)
                .set(StorageConfig::getIsDefault, false)
                .update();
        if (!configs.lambdaUpdate()
                .eq(StorageConfig::getId, key)
                .set(StorageConfig::getIsDefault, true)
                .update()) {
            throw new FileException("切换存储配置失败");
        }
    }

    /**
     * 使用不可删除的本地配置行协调上传和配置变更，不额外维护状态表
     * @param exclusive 配置写入使用独占锁，上传使用共享锁
     * @throws FileException 本地配置未初始化
     */
    private void lockLocal(boolean exclusive) {
        StorageConfig local;
        if (exclusive) {
            local = configs.lambdaQuery()
                    .eq(StorageConfig::getId, "local")
                    .last("FOR UPDATE")
                    .one();
        } else {
            local = configs.getByIdForShare("local");
        }
        if (local == null) {
            throw new FileException("本地存储配置未初始化");
        }
    }

    /**
     * 核对数据库查询结果的大小写，避免排序规则将不同配置名视为相同
     * @param config 查询结果，不存在时可为空
     * @param key 调用方请求的配置标识
     * @return 原查询结果
     * @throws FileException 查询结果与请求标识大小写不一致
     */
    private StorageConfig exactKey(StorageConfig config, String key) {
        if (config != null && !key.equals(config.getId())) {
            throw new FileException("存储配置名区分大小写，请使用原配置名", HttpStatus.BAD_REQUEST);
        }
        return config;
    }
}

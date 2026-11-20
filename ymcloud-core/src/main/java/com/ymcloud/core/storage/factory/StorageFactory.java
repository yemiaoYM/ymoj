package com.ymcloud.core.storage.factory;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.core.storage.client.DefaultStorageClientImpl;
import com.ymcloud.core.storage.client.StorageClient;
import com.ymcloud.core.storage.config.StorageClientConfig;
import com.ymcloud.core.storage.exception.S3StorageException;
import com.ymcloud.core.storage.properties.StorageProperties;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.pojo.entity.StorageConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 按配置标识获取和复用 S3 存储客户端
 */
@Component
public class StorageFactory {

    @Autowired
    private StorageConfigEntityService configs;

    private final Map<String, StorageClient> clients = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean closed;

    /**
     * 获取当前默认配置的客户端
     * @return 工厂管理的共享客户端
     */
    public StorageClient instance() {
        StorageConfig config = configs.getDefaultForShare();
        if (config == null) {
            throw new S3StorageException("未设置默认存储配置");
        }
        return client(config);
    }

    /**
     * 按配置标识获取客户端，配置变化时创建新实例并释放旧连接
     * @param configKey 配置标识
     * @return 对应当前配置的共享客户端
     */
    public StorageClient instance(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new S3StorageException("存储配置标识不能为空");
        }
        StorageConfig config = configs.getByConfigKey(configKey);
        if (config == null) {
            throw new S3StorageException("存储配置不存在");
        }
        return client(config);
    }

    /**
     * 为数据库配置复用或创建客户端
     * @param row 本次操作读取的存储配置
     * @return 使用该配置的客户端
     */
    private StorageClient client(StorageConfig row) {
        StorageClientConfig config = StorageClientConfig.fromProperties(BeanUtil.copyProperties(row, StorageProperties.class));
        var lock = lifecycle.readLock();
        lock.lock();
        try {
            if (closed) {
                throw new S3StorageException("存储工厂已关闭");
            }
            return clients.compute(row.getConfigKey(), (key, previous) -> {
                if (previous != null && previous.isInitialized() && previous.verifyConfig(config)) {
                    return previous;
                }
                if (previous != null) {
                    previous.close();
                }
                return createClient(key, config);
            });
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据配置创建 S3 客户端，由工厂负责关闭
     * @param key 配置标识
     * @param config 已转换的客户端参数
     * @return 独立客户端
     */
    public StorageClient createClient(String key, StorageClientConfig config) {
        return new DefaultStorageClientImpl(key, config);
    }

    /**
     * 移除指定客户端并关闭连接，下次访问时根据配置重新创建
     * @param configKey 配置标识
     * @return 是否移除了已缓存的实例
     */
    public boolean remove(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return false;
        }
        var lock = lifecycle.readLock();
        lock.lock();
        try {
            StorageClient client = clients.remove(configKey);
            if (client == null) {
                return false;
            }
            client.close();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 应用停止时释放所有传输连接和执行器
     */
    @PreDestroy
    public void close() {
        var lock = lifecycle.writeLock();
        lock.lock();
        try {
            closed = true;
            clients.values().forEach(StorageClient::close);
            clients.clear();
        } finally {
            lock.unlock();
        }
    }
}

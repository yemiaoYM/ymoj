package com.ymcloud.core.storage.factory;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.ExceptionLogUtils;
import com.ymcloud.core.storage.client.LocalObjectStorageService;
import com.ymcloud.core.storage.client.ObjectStorageService;
import com.ymcloud.core.storage.client.OssObjectStorageService;
import com.ymcloud.core.storage.client.S3ObjectStorageService;
import com.ymcloud.core.storage.config.StorageConfigSource;
import com.ymcloud.core.storage.config.StorageConfigValidation;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.core.storage.util.StorageDirectoryResolver;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.enums.StorageDriverType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 按命名配置访问历史对象，默认配置切换仅影响新上传
 * 已打开的流持有原客户端租约
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StorageRegistry {

    private final StorageProperties properties;
    private final Path localRoot;
    private final Map<String, ClientLease> clients = new ConcurrentHashMap<>();
    private final Set<ClientLease> leases = ConcurrentHashMap.newKeySet();
    private volatile StorageConfigSource catalog;
    private volatile boolean closed;

    /**
     * 固定本地根目录并绑定存储配置，客户端按需创建
     * @param properties 命名存储配置和上传限制
     * @param localRoot 本地文件根目录配置
     */
    public StorageRegistry(StorageProperties properties, @Value("${ymcloud.file.base-dir}") String localRoot) {
        this.properties = properties;
        this.localRoot = StorageDirectoryResolver.resolve(localRoot);
    }

    /**
     * 接入动态存储配置源，独立迁移上下文可仅使用静态配置
     * @param catalog 动态配置读取接口
     */
    @Autowired(required = false)
    public void setCatalog(StorageConfigSource catalog) {
        this.catalog = catalog;
    }

    /**
     * 获取并校验当前默认存储配置
     * @return 可用的默认配置标识
     * @throws FileException 默认配置不存在或内容非法
     */
    public String active() {
        String name = catalog == null ? properties.getActive() : catalog.active();
        profile(name);
        return name;
    }

    /**
     * 启动时创建本地公开与私有目录，并校验默认存储配置
     */
    @PostConstruct
    public void validateActiveProfile() {
        StorageDirectoryResolver.ensureDirectories(localRoot);
        String name = active();
        log.info("文件存储已启用配置 {}，驱动 {}，本地根目录 {}", name, profile(name).getDriverType(), localRoot);
    }

    /**
     * 读取指定命名配置的当前参数
     * @param name 配置标识
     * @return 含内部凭据的客户端参数，不得用于对外响应
     */
    public StorageProperties.Profile profile(String name) {
        return snapshot(name).profile();
    }

    /**
     * 创建按最新配置执行操作的客户端视图，下载流关闭前保留对应租约
     * @param name 配置标识
     * @return 不固定配置版本的客户端视图，返回的内容流必须关闭
     */
    public ObjectStorageService client(String name) {
        StorageConfigValidation.validateKey(name);
        return new ObjectStorageService() {
            /**
             * 在一次上传期间持有客户端租约，结束后释放
             * @param bucket 目标存储桶
             * @param key 对象键
             * @param source 待上传本地文件
             * @param contentType 媒体类型
             * @param sha256 文件 SHA-256 摘要
             */
            @Override
            public void put(String bucket, String key, Path source, String contentType, String sha256) {
                ClientLease lease = acquire(name);
                try {
                    lease.driver.put(bucket, key, source, contentType, sha256);
                } finally {
                    lease.release();
                }
            }

            /**
             * 打开对象内容并将操作租约交给返回流，打开失败时释放租约
             * @param bucket 目标存储桶
             * @param key 对象键
             * @return 持有租约的内容流，调用方必须关闭
             */
            @Override
            public InputStream open(String bucket, String key) {
                ClientLease lease = acquire(name);
                try {
                    InputStream stream = lease.driver.open(bucket, key);
                    return new FilterInputStream(stream) {
                        private final AtomicBoolean released = new AtomicBoolean();

                        /**
                         * 关闭内容流并且只释放一次租约，即使底层关闭失败也释放
                         * @throws IOException 底层内容流关闭失败
                         */
                        @Override
                        public void close() throws IOException {
                            if (released.compareAndSet(false, true)) {
                                try {
                                    super.close();
                                } finally {
                                    lease.release();
                                }
                            }
                        }
                    };
                } catch (RuntimeException e) {
                    lease.release();
                    throw e;
                }
            }

            /**
             * 在删除期间持有客户端租约，结束后释放
             * @param bucket 目标存储桶
             * @param key 对象键
             */
            @Override
            public void delete(String bucket, String key) {
                ClientLease lease = acquire(name);
                try {
                    lease.driver.delete(bucket, key);
                } finally {
                    lease.release();
                }
            }
        };
    }

    /**
     * 固定当前物理配置，避免事务回调重新解析已变化的配置
     * @param name 配置标识
     * @return 绑定当前配置的客户端租约，调用方在事务回调结束时关闭
     */
    public ObjectStorageService lease(String name) {
        return new BoundClient(acquire(name));
    }

    /**
     * 获取启动时确定的本地存储根目录
     * @return 规范化的绝对根目录
     */
    public Path localRoot() {
        return localRoot;
    }

    /**
     * 获取上传文件的字节数上限
     * @return 单文件最大字节数
     */
    public long maxFileSize() {
        return properties.getMaxFileSize();
    }

    /**
     * 创建独立客户端，连接测试使用的实例不会进入正式连接缓存
     * @param profile 待创建客户端的配置
     * @return 独立客户端，由调用方关闭
     */
    public ObjectStorageService createClient(StorageProperties.Profile profile) {
        StorageConfigValidation.validate(profile, false);
        return switch (profile.getDriverType()) {
            case LOCAL -> new LocalObjectStorageService(localRoot);
            case S3 -> new S3ObjectStorageService(profile);
            case ALIYUN_OSS -> new OssObjectStorageService(profile);
        };
    }

    /**
     * 读取动态配置或静态配置并形成用于客户端缓存的版本快照
     * @param name 配置标识
     * @return 已校验参数及包含凭据变化的版本指纹
     */
    private Snapshot snapshot(String name) {
        StorageConfigValidation.validateKey(name);
        StorageConfig config = catalog == null ? null : catalog.find(name);
        StorageProperties.Profile profile;
        String revision;
        if (config != null) {
            profile = catalog.profile(config);
            revision = "database:" + config.getRevision() + ":" + fingerprint(profile);
        } else {
            profile = properties.getProfiles().get(name);
            if (profile == null && "local".equals(name)) {
                profile = new StorageProperties.Profile();
                profile.setDriverType(StorageDriverType.LOCAL);
            }
            if (profile == null) {
                throw new FileException("文件对应的存储配置未配置：" + name);
            }
            // 静态配置变化同样需要更换客户端，含凭据的指纹不记录或返回
            revision = "config:" + fingerprint(profile);
        }
        StorageConfigValidation.validate(profile, false);
        return new Snapshot(profile, revision);
    }

    /**
     * 计算配置指纹以识别未变更 revision 的参数变化，指纹不对外输出
     * @param profile 当前客户端参数
     * @return 参数内容的 SHA-256 十六进制摘要
     */
    private String fingerprint(StorageProperties.Profile profile) {
        String content = String.join("\u0000", String.valueOf(profile.getDriverType()),
                String.valueOf(profile.getEndpoint()), String.valueOf(profile.getRegion()),
                String.valueOf(profile.getPublicBucket()), String.valueOf(profile.getPrivateBucket()),
                String.valueOf(profile.isPathStyle()), String.valueOf(profile.getTimeout()),
                String.valueOf(profile.getAccessKey()), String.valueOf(profile.getSecretKey()));
        return HexFormat.of().formatHex(FileDigests.digest().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 按配置版本获取客户端租约，并使被替换客户端等待现有使用者释放
     * @param name 配置标识
     * @return 已增加引用计数的客户端租约
     * @throws FileException 服务已关闭或配置不可用
     */
    private ClientLease acquire(String name) {
        // 先获取数据库状态，避免持有 JVM 锁等待上传事务的数据库锁造成反向死锁
        Snapshot snapshot = snapshot(name);
        synchronized (this) {
            if (closed) {
                throw new FileException("存储服务正在关闭");
            }
            ClientLease lease = clients.get(name);
            if (lease == null || !lease.revision.equals(snapshot.revision())) {
                ClientLease replacement = new ClientLease(createClient(snapshot.profile()), snapshot.revision());
                leases.add(replacement);
                clients.put(name, replacement);
                if (lease != null) {
                    lease.retire();
                }
                lease = replacement;
            }
            lease.acquire();
            return lease;
        }
    }

    /**
     * 停止分配新租约，现有操作与流释放后再关闭对应客户端
     */
    @PreDestroy
    public synchronized void close() {
        closed = true;
        clients.clear();
        leases.forEach(ClientLease::retire);
    }

    /**
     * 一次配置读取的参数与缓存版本
     */
    private record Snapshot(StorageProperties.Profile profile, String revision) {}

    /**
     * 固定配置版本的客户端视图，自身和内容流分别持有引用
     */
    private final class BoundClient implements ObjectStorageService {
        private final ClientLease lease;
        private boolean released;

        /**
         * 接管固定配置的客户端租约
         * @param lease 已获取的租约
         */
        private BoundClient(ClientLease lease) {
            this.lease = lease;
        }

        /**
         * 使用固定配置上传对象，操作结束后释放额外引用
         * @param bucket 目标存储桶
         * @param key 对象键
         * @param source 待上传本地文件
         * @param type 媒体类型
         * @param hash 文件 SHA-256 摘要
         */
        @Override
        public void put(String bucket, String key, Path source, String type, String hash) {
            acquireOperation();
            try {
                lease.driver.put(bucket, key, source, type, hash);
            } finally {
                lease.release();
            }
        }

        /**
         * 打开对象内容并将操作租约交给返回流，打开失败时释放租约
         * @param bucket 目标存储桶
         * @param key 对象键
         * @return 持有租约的内容流，调用方必须关闭
         */
        @Override
        public InputStream open(String bucket, String key) {
            acquireOperation();
            try {
                return new FilterInputStream(lease.driver.open(bucket, key)) {
                    private final AtomicBoolean finished = new AtomicBoolean();

                    /**
                     * 关闭内容流并且只释放一次租约，即使底层关闭失败也释放
                     * @throws IOException 底层内容流关闭失败
                     */
                    @Override
                    public void close() throws IOException {
                        if (finished.compareAndSet(false, true)) {
                            try {
                                super.close();
                            } finally {
                                lease.release();
                            }
                        }
                    }
                };
            } catch (RuntimeException e) {
                lease.release();
                throw e;
            }
        }

        /**
         * 在删除期间持有客户端租约，结束后释放
         * @param bucket 目标存储桶
         * @param key 对象键
         */
        @Override
        public void delete(String bucket, String key) {
            acquireOperation();
            try {
                lease.driver.delete(bucket, key);
            } finally {
                lease.release();
            }
        }

        /**
         * 幂等释放客户端自身的租约引用，未关闭的流仍持有独立引用
         */
        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                lease.release();
            }
        }

        /**
         * 为固定配置上的一次操作增加引用，已关闭的客户端禁止新操作
         * @throws FileException 客户端租约已关闭
         */
        private synchronized void acquireOperation() {
            if (released) {
                throw new FileException("存储客户端租约已关闭");
            }
            lease.acquire();
        }
    }

    /**
     * 管理底层客户端的引用计数及退休后关闭时机
     */
    private final class ClientLease {
        private final ObjectStorageService driver;
        private final String revision;
        private int users;
        private boolean retired;
        private boolean disposed;

        /**
         * 记录底层客户端与对应配置版本
         * @param driver 待管理关闭时机的客户端
         * @param revision 配置版本与指纹
         */
        private ClientLease(ObjectStorageService driver, String revision) {
            this.driver = driver;
            this.revision = revision;
        }

        /**
         * 增加一次使用引用，调用方在流或操作结束后释放
         */
        private synchronized void acquire() {
            users++;
        }

        /**
         * 释放一次使用引用，已退休且无使用者时关闭底层客户端
         */
        private synchronized void release() {
            users--;
            disposeIfUnused();
        }

        /**
         * 标记客户端不再接收新租约，现有使用者释放后关闭
         */
        private synchronized void retire() {
            retired = true;
            disposeIfUnused();
        }

        /**
         * 关闭已退休且无使用者的客户端，并从待关闭集合中移除
         */
        private void disposeIfUnused() {
            if (retired && users == 0 && !disposed) {
                disposed = true;
                try {
                    driver.close();
                } catch (RuntimeException e) {
                    log.warn("存储客户端关闭失败", ExceptionLogUtils.forLogging(e));
                } finally {
                    leases.remove(this);
                }
            }
        }
    }
}

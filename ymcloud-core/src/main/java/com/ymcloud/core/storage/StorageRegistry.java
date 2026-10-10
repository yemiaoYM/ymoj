package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.pojo.entity.StorageConfig;
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

/** 按命名配置访问历史对象，数据库切换仅影响新上传；旧流持有连接租约。 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StorageRegistry {

    private final StorageProperties properties;
    private final Path localRoot;
    private final Map<String, ClientLease> clients = new ConcurrentHashMap<>();
    private final Set<ClientLease> leases = ConcurrentHashMap.newKeySet();
    private volatile StorageConfigCatalog catalog;
    private volatile boolean closed;

    public StorageRegistry(StorageProperties properties, @Value("${ymcloud.file.base-dir}") String localRoot) {
        this.properties = properties;
        this.localRoot = StorageDirectoryResolver.resolve(localRoot);
    }

    /** 窄测试/迁移上下文可以只使用 YAML，不强制加载数据库组件。 */
    @Autowired(required = false)
    public void setCatalog(StorageConfigCatalog catalog) {
        this.catalog = catalog;
    }

    public String active() {
        String name = catalog == null ? properties.getActive() : catalog.active();
        profile(name);
        return name;
    }

    @PostConstruct
    public void validateActiveProfile() {
        StorageDirectoryResolver.ensureDirectories(localRoot);
        String name = active();
        log.info("文件存储已启用配置 {}，驱动 {}，本地根目录 {}", name, profile(name).getType(), localRoot);
    }

    public StorageProperties.Profile profile(String name) {
        return snapshot(name).profile();
    }

    /** 每个操作按最新数据库 revision 获取租约，下载流关闭前旧客户端不会退休关闭。 */
    public ObjectStorageService client(String name) {
        StorageConfigValidation.validateKey(name);
        return new ObjectStorageService() {
            @Override
            public void put(String bucket, String key, Path source, String contentType, String sha256) {
                ClientLease lease = acquire(name);
                try {
                    lease.driver.put(bucket, key, source, contentType, sha256);
                } finally {
                    lease.release();
                }
            }

            @Override
            public InputStream open(String bucket, String key) {
                ClientLease lease = acquire(name);
                try {
                    InputStream stream = lease.driver.open(bucket, key);
                    return new FilterInputStream(stream) {
                        private final AtomicBoolean released = new AtomicBoolean();

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

    /** 固定当前物理配置的客户端租约，事务回调结束时必须关闭，避免回调重新解析可变配置。 */
    public ObjectStorageService lease(String name) {
        return new BoundClient(acquire(name));
    }

    public Path localRoot() {
        return localRoot;
    }

    public long maxFileSize() {
        return properties.getMaxFileSize();
    }

    /** 连接测试使用独立短生命周期客户端，避免将未验证配置放入正式连接缓存。 */
    public ObjectStorageService createClient(StorageProperties.Profile profile) {
        StorageConfigValidation.validate(profile, false);
        return switch (profile.getType()) {
            case "local" -> new LocalObjectStorageService(localRoot);
            case "s3" -> new S3ObjectStorageService(profile);
            case "oss" -> new OssObjectStorageService(profile);
            default -> throw new FileException("不支持的存储驱动");
        };
    }

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
            }
            if (profile == null) {
                throw new FileException("文件对应的存储配置未配置：" + name);
            }
            // YAML 在部分迁移/测试工具中可修改；指纹包含凭据，但不记录或返回。
            revision = "config:" + fingerprint(profile);
        }
        StorageConfigValidation.validate(profile, false);
        return new Snapshot(profile, revision);
    }

    private String fingerprint(StorageProperties.Profile profile) {
        String content = String.join("\u0000", String.valueOf(profile.getType()),
                String.valueOf(profile.getEndpoint()), String.valueOf(profile.getRegion()),
                String.valueOf(profile.getPublicBucket()), String.valueOf(profile.getPrivateBucket()),
                String.valueOf(profile.isPathStyle()), String.valueOf(profile.getTimeout()),
                String.valueOf(profile.getAccessKey()), String.valueOf(profile.getSecretKey()));
        return HexFormat.of().formatHex(FileDigests.digest().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private ClientLease acquire(String name) {
        // 先获取数据库状态，避免持有 JVM 锁等待上传事务的数据库锁造成反向死锁。
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

    @PreDestroy
    public synchronized void close() {
        closed = true;
        clients.clear();
        leases.forEach(ClientLease::retire);
    }

    private record Snapshot(StorageProperties.Profile profile, String revision) {
    }

    private final class BoundClient implements ObjectStorageService {
        private final ClientLease lease;
        private boolean released;

        private BoundClient(ClientLease lease) {
            this.lease = lease;
        }

        @Override
        public void put(String bucket, String key, Path source, String type, String hash) {
            acquireOperation();
            try {
                lease.driver.put(bucket, key, source, type, hash);
            } finally {
                lease.release();
            }
        }

        @Override
        public InputStream open(String bucket, String key) {
            acquireOperation();
            try {
                return new FilterInputStream(lease.driver.open(bucket, key)) {
                    private final AtomicBoolean finished = new AtomicBoolean();

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

        @Override
        public void delete(String bucket, String key) {
            acquireOperation();
            try {
                lease.driver.delete(bucket, key);
            } finally {
                lease.release();
            }
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                lease.release();
            }
        }

        private synchronized void acquireOperation() {
            if (released) {
                throw new FileException("存储客户端租约已关闭");
            }
            lease.acquire();
        }
    }

    private final class ClientLease {
        private final ObjectStorageService driver;
        private final String revision;
        private int users;
        private boolean retired;
        private boolean disposed;

        private ClientLease(ObjectStorageService driver, String revision) {
            this.driver = driver;
            this.revision = revision;
        }

        private synchronized void acquire() {
            users++;
        }

        private synchronized void release() {
            users--;
            disposeIfUnused();
        }

        private synchronized void retire() {
            retired = true;
            disposeIfUnused();
        }

        private void disposeIfUnused() {
            if (retired && users == 0 && !disposed) {
                disposed = true;
                try {
                    driver.close();
                } catch (RuntimeException e) {
                    log.warn("存储客户端关闭失败");
                } finally {
                    leases.remove(this);
                }
            }
        }
    }
}

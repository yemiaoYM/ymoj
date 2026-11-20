package com.ymcloud.judge.data;

import com.ymcloud.judge.data.TestDataManifestReader.CaseSpec;
import com.ymcloud.judge.data.TestDataManifestReader.FileSpec;
import com.ymcloud.judge.data.TestDataManifestReader.VerifiedManifest;
import com.ymcloud.judge.io.WorkerPaths;
import jakarta.annotation.PreDestroy;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 按题目与不可变版本缓存原始文件。下载校验完成后原子发布，使用中的版本不会被容量清理。
 */
@Slf4j
@Component
public class TestDataCache implements AutoCloseable {

    private static final Pattern VERSION_DIRECTORY = Pattern.compile("case-p([1-9][0-9]*)-v([1-9][0-9]*)");
    private static final Pattern TEMP_DIRECTORY = Pattern.compile("\\.download-[0-9a-f-]{36}");
    private static final String MANIFEST = "manifest.json";

    private final TestDataSource source;
    private final TestDataCacheProperties properties;
    private final TestDataManifestReader manifestReader;
    private final Path root;
    private final FileChannel lockChannel;
    private final FileLock processLock;
    private final Object state = new Object();
    private final Map<Key, CachedVersion> versions = new HashMap<>();
    private final Map<Key, KeyLock> downloads = new HashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong downloadFailures = new AtomicLong();
    private final AtomicLong verificationFailures = new AtomicLong();

    private long readyBytes;
    private long reservedBytes;
    private boolean closed;

    /**
     * 构造时取得目录独占锁并恢复缓存，因此保留构造器注入。
     * @param source 版本数据下载边界
     * @param properties 缓存目录及容量限制
     * @throws IOException 目录不安全、占用冲突或恢复失败
     */
    public TestDataCache(TestDataSource source, TestDataCacheProperties properties) throws IOException {
        this.source = Objects.requireNonNull(source);
        this.properties = properties;
        validateProperties(properties);
        this.manifestReader = new TestDataManifestReader(properties);
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        WorkerPaths.rejectSymbolicLinks(root);
        Files.createDirectories(root);
        WorkerPaths.rejectSymbolicLinks(root);
        privatePermissions(root, true);
        Path lockPath = root.resolve(".worker.lock");
        WorkerPaths.rejectSymbolicLinks(lockPath);
        this.lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            privatePermissions(lockPath, false);
            this.processLock = lockChannel.tryLock();
            if (processLock == null) {
                throw new IOException("测试数据缓存目录已被其他 Worker 使用");
            }
        } catch (IOException | OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("测试数据缓存目录已被其他 Worker 使用", e);
        }
        try {
            recover();
            log.info("测试数据缓存初始化完成 versions={} bytes={}", versions.size(), readyBytes);
        } catch (IOException | RuntimeException e) {
            processLock.release();
            lockChannel.close();
            throw e;
        }
    }

    /**
     * 每次执行读取当前清单，租约持有独立的不可变版本目录。
     * @param problemId 正数题目 ID
     * @return 由调用方关闭的版本引用
     * @throws IOException 下载、完整性或容量检查失败，或任务中断
     */
    public Lease acquire(Long problemId) throws IOException {
        requireId(problemId);
        CachedVersion previous;
        synchronized (state) {
            requireOpen();
            previous = versions.values().stream()
                    .filter(value -> value.confirmed && value.key.getProblemId().equals(problemId))
                    .max(Comparator.comparing(value -> value.key.getVersion()))
                    .orElse(null);
        }
        Long baseline = previous == null ? null : previous.key.getVersion();
        TestDataSource.ManifestResponse response = source.fetchManifest(problemId, baseline);
        requireId(response.getVersion());
        Key key = new Key(problemId, response.getVersion());
        byte[] raw = response.getBody();
        if (raw == null) {
            if (previous == null || !previous.key.equals(key)) {
                throw new IOException("服务端未返回当前版本的清单");
            }
            raw = previous.manifest;
        }

        // 先取得本次执行的清单，再合并相同题目和版本的下载。
        KeyLock keyLock;
        synchronized (state) {
            requireOpen();
            keyLock = downloads.computeIfAbsent(key, ignored -> new KeyLock());
            keyLock.users++;
        }
        try {
            keyLock.lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (state) {
                if (--keyLock.users == 0) {
                    downloads.remove(key, keyLock);
                }
                releaseProcessLockIfUnused();
            }
            throw new IOException("等待测试数据缓存时被中断", e);
        }
        try {
            Lease cached = reuse(key, raw);
            if (cached != null) {
                hits.incrementAndGet();
                return cached;
            }
            misses.incrementAndGet();
            return download(key, raw);
        } finally {
            keyLock.lock.unlock();
            synchronized (state) {
                if (--keyLock.users == 0) {
                    downloads.remove(key, keyLock);
                }
                releaseProcessLockIfUnused();
            }
        }
    }

    /**
     * 校验相同发布版本的文件并取得租约，损坏且空闲的缓存可按本次清单重新下载。
     * @param key 本次执行取得的题目和版本
     * @param manifest 已由服务端确认的完整清单
     * @return 当前缓存的租约，缺失或需要重新下载时为空
     * @throws IOException 正在使用的文件损坏、校验中断或缓存不可用
     */
    private Lease reuse(Key key, byte[] manifest) throws IOException {
        CachedVersion cached;
        synchronized (state) {
            requireOpen();
            cached = versions.get(key);
            if (cached == null) {
                return null;
            }
            cached.references++;
        }
        try {
            if (!Arrays.equals(manifest, cached.manifest)) {
                throw new IOException("本地清单与发布记录不一致");
            }
            validateFiles(cached);
        } catch (IOException e) {
            synchronized (state) {
                release(cached);
                checkInterrupted();
                verificationFailures.incrementAndGet();
                if (cached.references != 0) {
                    throw new IOException("正在使用的测试缓存损坏", e);
                }
                removeVersion(cached);
            }
            return null;
        }
        synchronized (state) {
            cached.confirmed = true;
        }
        return new Lease(cached);
    }

    /**
     * 读取缓存命中、失败及磁盘预算快照。
     * @return 当前累计计数与字节容量
     */
    public Statistics statistics() {
        synchronized (state) {
            return new Statistics(hits.get(), misses.get(), downloadFailures.get(),
                    verificationFailures.get(), readyBytes, reservedBytes, versions.size());
        }
    }

    /**
     * 停止接收新请求，所有下载和引用释放后再关闭目录锁。
     * @throws IOException 目录锁或锁通道释放失败
     */
    @PreDestroy
    @Override
    public void close() throws IOException {
        synchronized (state) {
            closed = true;
            log.info("测试数据缓存停止接收任务 versions={} downloads={}", versions.size(), downloads.size());
            releaseProcessLockIfUnused();
        }
    }

    /**
     * 在临时目录下载并验证版本，完成后原子发布。
     * @param key 固定题目和版本
     * @param raw 已读取的发布清单
     * @return 持有已发布版本的引用
     * @throws IOException 下载、容量或校验失败
     */
    private Lease download(Key key, byte[] raw) throws IOException {
        long started = System.nanoTime();
        log.info("开始下载测试数据 problem={} version={}", key.getProblemId(), key.getVersion());
        Path staged = null;
        long reservation = 0;
        boolean published = false;
        String stage = "manifest";
        Long fileId = null;
        try {
            checkInterrupted();
            CachedVersion planned = parseVersion(key, raw, directory(key));
            staged = Files.createDirectory(root.resolve(".download-" + UUID.randomUUID()));
            privatePermissions(staged, true);
            stage = "capacity";
            synchronized (state) {
                requireOpen();
                // 先将可复用文件链接到新目录，再允许容量淘汰旧版本。
                for (FileSpec file : planned.files.values()) {
                    for (CachedVersion existing : versions.values()) {
                        if (existing.key.getProblemId().equals(key.getProblemId()) && file.equals(existing.files.get(file.getId()))) {
                            Path candidate = existing.directory.resolve(file.filename());
                            if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                                Files.createLink(staged.resolve(file.filename()), candidate);
                                break;
                            }
                        }
                    }
                }
                ensureCapacity(planned.bytes);
                reservedBytes += planned.bytes;
                reservation = planned.bytes;
            }
            writeManifest(staged, raw);
            for (FileSpec file : planned.files.values()) {
                stage = "file";
                fileId = file.getId();
                checkInterrupted();
                Path target = staged.resolve(file.filename());
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    try (InputStream input = verifiedStream(target, file)) {
                        input.transferTo(OutputStream.nullOutputStream());
                        continue;
                    } catch (IOException e) {
                        checkInterrupted();
                        verificationFailures.incrementAndGet();
                        Files.delete(target);
                    }
                }
                try (InputStream input = source.openFile(key.getProblemId(), file.getId())) {
                    writeVerified(staged.resolve(file.filename()), input, file);
                }
            }
            stage = "publish";
            fileId = null;
            synchronized (state) {
                requireOpen();
                Files.move(staged, planned.directory, StandardCopyOption.ATOMIC_MOVE);
                planned.references = 1;
                planned.confirmed = true;
                versions.put(key, planned);
                readyBytes += planned.bytes;
                reservedBytes -= reservation;
                reservation = 0;
                published = true;
                log.info("测试数据下载校验完成 problem={} version={} files={} cases={} bytes={} elapsedMs={}",
                        key.getProblemId(), key.getVersion(), planned.files.size(), planned.cases.size(), planned.bytes,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                return new Lease(planned);
            }
        } catch (IOException | RuntimeException e) {
            downloadFailures.incrementAndGet();
            log.warn("测试数据下载失败 problem={} version={} stage={} file={} elapsedMs={} cause={}",
                    key.getProblemId(), key.getVersion(), stage, fileId,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), e.getClass().getSimpleName());
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("下载测试数据失败", e);
        } finally {
            try {
                if (!published && staged != null) {
                    deleteOwnedDirectory(staged);
                }
            } catch (IOException e) {
                synchronized (state) {
                    // 残留下载无法删除时停止新任务准备，避免释放预算后继续占满磁盘。
                    closed = true;
                    log.warn("下载临时目录清理失败，停止缓存准备 problem={} version={} cause={}",
                            key.getProblemId(), key.getVersion(), e.getClass().getSimpleName());
                }
                throw e;
            } finally {
                synchronized (state) {
                    reservedBytes -= reservation;
                }
            }
        }
    }

    /**
     * 恢复完整版本并清理中断下载及无效版本。
     * @throws IOException 扫描或清理缓存目录失败
     */
    private void recover() throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.sorted().toList()) {
                String name = path.getFileName().toString();
                if (TEMP_DIRECTORY.matcher(name).matches()) {
                    deleteOwnedDirectory(path);
                    log.debug("清理上次未完成的测试数据下载 directory={}", name);
                    continue;
                }
                Matcher matcher = VERSION_DIRECTORY.matcher(name);
                if (!matcher.matches()) {
                    continue;
                }
                try {
                    Key key = new Key(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
                    byte[] raw = readManifest(path.resolve(MANIFEST));
                    CachedVersion recovered = parseVersion(key, raw, path);
                    validateFiles(recovered);
                    ensureCapacity(recovered.bytes);
                    versions.put(key, recovered);
                    readyBytes += recovered.bytes;
                    log.debug("恢复测试数据缓存 problem={} version={} cases={} bytes={}",
                            key.getProblemId(), key.getVersion(), recovered.cases.size(), recovered.bytes);
                } catch (IOException | RuntimeException e) {
                    verificationFailures.incrementAndGet();
                    log.warn("移除无效的测试数据缓存 directory={} cause={}", name, e.getClass().getSimpleName());
                    deleteOwnedDirectory(path);
                }
            }
        }
    }

    /**
     * 校验原始清单并建立版本元数据。
     * @param key 固定题目和版本
     * @param raw 有界原始清单
     * @param directory 当前版本目录
     * @return 已验证版本描述
     * @throws IOException 清单与任务不匹配或字段非法
     */
    private CachedVersion parseVersion(Key key, byte[] raw, Path directory) throws IOException {
        try {
            VerifiedManifest manifest = manifestReader.read(raw);
            return new CachedVersion(key, directory, raw, manifest.getBytes(), manifest.getFiles(), manifest.getCases());
        } catch (IOException e) {
            verificationFailures.incrementAndGet();
            throw e;
        }
    }

    /**
     * 核对清单及全部文件完整性，拒绝额外文件。
     * @param version 待核验缓存版本
     * @throws IOException 文件缺失、校验失败或中断
     */
    private void validateFiles(CachedVersion version) throws IOException {
        WorkerPaths.rejectSymbolicLinks(version.directory);
        if (!Files.isDirectory(version.directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("缓存版本目录不存在或路径无效");
        }
        privatePermissions(version.directory, true);
        Set<String> expectedNames = new HashSet<>();
        expectedNames.add(MANIFEST);
        for (FileSpec file : version.files.values()) {
            expectedNames.add(file.filename());
            try (InputStream input = verifiedStream(version.directory.resolve(file.filename()), file)) {
                byte[] buffer = new byte[64 * 1024];
                while (input.read(buffer) != -1) {
                    checkInterrupted();
                }
            }
        }
        try (Stream<Path> paths = Files.list(version.directory)) {
            for (Path path : paths.toList()) {
                if (!expectedNames.remove(path.getFileName().toString())
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("缓存版本目录包含非预期文件");
                }
            }
        }
        if (!expectedNames.isEmpty()) {
            throw new IOException("缓存版本目录缺少测试数据文件");
        }
    }

    /**
     * 有界读取本地清单，拒绝链接及非普通文件。
     * @param path 清单绝对路径
     * @return 原始清单字节
     * @throws IOException 路径非法、文件超限或读取失败
     */
    private byte[] readManifest(Path path) throws IOException {
        WorkerPaths.rejectSymbolicLinks(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > properties.getMaxManifestBytes()) {
            throw new IOException("缓存清单文件无效或过大");
        }
        privatePermissions(path, false);
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] raw = input.readNBytes(properties.getMaxManifestBytes() + 1);
            if (raw.length > properties.getMaxManifestBytes()) {
                throw new IOException("缓存清单文件超过限制");
            }
            return raw;
        }
    }

    /**
     * 写入并刷盘原始清单。
     * @param staged 当前下载临时目录
     * @param raw 原始清单字节
     * @throws IOException 写入、权限或刷盘失败
     */
    private void writeManifest(Path staged, byte[] raw) throws IOException {
        Path path = staged.resolve(MANIFEST);
        Files.write(path, raw, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        privatePermissions(path, false);
        forceFile(path);
    }

    /**
     * 按流写入单个数据文件，复核字节数和 SHA-256。
     * @param target 新文件路径
     * @param input 由上层关闭的数据流
     * @param file 清单文件描述
     * @throws IOException 容量、完整性或写入失败
     */
    private void writeVerified(Path target, InputStream input, FileSpec file) throws IOException {
        MessageDigest digest = digest();
        long count = 0;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            privatePermissions(target, false);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                checkInterrupted();
                count += read;
                if (count > file.getSize()) {
                    verificationFailures.incrementAndGet();
                    throw new IOException("下载的测试数据超过清单声明大小");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (count != file.getSize() || !file.getSha256().equals(HexFormat.of().formatHex(digest.digest()))) {
            verificationFailures.incrementAndGet();
            throw new IOException("下载的测试数据大小或 SHA-256 校验失败");
        }
        forceFile(target);
    }

    /**
     * 仅淘汰无引用的最久未使用版本，调用方持有状态锁。
     * @param incoming 新版本占用字节数
     * @throws IOException 没有可安全腾出的空间
     */
    private void ensureCapacity(long incoming) throws IOException {
        if (incoming > properties.getMaxBytes()) {
            throw new IOException("测试数据版本超过 Worker 总缓存容量");
        }
        while (readyBytes + reservedBytes > properties.getMaxBytes() - incoming) {
            CachedVersion oldest = versions.values().stream()
                    .filter(version -> version.references == 0)
                    .min(Comparator.comparingLong(version -> version.lastAccess))
                    .orElseThrow(() -> new IOException("缓存容量不足，运行中的测试数据不会被清理"));
            removeVersion(oldest);
        }
    }

    /**
     * 删除无引用版本并扣减容量，调用方持有状态锁。
     * @param version 待淘汰版本
     * @throws IOException 版本仍在使用或删除失败
     */
    private void removeVersion(CachedVersion version) throws IOException {
        if (version.references != 0) {
            throw new IOException("不能删除运行中的测试数据缓存");
        }
        deleteOwnedDirectory(version.directory);
        if (versions.remove(version.key, version)) {
            readyBytes -= version.bytes;
            log.debug("清理未使用的测试数据缓存 problem={} version={} bytes={}",
                    version.key.getProblemId(), version.key.getVersion(), version.bytes);
        }
    }

    /**
     * 仅删除缓存根目录内符合自有命名的目录，不跟随链接。
     * @param directory 候选目录
     * @throws IOException 路径越界或清理失败
     */
    private void deleteOwnedDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        String name = normalized.getFileName().toString();
        if (!root.equals(normalized.getParent())
                || (!VERSION_DIRECTORY.matcher(name).matches() && !TEMP_DIRECTORY.matcher(name).matches())) {
            throw new IOException("拒绝清理缓存目录以外的路径");
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // walk 不跟随符号链接；链接本身被删除，绝不删除其指向的文件。
        try (Stream<Path> paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 由可信 ID 构造固定版本路径。
     * @param key 固定题目和版本
     * @return 缓存根目录内的版本路径
     */
    private Path directory(Key key) {
        return root.resolve("case-p" + key.getProblemId() + "-v" + key.getVersion());
    }

    /**
     * 释放一个引用并更新访问时间，关闭期间检查目录锁。
     * @param version 当前引用的版本
     * @throws IOException 目录锁释放失败
     */
    private void release(CachedVersion version) throws IOException {
        synchronized (state) {
            version.references--;
            version.lastAccess = System.nanoTime();
            releaseProcessLockIfUnused();
        }
    }

    /**
     * 停止后等待下载及引用全部释放，再解除目录占用。
     * @throws IOException 释放文件锁或通道失败
     */
    private void releaseProcessLockIfUnused() throws IOException {
        if (closed && downloads.isEmpty() && reservedBytes == 0
                && versions.values().stream().noneMatch(version -> version.references != 0)
                && processLock.isValid()) {
            processLock.release();
            lockChannel.close();
        }
    }

    /**
     * 拒绝已停止缓存接收新的数据准备。
     * @throws IOException 缓存已经停止
     */
    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("测试数据缓存已经关闭");
        }
    }

    /**
     * 校验缓存目录、容量及测试点数量。
     * @param properties 缓存配置
     */
    private static void validateProperties(TestDataCacheProperties properties) {
        if (properties == null || properties.getDirectory() == null || properties.getDirectory().isBlank()
                || properties.getMaxBytes() <= 0 || properties.getMaxVersionBytes() <= 0
                || properties.getMaxFileBytes() <= 0 || properties.getMaxManifestBytes() <= 0
                || properties.getMaxManifestBytes() > 16 * 1024 * 1024
                || properties.getMaxCases() <= 0 || properties.getMaxCases() > 10_000) {
            throw new IllegalArgumentException("Worker 测试数据缓存配置无效");
        }
    }

    /**
     * 要求题目、版本和文件标识为正数。
     * @param id 待检查 ID
     * @throws IOException 标识为空或非正数
     */
    private static void requireId(Long id) throws IOException {
        if (id == null || id <= 0) {
            throw new IOException("测试数据题目、版本或文件 ID 无效");
        }
    }

    /**
     * 限制缓存文件仅由当前所有者访问，非 POSIX 平台保留系统权限。
     * @param path 缓存路径
     * @param directory 是否为目录
     * @throws IOException 权限更新失败
     */
    private static void privatePermissions(Path path, boolean directory) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            String permissions = directory ? "rwx------" : "rw-------";
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }

    /**
     * 将已写文件刷盘后再参与版本发布。
     * @param path 已写入的普通文件
     * @throws IOException 刷盘失败
     */
    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    /**
     * 在文件处理边界响应任务中断。
     * @throws IOException 当前线程已中断
     */
    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("测试数据准备被中断");
        }
    }

    /**
     * 创建独立的 SHA-256 摘要状态。
     * @return 本次校验的摘要实例
     */
    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 打开受限本地文件，EOF 和提前关闭都检查完整性。
     * @param path 缓存文件路径
     * @param file 固定文件元数据
     * @return 调用方负责关闭的校验流
     * @throws IOException 路径、大小或打开失败
     */
    private static InputStream verifiedStream(Path path, FileSpec file) throws IOException {
        WorkerPaths.rejectSymbolicLinks(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != file.getSize()) {
            throw new IOException("缓存文件不存在或大小不一致");
        }
        privatePermissions(path, false);
        InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        return new VerifiedTestDataStream(input, file.getSize(), file.getSha256());
    }

    public final class Lease implements AutoCloseable {

        private final CachedVersion version;
        private final AtomicBoolean released = new AtomicBoolean();
        private final List<CachedCase> entries;

        /**
         * 为已领取版本建立测试点视图。
         * @param version 已经增加引用计数的版本
         */
        private Lease(CachedVersion version) {
            this.version = version;
            this.entries = version.cases.stream()
                    .map(testCase -> new CachedCase(this, testCase))
                    .toList();
        }

        /**
         * 读取仍持有引用的测试点列表。
         * @return 不可变测试点列表
         * @throws IOException 引用已释放
         */
        public List<CachedCase> entries() throws IOException {
            requireActive();
            return entries;
        }

        /**
         * 阻止已释放版本引用继续打开文件。
         * @throws IOException 引用已经释放
         */
        private void requireActive() throws IOException {
            if (released.get()) {
                throw new IOException("测试数据缓存引用已经释放");
            }
        }

        /**
         * 打开文件时增加独立引用，流关闭后再释放。
         * @param file 固定清单文件描述
         * @return 调用方负责关闭的完整性校验流
         * @throws IOException 版本已释放或文件无效
         */
        private InputStream openFile(FileSpec file) throws IOException {
            synchronized (state) {
                requireActive();
                version.references++;
            }
            InputStream input;
            try {
                input = verifiedStream(version.directory.resolve(file.filename()), file);
            } catch (IOException e) {
                release(version);
                throw e;
            }
            return new FilterInputStream(input) {

                private final AtomicBoolean streamReleased = new AtomicBoolean();

                /**
                 * 关闭校验流并释放其独立引用，重复关闭无操作。
                 * @throws IOException 数据完整性、流关闭或目录锁释放失败
                 */
                @Override
                public void close() throws IOException {
                    if (streamReleased.compareAndSet(false, true)) {
                        try {
                            in.close();
                        } finally {
                            release(version);
                        }
                    }
                }
            };
        }

        /**
         * 释放版本引用，重复关闭无操作；在用文件仍持有各自引用。
         * @throws IOException 目录锁或锁通道释放失败
         */
        @Override
        public void close() throws IOException {
            if (released.compareAndSet(false, true)) {
                release(version);
            }
        }
    }

    public static final class CachedCase {

        private final Lease lease;
        private final CaseSpec testCase;

        /**
         * 将测试点绑定到当前版本租约。
         * @param lease 有效版本引用
         * @param testCase 固定测试点描述
         */
        private CachedCase(Lease lease, CaseSpec testCase) {
            this.lease = lease;
            this.testCase = testCase;
        }

        /**
         * 读取清单中的测试点编号。
         * @return 从 1 开始的测试点编号
         */
        public int id() {
            return testCase.getId();
        }

        /**
         * 打开当前测试点的输入流。
         * @return 调用方负责关闭的校验流
         * @throws IOException 引用已释放或文件非法
         */
        public InputStream openInput() throws IOException {
            return lease.openFile(testCase.getInput());
        }

        /**
         * 打开当前测试点的标准答案流。
         * @return 调用方负责关闭的校验流
         * @throws IOException 引用已释放或文件非法
         */
        public InputStream openOutput() throws IOException {
            return lease.openFile(testCase.getOutput());
        }
    }

    /**
     * 当前 Worker 测试数据缓存统计
     */
    @Value
    public static class Statistics {

        /**
         * 命中缓存的次数
         */
        long hits;

        /**
         * 未命中缓存的次数
         */
        long misses;

        /**
         * 下载失败次数
         */
        long downloadFailures;

        /**
         * 完整性校验失败次数
         */
        long verificationFailures;

        /**
         * 已就绪缓存占用的字节数
         */
        long readyBytes;

        /**
         * 进行中下载预留的字节数
         */
        long reservedBytes;

        /**
         * 已就绪的清单版本数
         */
        int versions;
    }

    /**
     * 按内部题号和清单版本区分本地缓存
     */
    @Value
    private static class Key {

        /**
         * 题目内部主键
         */
        Long problemId;

        /**
         * 测试清单发布版本
         */
        Long version;
    }

    private static final class KeyLock {

        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    private static final class CachedVersion {

        private final Key key;
        private final Path directory;
        private final byte[] manifest;
        private boolean confirmed;
        private final long bytes;
        private final Map<Long, FileSpec> files;
        private final List<CaseSpec> cases;
        private int references;
        private long lastAccess = System.nanoTime();

        /**
         * 保存已验证版本的索引和磁盘占用。
         * @param key 固定版本键
         * @param directory 版本目录
         * @param manifest 经校验的清单原文，用于条件读取及同版本修复
         * @param bytes 总占用字节数
         * @param files 不可变文件索引
         * @param cases 不可变测试点列表
         */
        private CachedVersion(Key key, Path directory, byte[] manifest, long bytes,
                              Map<Long, FileSpec> files, List<CaseSpec> cases) {
            this.key = key;
            this.directory = directory;
            this.manifest = manifest;
            this.bytes = bytes;
            this.files = files;
            this.cases = cases;
        }
    }
}

package com.ymcloud.judge.data;

import com.ymcloud.judge.data.TestDataManifestReader.CaseSpec;
import com.ymcloud.judge.data.TestDataManifestReader.FileSpec;
import com.ymcloud.judge.data.TestDataManifestReader.VerifiedManifest;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 按题目与不可变版本缓存原始文件。下载校验完成后原子发布，使用中的版本不会被容量清理。
 */
@Component
public class TestDataCache implements AutoCloseable {

    private static final Pattern VERSION_DIRECTORY = Pattern.compile("p([1-9][0-9]*)-v([1-9][0-9]*)");
    private static final Pattern TEMP_DIRECTORY = Pattern.compile("\\.download-[0-9a-f-]{36}");
    private static final String MANIFEST = "manifest.json";
    private static final String MANIFEST_HASH = "manifest.sha256";

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

    public TestDataCache(TestDataSource source, TestDataCacheProperties properties) throws IOException {
        this.source = Objects.requireNonNull(source);
        this.properties = properties;
        validateProperties(properties);
        this.manifestReader = new TestDataManifestReader(properties);
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        rejectSymbolicLinks(root);
        Files.createDirectories(root);
        rejectSymbolicLinks(root);
        privatePermissions(root, true);
        Path lockPath = root.resolve(".worker.lock");
        rejectSymbolicLinks(lockPath);
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
        } catch (IOException | RuntimeException e) {
            processLock.release();
            lockChannel.close();
            throw e;
        }
    }

    /**
     * 任务固定的清单哈希参与校验，不能以题目当前版本或缓存自身的哈希替代。
     */
    public Lease acquire(Long problemId, Long versionId, String manifestSha256) throws IOException {
        requireId(problemId);
        requireId(versionId);
        requireHash(manifestSha256);
        Key key = new Key(problemId, versionId);
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
            CachedVersion current;
            synchronized (state) {
                requireOpen();
                current = versions.get(key);
                if (current != null) {
                    if (!manifestSha256.equals(current.manifestHash)) {
                        throw new IOException("任务的清单哈希与已缓存不可变版本不一致");
                    }
                    current.references++;
                }
            }
            if (current != null) {
                try {
                    validateFiles(current, manifestSha256);
                    synchronized (state) {
                        current.lastAccess = System.nanoTime();
                    }
                    hits.incrementAndGet();
                    return new Lease(current);
                } catch (IOException e) {
                    synchronized (state) {
                        current.references--;
                        if (Thread.currentThread().isInterrupted()) {
                            throw e;
                        }
                        verificationFailures.incrementAndGet();
                        if (current.references != 0) {
                            throw new IOException("运行中的测试数据缓存损坏，不能替换其文件", e);
                        }
                        removeVersion(current);
                    }
                }
            }
            misses.incrementAndGet();
            return download(key, manifestSha256);
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

    public Statistics statistics() {
        synchronized (state) {
            return new Statistics(hits.get(), misses.get(), downloadFailures.get(),
                    verificationFailures.get(), readyBytes, reservedBytes, versions.size());
        }
    }

    @PreDestroy
    @Override
    public void close() throws IOException {
        synchronized (state) {
            closed = true;
            releaseProcessLockIfUnused();
        }
    }

    private Lease download(Key key, String expectedHash) throws IOException {
        Path staged = null;
        long reservation = 0;
        boolean published = false;
        try {
            checkInterrupted();
            byte[] raw = source.fetchManifest(key.problemId, key.versionId);
            CachedVersion planned = parseVersion(key, raw, expectedHash, directory(key));
            synchronized (state) {
                requireOpen();
                ensureCapacity(planned.bytes);
                reservedBytes += planned.bytes;
                reservation = planned.bytes;
            }
            staged = Files.createDirectory(root.resolve(".download-" + UUID.randomUUID()));
            privatePermissions(staged, true);
            writeManifest(staged, raw, expectedHash);
            for (FileSpec file : planned.files.values()) {
                checkInterrupted();
                try (InputStream input = source.openFile(key.problemId, key.versionId, file.id())) {
                    writeVerified(staged.resolve(file.filename()), input, file);
                }
            }
            synchronized (state) {
                requireOpen();
                Files.move(staged, planned.directory, StandardCopyOption.ATOMIC_MOVE);
                planned.references = 1;
                versions.put(key, planned);
                readyBytes += planned.bytes;
                reservedBytes -= reservation;
                reservation = 0;
                published = true;
                return new Lease(planned);
            }
        } catch (IOException | RuntimeException e) {
            downloadFailures.incrementAndGet();
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
                }
                throw e;
            } finally {
                synchronized (state) {
                    reservedBytes -= reservation;
                }
            }
        }
    }

    private void recover() throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.sorted().toList()) {
                String name = path.getFileName().toString();
                if (TEMP_DIRECTORY.matcher(name).matches()) {
                    deleteOwnedDirectory(path);
                    continue;
                }
                Matcher matcher = VERSION_DIRECTORY.matcher(name);
                if (!matcher.matches()) {
                    continue;
                }
                try {
                    Key key = new Key(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
                    byte[] raw = readManifest(path.resolve(MANIFEST));
                    CachedVersion recovered = parseVersion(key, raw, readManifestHash(path), path);
                    validateFiles(recovered, recovered.manifestHash);
                    ensureCapacity(recovered.bytes);
                    versions.put(key, recovered);
                    readyBytes += recovered.bytes;
                } catch (IOException | RuntimeException e) {
                    verificationFailures.incrementAndGet();
                    deleteOwnedDirectory(path);
                }
            }
        }
    }

    private CachedVersion parseVersion(Key key, byte[] raw, String expectedHash, Path directory) throws IOException {
        try {
            VerifiedManifest manifest = manifestReader.read(key.problemId, key.versionId, raw, expectedHash);
            return new CachedVersion(key, directory, expectedHash, manifest.bytes(), manifest.files(), manifest.cases());
        } catch (IOException e) {
            verificationFailures.incrementAndGet();
            throw e;
        }
    }

    private void validateFiles(CachedVersion version, String expectedHash) throws IOException {
        rejectSymbolicLinks(version.directory);
        if (!Files.isDirectory(version.directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("缓存版本目录不存在或路径无效");
        }
        privatePermissions(version.directory, true);
        if (!expectedHash.equals(version.manifestHash)
                || !expectedHash.equals(readManifestHash(version.directory))
                || !expectedHash.equals(hash(readManifest(version.directory.resolve(MANIFEST))))) {
            throw new IOException("缓存清单与任务的固定版本不一致");
        }
        Set<String> expectedNames = new HashSet<>();
        expectedNames.add(MANIFEST);
        expectedNames.add(MANIFEST_HASH);
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

    private byte[] readManifest(Path path) throws IOException {
        rejectSymbolicLinks(path);
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

    private String readManifestHash(Path directory) throws IOException {
        Path path = directory.resolve(MANIFEST_HASH);
        rejectSymbolicLinks(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != 64) {
            throw new IOException("缓存清单的固定哈希标记无效");
        }
        privatePermissions(path, false);
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(65);
        }
        String hash = new String(bytes, StandardCharsets.US_ASCII);
        requireHash(hash);
        return hash;
    }

    private void writeManifest(Path staged, byte[] raw, String expectedHash) throws IOException {
        Path path = staged.resolve(MANIFEST);
        Files.write(path, raw, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        privatePermissions(path, false);
        forceFile(path);
        Path marker = staged.resolve(MANIFEST_HASH);
        Files.writeString(marker, expectedHash, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        privatePermissions(marker, false);
        forceFile(marker);
    }

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
                if (count > file.size()) {
                    verificationFailures.incrementAndGet();
                    throw new IOException("下载的测试数据超过清单声明大小");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (count != file.size() || !file.sha256().equals(HexFormat.of().formatHex(digest.digest()))) {
            verificationFailures.incrementAndGet();
            throw new IOException("下载的测试数据大小或 SHA-256 校验失败");
        }
        forceFile(target);
    }

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

    private void removeVersion(CachedVersion version) throws IOException {
        if (version.references != 0) {
            throw new IOException("不能删除运行中的测试数据缓存");
        }
        deleteOwnedDirectory(version.directory);
        if (versions.remove(version.key, version)) {
            readyBytes -= version.bytes;
        }
    }

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

    private Path directory(Key key) {
        return root.resolve("p" + key.problemId + "-v" + key.versionId);
    }

    private void release(CachedVersion version) throws IOException {
        synchronized (state) {
            version.references--;
            version.lastAccess = System.nanoTime();
            releaseProcessLockIfUnused();
        }
    }

    private void releaseProcessLockIfUnused() throws IOException {
        if (closed && downloads.isEmpty() && reservedBytes == 0
                && versions.values().stream().noneMatch(version -> version.references != 0)
                && processLock.isValid()) {
            processLock.release();
            lockChannel.close();
        }
    }

    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("测试数据缓存已经关闭");
        }
    }

    private static void validateProperties(TestDataCacheProperties properties) {
        if (properties == null || properties.getDirectory() == null || properties.getDirectory().isBlank()
                || properties.getMaxBytes() <= 0 || properties.getMaxVersionBytes() <= 0
                || properties.getMaxFileBytes() <= 0 || properties.getMaxManifestBytes() <= 0
                || properties.getMaxManifestBytes() > 16 * 1024 * 1024
                || properties.getMaxCases() <= 0 || properties.getMaxCases() > 10_000) {
            throw new IllegalArgumentException("Worker 测试数据缓存配置无效");
        }
    }

    private static void requireId(Long id) throws IOException {
        if (id == null || id <= 0) {
            throw new IOException("测试数据题目、版本或文件 ID 无效");
        }
    }

    private static void requireHash(String hash) throws IOException {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IOException("测试数据 SHA-256 无效");
        }
    }

    private static void rejectSymbolicLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("测试数据缓存路径不允许符号链接");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("测试数据缓存路径不允许链接或目录联接");
            }
        }
    }

    private static void privatePermissions(Path path, boolean directory) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            String permissions = directory ? "rwx------" : "rw-------";
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("测试数据准备被中断");
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private static String hash(byte[] value) {
        return HexFormat.of().formatHex(digest().digest(value));
    }

    private static InputStream verifiedStream(Path path, FileSpec file) throws IOException {
        rejectSymbolicLinks(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != file.size()) {
            throw new IOException("缓存文件不存在或大小不一致");
        }
        privatePermissions(path, false);
        InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        return new VerifiedTestDataStream(input, file.size(), file.sha256());
    }

    public final class Lease implements AutoCloseable {

        private final CachedVersion version;
        private final AtomicBoolean released = new AtomicBoolean();
        private final List<CachedCase> entries;

        private Lease(CachedVersion version) {
            this.version = version;
            this.entries = version.cases.stream()
                    .map(testCase -> new CachedCase(this, testCase))
                    .toList();
        }

        public List<CachedCase> entries() throws IOException {
            requireActive();
            return entries;
        }

        private void requireActive() throws IOException {
            if (released.get()) {
                throw new IOException("测试数据缓存引用已经释放");
            }
        }

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

        private CachedCase(Lease lease, CaseSpec testCase) {
            this.lease = lease;
            this.testCase = testCase;
        }

        public int id() {
            return testCase.id();
        }

        public Path inputPath() {
            return lease.version.directory.resolve(testCase.input().filename());
        }

        public Path outputPath() {
            return lease.version.directory.resolve(testCase.output().filename());
        }

        public InputStream openInput() throws IOException {
            return lease.openFile(testCase.input());
        }

        public InputStream openOutput() throws IOException {
            return lease.openFile(testCase.output());
        }
    }

    public record Statistics(long hits, long misses, long downloadFailures, long verificationFailures,
                             long readyBytes, long reservedBytes, int versions) {
    }

    private record Key(Long problemId, Long versionId) {
    }

    private static final class KeyLock {

        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    private static final class CachedVersion {

        private final Key key;
        private final Path directory;
        private final String manifestHash;
        private final long bytes;
        private final Map<Long, FileSpec> files;
        private final List<CaseSpec> cases;
        private int references;
        private long lastAccess = System.nanoTime();

        private CachedVersion(Key key, Path directory, String manifestHash, long bytes,
                              Map<Long, FileSpec> files, List<CaseSpec> cases) {
            this.key = key;
            this.directory = directory;
            this.manifestHash = manifestHash;
            this.bytes = bytes;
            this.files = files;
            this.cases = cases;
        }
    }

}

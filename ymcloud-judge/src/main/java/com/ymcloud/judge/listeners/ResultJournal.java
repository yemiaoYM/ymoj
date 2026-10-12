package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.io.WorkerPaths;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/**
 * 发布前原子保存终态。进程重启或 ACK 丢失时复用结果，避免重复执行代码。
 */
@Component
public class ResultJournal {

    private final ObjectMapper mapper;
    private final Path directory;
    private final long retentionMillis;

    public ResultJournal(ObjectMapper mapper, WorkerProperties properties) throws IOException {
        this.mapper = mapper;
        this.directory = properties.getJournalDir().toAbsolutePath().normalize();
        this.retentionMillis = properties.getRetentionMillis();
        WorkerPaths.rejectSymbolicLinks(directory);
        Files.createDirectories(directory);
        privatePermissions(directory, true);
        prune();
    }

    public synchronized JudgeResult read(String executionId, String digest) throws IOException {
        Path path = path(executionId);
        WorkerPaths.rejectSymbolicLinks(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 32 * 1024 * 1024L) {
            throw new IOException("Worker 结果日志无效");
        }
        Entry entry;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            entry = mapper.readValue(input, Entry.class);
        }
        if (entry == null || !digest.equals(entry.digest()) || entry.result() == null || !entry.result().isTerminal()
                || !executionId.equals(entry.result().getExecutionId())) {
            throw new IOException("Worker 结果日志与任务指纹不一致");
        }
        return entry.result();
    }

    public synchronized void save(String executionId, String digest, JudgeResult result) throws IOException {
        Path target = path(executionId);
        WorkerPaths.rejectSymbolicLinks(target);
        Path temporary = Files.createTempFile(directory, ".result-", ".tmp");
        try {
            privatePermissions(temporary, false);
            mapper.writeValue(temporary.toFile(), new Entry(digest, result));
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 清理超过恢复保留期的已命名结果；写入、读取和清理互斥，不扫描沙箱或数据目录。
     */
    public synchronized void prune() throws IOException {
        WorkerPaths.rejectSymbolicLinks(directory);
        long cutoff = System.currentTimeMillis() - retentionMillis;
        try (var files = Files.list(directory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                String name = path.getFileName().toString();
                if (name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json")
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() < cutoff) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Path path(String executionId) {
        if (!UUID.fromString(executionId).toString().equals(executionId)) {
            throw new IllegalArgumentException("执行标识无效");
        }
        return directory.resolve(executionId + ".json");
    }

    private static void privatePermissions(Path path, boolean folder) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(folder ? "rwx------" : "rw-------"));
        }
    }

    public record Entry(String digest, JudgeResult result) {
    }
}

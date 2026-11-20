package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.io.WorkerPaths;
import com.ymcloud.pojo.bo.judge.JudgeResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 发布前原子保存终态。进程重启或 ACK 丢失时复用结果，避免重复执行代码。
 */
@Component
@Slf4j
public class ResultJournal {

    private static final long TEMPORARY_RETENTION_MILLIS = 60 * 60 * 1000L;

    private final ObjectMapper mapper;
    private final Path directory;
    private final long retentionMillis;

    /**
     * 构造时建立私有结果目录并清理过期记录，保留构造器注入。
     * @param mapper 判题专用映射器
     * @param properties 结果目录及恢复保留期
     * @throws IOException 目录、权限或清理失败
     */
    public ResultJournal(ObjectMapper mapper, WorkerProperties properties) throws IOException {
        this.mapper = mapper;
        this.directory = properties.getJournalDir().toAbsolutePath().normalize();
        this.retentionMillis = properties.getRetentionMillis();
        WorkerPaths.rejectSymbolicLinks(directory);
        Files.createDirectories(directory);
        privatePermissions(directory, true);
        prune();
    }

    /**
     * 读取同一执行的终态并验证任务指纹。
     * @param executionId 标准 UUID 执行标识
     * @param digest 原任务摘要
     * @return 合法终态，未保存时为 null
     * @throws IOException 记录损坏、错配或读取失败
     */
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
        if (entry == null || !digest.equals(entry.getDigest()) || entry.getResult() == null || !entry.getResult().isTerminal()
                || !executionId.equals(entry.getResult().getExecutionId())) {
            throw new IOException("Worker 结果日志与任务指纹不一致");
        }
        JudgeResult result = entry.getResult();
        log.debug("Worker 读取本地终态记录 submission={} execution={} status={}",
                result.getSubmissionId(), executionId, result.getStatus());
        return entry.getResult();
    }

    /**
     * 刷盘后原子替换终态记录，发布失败可从此恢复。
     * @param executionId 标准 UUID 执行标识
     * @param digest 原任务摘要
     * @param result 本次终态结果
     * @throws IOException 写入或原子替换失败
     */
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
            log.debug("Worker 保存本地终态记录 submission={} execution={} status={}",
                    result.getSubmissionId(), executionId, result.getStatus());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 正式结果按恢复保留期清理，异常退出的临时文件至少保留一小时。
     * 写入、读取和清理互斥，不能删除本实例正在写入的临时文件，不扫描沙箱或数据目录。
     * @throws IOException 遍历或删除失败
     */
    public synchronized void prune() throws IOException {
        WorkerPaths.rejectSymbolicLinks(directory);
        long now = System.currentTimeMillis();
        long cutoff = now - retentionMillis;
        long temporaryCutoff = now - TEMPORARY_RETENTION_MILLIS;
        int removed = 0;
        int temporaryRemoved = 0;
        try (var files = Files.list(directory)) {
            var iterator = files.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                long modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
                if (name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json")
                        && modified < cutoff) {
                    Files.deleteIfExists(path);
                    removed++;
                } else if (name.matches("\\.result-[0-9]{1,20}\\.tmp") && modified < temporaryCutoff) {
                    // 仅识别 createTempFile 生成的十进制随机后缀；其他文件名不属于本组件。
                    Files.deleteIfExists(path);
                    temporaryRemoved++;
                }
            }
        }
        if (removed > 0) {
            log.info("Worker 清理过期终态记录 count={}", removed);
        }
        if (temporaryRemoved > 0) {
            log.info("Worker 清理异常退出残留的结果临时文件 count={}", temporaryRemoved);
        }
    }

    /**
     * 将标准 UUID 映射到本组件结果目录。
     * @param executionId 标准 UUID 执行标识
     * @return 固定后缀的结果路径
     */
    private Path path(String executionId) {
        if (!UUID.fromString(executionId).toString().equals(executionId)) {
            throw new IllegalArgumentException("执行标识无效");
        }
        return directory.resolve(executionId + ".json");
    }

    /**
     * 在 POSIX 平台限制为所有者可访问。
     * @param path 受控路径
     * @param folder 是否为目录
     * @throws IOException 权限更新失败
     */
    private static void privatePermissions(Path path, boolean folder) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(folder ? "rwx------" : "rw-------"));
        }
    }

    /**
     * 恢复日志持久化的任务指纹及终态结果
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Entry {

        /**
         * 原任务快照的指纹
         */
        private String digest;

        /**
         * 与执行身份对应的终态结果
         */
        private JudgeResult result;
    }
}

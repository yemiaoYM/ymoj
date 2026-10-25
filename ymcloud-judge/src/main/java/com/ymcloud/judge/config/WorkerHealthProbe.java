package com.ymcloud.judge.config;

import com.ymcloud.judge.io.WorkerPaths;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 将实际 Rabbit 消费就绪状态写为短效探针；仅 JVM 存活不会产生健康标记。
 */
@Slf4j
@Component
public class WorkerHealthProbe {

    @Autowired
    private RabbitListenerEndpointRegistry registry;

    @Autowired
    private WorkerProperties properties;

    private Path directory;
    private boolean started;

    /**
     * 注入完成后固定工作目录，并清除上次进程留下的健康标记。
     */
    @PostConstruct
    public synchronized void initialize() {
        directory = properties.getWorkDir().toAbsolutePath().normalize();
        clearHealth();
    }

    /**
     * 应用就绪后按真实消费者状态刷新探针。
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void applicationReady() {
        started = true;
        refresh();
    }

    /**
     * 仅所有消费者正常运行时写入健康标记，异常时移除标记。
     */
    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    public synchronized void refresh() {
        try {
            var listeners = registry.getListenerContainers();
            boolean healthy = started && !listeners.isEmpty() && listeners.stream().allMatch(container ->
                    container instanceof SimpleMessageListenerContainer simple
                            && simple.isRunning()
                            && simple.getActiveConsumerCount() >= properties.getConcurrency());
            if (healthy) {
                writeHealth();
            } else {
                clearHealth();
            }
        } catch (IOException | RuntimeException e) {
            clearHealth();
            log.warn("Worker 健康探针更新失败 worker={} cause={}",
                    properties.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 停止健康报告并移除本进程的探针。
     */
    @PreDestroy
    public synchronized void close() {
        started = false;
        clearHealth();
    }

    /**
     * 以原子替换发布当前时间，拒绝链接路径。
     * @throws IOException 目录、权限或原子写入失败
     */
    private void writeHealth() throws IOException {
        WorkerPaths.rejectSymbolicLinks(directory);
        Files.createDirectories(directory);
        WorkerPaths.rejectSymbolicLinks(directory);
        privatePermissions(directory, true);
        Path target = directory.resolve("health");
        WorkerPaths.rejectSymbolicLinks(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Worker 健康标记必须为普通文件");
        }
        Path temporary = Files.createTempFile(directory, ".health-", ".tmp");
        try {
            privatePermissions(temporary, false);
            Files.writeString(temporary, Long.toString(System.currentTimeMillis()) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 移除本组件的健康标记，失败仅记录安全诊断。
     */
    private void clearHealth() {
        try {
            WorkerPaths.rejectSymbolicLinks(directory);
            // 删除本身不跟随最终的符号链接，只移除异常标记，绝不访问其目标。
            Files.deleteIfExists(directory.resolve("health"));
        } catch (IOException | RuntimeException e) {
            log.warn("Worker 健康标记清理失败 worker={} cause={}",
                    properties.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 在支持 POSIX 的文件系统限制为所有者可访问。
     * @param path 受控文件或目录
     * @param folder 是否为目录
     * @throws IOException 设置权限失败
     */
    private static void privatePermissions(Path path, boolean folder) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(folder ? "rwx------" : "rw-------"));
        }
    }
}

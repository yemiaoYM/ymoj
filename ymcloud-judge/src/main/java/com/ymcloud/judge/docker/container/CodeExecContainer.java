package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.exception.CommandTimeoutException;
import com.ymcloud.judge.io.WorkerPaths;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 代码执行容器，输入通过挂载目录中的独立文件提供。
 */
@Getter
public class CodeExecContainer extends DockerContainer {

    private final String hostWorkingDir;
    private final String containerWorkingDir;

    public CodeExecContainer(DockerClient dockerClient, String containerId, String hostWorkingDir,
                             String containerWorkingDir) {
        super(dockerClient, containerId);
        this.hostWorkingDir = hostWorkingDir;
        this.containerWorkingDir = WorkerPaths.containerWorkingDirectory(containerWorkingDir);
    }

    /**
     * 使用当前挂载目录内的 UTF-8 文件提供 stdin，使读取到 EOF 的程序正常结束。
     * 命令及路径通过 shell 位置参数传递，不将输入或命令拼接进脚本。
     */
    @Override
    public CmdExecResult execCmd(String[] cmd, String stdin) throws Exception {
        Objects.requireNonNull(stdin, "标准输入不能为空");
        return execCmd(cmd, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), 30000L);
    }

    /**
     * 复制经过校验的输入流，不解码、不修改空白，并使容器程序收到 EOF。
     */
    public CmdExecResult execCmd(String[] cmd, InputStream stdin, long timeoutMillis) throws Exception {
        Objects.requireNonNull(cmd, "命令不能为空");
        Objects.requireNonNull(stdin, "标准输入不能为空");
        if (cmd.length == 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("命令或执行截止时间无效");
        }
        for (String argument : cmd) {
            Objects.requireNonNull(argument, "命令参数不能为空");
        }

        long started = System.nanoTime();
        WorkerPaths.rejectSymbolicLinks(Path.of(hostWorkingDir));
        Path directory = Path.of(hostWorkingDir).toRealPath();
        Path inputFile = Files.createTempFile(directory, ".judge-stdin-", ".tmp");
        try {
            try (var output = Files.newOutputStream(inputFile, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = stdin.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("输入准备被中断");
                    }
                    output.write(buffer, 0, read);
                }
            }
            String inputPath = containerWorkingDir + "/" + inputFile.getFileName();
            String[] wrappedCommand = new String[cmd.length + 4];
            wrappedCommand[0] = "/bin/sh";
            wrappedCommand[1] = "-c";
            wrappedCommand[2] = "exec \"$@\" < \"$0\"";
            wrappedCommand[3] = inputPath;
            System.arraycopy(cmd, 0, wrappedCommand, 4, cmd.length);
            long remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (remaining <= 0) {
                throw new CommandTimeoutException();
            }
            return execCmd(wrappedCommand, remaining);
        } finally {
            // 只删除本次创建的输入文件，不扫描或清理其他工作文件。
            Files.deleteIfExists(inputFile);
        }
    }

}

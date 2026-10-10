package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.bo.CmdExecResult;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * @PackageName: com.ymcloud.judge.docker.container
 * @ClassName: CodeExecContainer
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  00:18
 * @Description: 代码执行容器
 */
@Getter
public class CodeExecContainer extends DockerContainer {

    private final String hostWorkingDir;
    private final String containerWorkingDir;

    public CodeExecContainer(DockerClient dockerClient, String containerId, String hostWorkingDir,
                             String containerWorkingDir) {
        super(dockerClient, containerId);
        this.hostWorkingDir = hostWorkingDir;
        this.containerWorkingDir = requireContainerWorkingDir(containerWorkingDir);
    }

    /**
     * 使用当前挂载目录内的 UTF-8 文件提供 stdin，使读取到 EOF 的程序正常结束。
     * 命令及路径通过 shell 位置参数传递，不将输入或命令拼接进脚本。
     */
    @Override
    public CmdExecResult execCmd(String[] cmd, String stdin) throws Exception {
        Objects.requireNonNull(cmd, "命令不能为空");
        Objects.requireNonNull(stdin, "标准输入不能为空");
        if (cmd.length == 0) {
            throw new IllegalArgumentException("命令不能为空");
        }
        for (String argument : cmd) {
            Objects.requireNonNull(argument, "命令参数不能为空");
        }

        Path directory = Path.of(hostWorkingDir).toRealPath();
        Path inputFile = Files.createTempFile(directory, ".judge-stdin-", ".tmp");
        try {
            Files.writeString(inputFile, stdin, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            String inputPath = containerWorkingDir + "/" + inputFile.getFileName();
            String[] wrappedCommand = new String[cmd.length + 4];
            wrappedCommand[0] = "/bin/sh";
            wrappedCommand[1] = "-c";
            wrappedCommand[2] = "exec \"$@\" < \"$0\"";
            wrappedCommand[3] = inputPath;
            System.arraycopy(cmd, 0, wrappedCommand, 4, cmd.length);
            return execCmd(wrappedCommand);
        } finally {
            // 只删除本次创建的输入文件，不扫描或清理其他工作文件。
            Files.deleteIfExists(inputFile);
        }
    }

    private String requireContainerWorkingDir(String directory) {
        Objects.requireNonNull(directory, "容器工作目录不能为空");
        if (!directory.startsWith("/") || directory.contains("\\") || directory.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("容器工作目录必须为绝对 Linux 路径");
        }
        for (String segment : directory.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("容器工作目录不能包含相对路径");
            }
        }
        return directory.equals("/") ? "" : directory.replaceAll("/+$", "");
    }

}

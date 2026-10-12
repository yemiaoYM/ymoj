package com.ymcloud.judge.docker.factory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.exception.SandBoxException;
import com.ymcloud.judge.io.WorkerPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 创建无网络、限制资源的代码容器，只挂载当前执行目录。
 */
public class CodeExecContainerFactory implements DockerContainerFactory<CodeExecContainer> {

    private final DockerClient dockerClient;
    private final String hostWorkingDir;
    private final String containerWorkingDir;
    private final Long memoryLimitKb;
    private final String imageName;
    private final String containerUser;

    public CodeExecContainerFactory(DockerClient dockerClient,
                                    String hostWorkingDir,
                                    String containerWorkingDir,
                                    Long memoryLimitKb,
                                    String imageName) {
        this.dockerClient = Objects.requireNonNull(dockerClient, "Docker 客户端不能为空");
        this.containerWorkingDir = WorkerPaths.containerWorkingDirectory(containerWorkingDir);
        if (memoryLimitKb == null || memoryLimitKb < 1024 || memoryLimitKb > 2 * 1024 * 1024L
                || imageName == null || imageName.isBlank()) {
            throw new IllegalArgumentException("代码容器的内存或镜像配置无效");
        }
        // 先校验边界再创建目录或容器，失败时不留下无效容器。
        try {
            Path directory = Path.of(hostWorkingDir).toAbsolutePath().normalize();
            WorkerPaths.rejectSymbolicLinks(directory);
            Files.createDirectories(directory);
            WorkerPaths.rejectSymbolicLinks(directory);
            this.hostWorkingDir = directory.toRealPath().toString();
            if (directory.getFileSystem().supportedFileAttributeViews().contains("unix")) {
                Object uid = Files.getAttribute(directory, "unix:uid", LinkOption.NOFOLLOW_LINKS);
                Object gid = Files.getAttribute(directory, "unix:gid", LinkOption.NOFOLLOW_LINKS);
                this.containerUser = uid + ":" + gid;
            } else {
                this.containerUser = null;
            }
        } catch (IOException e) {
            throw new SandBoxException("无法创建判题工作目录", e);
        }
        this.memoryLimitKb = memoryLimitKb;
        this.imageName = imageName;
    }

    @Override
    public CodeExecContainer createDockerContainer() {
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(memoryLimitKb * 1024L);
        // 内存与 swap 总上限相同，禁止借用 swap 绕过限制。
        hostConfig.withMemorySwap(memoryLimitKb * 1024L);
        hostConfig.withNanoCPUs(1000000000L);
        hostConfig.withNetworkMode("none");
        hostConfig.withReadonlyRootfs(true);
        hostConfig.withTmpFs(Map.of("/tmp", "rw,nosuid,nodev,noexec,size=64m"));
        hostConfig.withPidsLimit(64L);
        hostConfig.withSecurityOpts(List.of("no-new-privileges:true"));
        hostConfig.withCapDrop(Capability.ALL);
        hostConfig.withBinds(new Bind(hostWorkingDir, new Volume(this.containerWorkingDir)));

        CreateContainerCmd command = dockerClient
                .createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withWorkingDir(containerWorkingDir)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                // 预置镜像以 shell 作为 PID 1，保持容器直到本次评测结束。
                .withTty(true);
        if (containerUser != null) {
            // Linux 使用工作目录的属主，保留 0700/0600 权限并支持非 root Worker。
            command.withUser(containerUser);
        }
        CreateContainerResponse container = command.exec();

        return new CodeExecContainer(
                dockerClient,
                container.getId(),
                hostWorkingDir,
                containerWorkingDir);
    }

}

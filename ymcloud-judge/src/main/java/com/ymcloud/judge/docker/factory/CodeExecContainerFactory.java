package com.ymcloud.judge.docker.factory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.docker.factory
 * @ClassName: CodeExecContainerFactory
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  13:46
 * @Description: 代码执行容器工厂
 */
public class CodeExecContainerFactory implements DockerContainerFactory<CodeExecContainer> {

    private final DockerClient dockerClient;
    private final String hostWorkingDir;
    private final String containerWorkingDir;
    private final Long memoryLimitMb;
    private final String imageName;

    public CodeExecContainerFactory(DockerClient dockerClient,
                                    String hostWorkingDir,
                                    String containerWorkingDir,
                                    Long memoryLimitMb,
                                    String imageName) {
        this.dockerClient = dockerClient;
        //根据不同环境获取对应的路径
        this.hostWorkingDir = FileUtils.getAbsolutePath(hostWorkingDir);
        this.containerWorkingDir = containerWorkingDir;
        this.memoryLimitMb = memoryLimitMb;
        this.imageName = imageName;
    }

    @Override
    public CodeExecContainer createDockerContainer() {
        //创建docker容器配置
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(memoryLimitMb * 1024 * 1024L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withBinds(new Bind(hostWorkingDir, new Volume(containerWorkingDir)));
        //创建请求命令
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withWorkingDir(containerWorkingDir)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withTty(true)
                .exec();
        return new CodeExecContainer(
                dockerClient,
                container.getId(),
                hostWorkingDir);
    }

}

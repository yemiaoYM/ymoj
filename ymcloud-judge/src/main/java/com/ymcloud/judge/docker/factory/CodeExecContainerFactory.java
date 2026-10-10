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
    private final Long memoryLimitKb;
    private final String imageName;

    public CodeExecContainerFactory(DockerClient dockerClient,
                                    String hostWorkingDir,
                                    String containerWorkingDir,
                                    Long memoryLimitKb,
                                    String imageName) {
        this.dockerClient = dockerClient;
        //根据不同环境获取对应的路径
        this.hostWorkingDir = FileUtils.getAbsolutePath(hostWorkingDir);
        this.containerWorkingDir = containerWorkingDir;
        this.memoryLimitKb = memoryLimitKb;
        this.imageName = imageName;
    }

    @Override
    public CodeExecContainer createDockerContainer() {
        //创建docker容器配置
        HostConfig hostConfig = new HostConfig();
        //限制的内存数
        hostConfig.withMemory(memoryLimitKb * 1024L);
        //禁止使用swap，即不允许容器超用内存
        hostConfig.withMemorySwap(0L);
        //单核
        hostConfig.withCpuCount(1L);
        //关闭网络
        hostConfig.withNetworkMode("none");
        //防止恶意代码写乱系统目录
        hostConfig.withReadonlyRootfs(true);
        //限制进程数，防fork炸弹
        hostConfig.withPidsLimit(64L);
        //挂载本地文件到docker文件中
        hostConfig.withBinds(new Bind(hostWorkingDir, new Volume(containerWorkingDir)));

        //创建请求命令
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withWorkingDir(containerWorkingDir)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                //挂载一个伪终端，使容器一直运行，系统手动关闭容器
                .withTty(true)
                .exec();

        return new CodeExecContainer(
                dockerClient,
                container.getId(),
                hostWorkingDir,
                containerWorkingDir);
    }

}

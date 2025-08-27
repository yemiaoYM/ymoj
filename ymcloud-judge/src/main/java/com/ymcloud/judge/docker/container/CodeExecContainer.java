package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import lombok.Getter;

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

    public CodeExecContainer(DockerClient dockerClient, String containerId, String hostWorkingDir) {
        super(dockerClient, containerId);
        this.hostWorkingDir = hostWorkingDir;
    }

}

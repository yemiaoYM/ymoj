package com.ymcloud.judge.docker.factory;

import com.ymcloud.judge.docker.container.DockerContainer;

/**
 * @PackageName: com.ymcloud.judge.docker.factory
 * @ClassName: DockerContainerFactory
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  13:45
 * @Description: docker容器工厂
 */
public interface DockerContainerFactory<T extends DockerContainer> {

    /**
     * 创建一个新的 Docker 容器
     * @return 新创建的 Docker 容器实例
     */
    T createDockerContainer();
}

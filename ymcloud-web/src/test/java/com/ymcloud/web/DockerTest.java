package com.ymcloud.web;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.domain.CodeRunResult;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: DockerTest
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  13:53
 * @Description: docker≤‚ ‘
 */
@SpringBootTest
public class DockerTest {

    @Autowired
    private DockerClient dockerClient;

    @Test
    public void testCreateContainer() {
//        String hostWorkingDir="E:\\Java\\ymcloud\\files\\tmp\\submission_10020";
        String hostWorkingDir="E:/Java/ymcloud/files/tmp/submission_10020";
        String absolutePath = FileUtils.getAbsolutePath(hostWorkingDir);
        String containerWorkingDir="/app";

        long memoryLimitMb=128;
        String imageName="oj-java-env";

        CodeExecContainerFactory factory=new CodeExecContainerFactory(dockerClient,absolutePath,containerWorkingDir,memoryLimitMb,imageName);
        CodeExecContainer container = factory.createDockerContainer();

        String containerId = container.getContainerId();
        System.out.println(containerId);

        container.start();

        SandBoxFactory sandBoxFactory = new SandBoxFactory("Java");
        SandBox sandBox = sandBoxFactory.createSandBox(container);

        sandBox.compile(container);

        ArrayList<String> testCases=new ArrayList<>();
        testCases.add("1 2");
        testCases.add("11 55");
        testCases.add("0 0");
        testCases.add("4 5");
        testCases.add("5 5");
        testCases.add("1 2");
        testCases.add("11 55");
        testCases.add("0 0");
        testCases.add("4 5");
        testCases.add("5 5");
        testCases.add("1 2");
        testCases.add("11 55");
        testCases.add("0 0");
        testCases.add("4 5");
        testCases.add("5 5");
        testCases.add("1 1");
        for(String testCase:testCases){
            CodeRunResult execute = sandBox.execute(container, testCase + "\n");
            System.out.println(execute);
        }

//        container.remove(true);
    }

    @Test
    public void testInspectContainer() {
        String containerId="0df2634c851580937c88035960b49657c4d61a523ab1accf2155a9423c940ac6";
        InspectContainerResponse exec = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = exec.getState();
        System.out.println(state);
    }

    @Test
    public void testFilePath() {
        String hostWorkingDir="E:/Java/ymcloud/files/tmp/submission_10020";
        String absolutePath = FileUtils.getAbsolutePath(hostWorkingDir);

        System.out.println(absolutePath);
    }

}

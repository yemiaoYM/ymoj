package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.bo.DockerFrameCallback;
import com.ymcloud.judge.bo.StringInputStream;
import lombok.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.judge.docker.container
 * @ClassName: DockerContainer
 * @Author: Yemiao
 * @CreateTime: 2025-08-24  22:56
 * @Description: docker操作容器
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class DockerContainer {
    /**
     * docker客户端
     */
    private DockerClient dockerClient;
    /**
     * 容器id
     */
    private String containerId;

    public void start() {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stop() {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public void restart() {
        dockerClient.restartContainerCmd(containerId).exec();
    }

    public void pause() {
        dockerClient.pauseContainerCmd(containerId).exec();
    }

    public void unpause() {
        dockerClient.unpauseContainerCmd(containerId).exec();
    }

    public void kill() {
        dockerClient.killContainerCmd(containerId).exec();
    }

    public void remove(boolean force) {
        dockerClient.removeContainerCmd(containerId).withForce(force).exec();
    }

    /**
     * 查看进程信息
     */
    public InspectExecResponse inspectExec(String execId){
        return dockerClient.inspectExecCmd(execId).exec();
    }

    /**
     * 查看容器信息
     */
    public InspectContainerResponse inspectContainer() {
        return dockerClient.inspectContainerCmd(containerId).exec();
    }

    /**
     * 判断容器是否在运行
     */
    public boolean isRunning() {
        return Boolean.TRUE.equals(inspectContainer().getState().getRunning());
    }

    /**
     * 判断容器是否被OOM杀死
     */
    public boolean isOOMKilled() {
        return Boolean.TRUE.equals(inspectContainer().getState().getOOMKilled());
    }

    /**
     * 创建执行命令
     * 可以传入标准输入、标准输出和错误输出的流
     * @param cmd 命令
     * @return 执行命令的ID
     */
    public ExecCreateCmdResponse createCmd(String... cmd) {
        return dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withCmd(cmd)
                .exec();
    }

    /**
     * 执行命令
     * @param cmd 命令
     * @return 执行结果
     */
    public CmdExecResult execCmd(String[] cmd) throws Exception {

        String execID = this.createCmd(cmd).getId();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        dockerClient.execStartCmd(execID)
                .exec(new DockerFrameCallback(out, err))
                .awaitCompletion()
                .close();

        return CmdExecResult.builder()
                .stdout(out.toString(StandardCharsets.UTF_8))
                .stderr(err.toString(StandardCharsets.UTF_8))
                .exitCode(inspectExec(execID).getExitCodeLong())
                .build();
    }

    /**
     * 执行命令带输入流
     * @param cmd 命令
     * @param stdin 输入
     * @return 执行结果
     */
    public CmdExecResult execCmd(String[] cmd, String stdin) throws Exception {

        String execID = this.createCmd(cmd).getId();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        InputStream in = new StringInputStream(stdin);

        dockerClient.execStartCmd(execID)
                .withStdIn(in)
                .exec(new DockerFrameCallback(out,err))
                .awaitCompletion()
                .close();

        return CmdExecResult.builder()
                .stdout(out.toString(StandardCharsets.UTF_8))
                .stderr(err.toString(StandardCharsets.UTF_8))
                .exitCode(inspectExec(execID).getExitCodeLong())
                .build();
    }

}

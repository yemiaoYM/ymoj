package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.ymcloud.judge.domain.CmdExecResult;
import lombok.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public CmdExecResult execCmd(String[] cmd, int timeout, TimeUnit unit) throws InterruptedException {

        String execID = this.createCmd(cmd).getId();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        boolean awaited = dockerClient.execStartCmd(execID)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                out.write(frame.getPayload());
                            } else if (frame.getStreamType() == StreamType.STDERR) {
                                err.write(frame.getPayload());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error writing to output streams", e);
                        }
                    }
                }).awaitCompletion(timeout, unit);

        return CmdExecResult.builder()
                .stdout(out.toString(StandardCharsets.UTF_8))
                .stderr(err.toString(StandardCharsets.UTF_8))
                .isTimeout(!awaited)
                .build();
    }

    public CmdExecResult execCmd(String[] cmd, String stdin, int timeout, TimeUnit unit) throws InterruptedException {

        String execID = this.createCmd(cmd).getId();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        InputStream in = new ByteArrayInputStream(stdin.getBytes());

        boolean awaited = dockerClient.execStartCmd(execID)
                .withStdIn(in)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                out.write(frame.getPayload());
                            } else if (frame.getStreamType() == StreamType.STDERR) {
                                err.write(frame.getPayload());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error writing to output streams", e);
                        }
                    }
                }).awaitCompletion(timeout, unit);

        return CmdExecResult.builder()
                .stdout(out.toString())
                .stderr(err.toString())
                .isTimeout(!awaited)
                .build();
    }

}

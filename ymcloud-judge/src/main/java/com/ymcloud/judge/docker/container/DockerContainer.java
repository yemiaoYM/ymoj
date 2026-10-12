package com.ymcloud.judge.docker.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.bo.DockerFrameCallback;
import com.ymcloud.judge.exception.CommandTimeoutException;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Docker 进程生命周期与有界的命令执行。
 */
@Getter
public abstract class DockerContainer {

    private final DockerClient dockerClient;
    private final String containerId;
    @Setter
    private int maxOutputBytes = 1024 * 1024;

    public DockerContainer(DockerClient dockerClient, String containerId) {
        this.dockerClient = Objects.requireNonNull(dockerClient, "Docker 客户端不能为空");
        this.containerId = Objects.requireNonNull(containerId, "容器标识不能为空");
    }

    public void start() {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stop() {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public void remove(boolean force) {
        dockerClient.removeContainerCmd(containerId).withForce(force).exec();
    }

    public void kill() {
        dockerClient.killContainerCmd(containerId).exec();
    }

    public InspectExecResponse inspectExec(String execId) {
        return dockerClient.inspectExecCmd(execId).exec();
    }

    public InspectContainerResponse inspectContainer() {
        return dockerClient.inspectContainerCmd(containerId).exec();
    }

    public boolean isRunning() {
        return Boolean.TRUE.equals(inspectContainer().getState().getRunning());
    }

    public boolean isOOMKilled() {
        return Boolean.TRUE.equals(inspectContainer().getState().getOOMKilled());
    }

    /**
     * cgroup v2 记录 exec 进程被 OOM 杀死的次数，即使 PID 1 仍在运行也可识别。
     */
    public long oomKillCount() {
        try {
            CmdExecResult result = execCmd(new String[]{"cat", "/sys/fs/cgroup/memory.events"}, 1000L);
            if (result.getExitCode() == 0) {
                for (String line : result.getStdout().split("\\n")) {
                    if (line.startsWith("oom_kill ")) {
                        return Long.parseLong(line.substring(9).trim());
                    }
                }
            }
        } catch (Exception e) {
            // cgroup v1 使用容器 OOM 状态和退出码作为兼容入口。
        }
        return -1L;
    }

    public ExecCreateCmdResponse createCmd(String... cmd) {
        return dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(false)
                .withCmd(cmd)
                .exec();
    }

    public CmdExecResult execCmd(String[] cmd) throws Exception {
        return execCmd(cmd, 30000L);
    }

    public CmdExecResult execCmd(String[] cmd, long timeoutMillis) throws Exception {
        if (timeoutMillis <= 0 || maxOutputBytes < 1) {
            throw new IllegalArgumentException("命令执行边界无效");
        }
        String execId = createCmd(cmd).getId();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        DockerFrameCallback callback = new DockerFrameCallback(out, err, maxOutputBytes);
        try (callback) {
            dockerClient.execStartCmd(execId).exec(callback);
            if (!callback.awaitCompletion(timeoutMillis, TimeUnit.MILLISECONDS)) {
                terminateCommand();
                throw new CommandTimeoutException();
            }
            if (callback.getFailure() != null) {
                throw callback.getFailure();
            }
        } catch (InterruptedException e) {
            terminateCommand();
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            if (!(e instanceof CommandTimeoutException) && callback.getFailure() != null) {
                terminateCommand();
                throw callback.getFailure();
            }
            throw e;
        }
        return CmdExecResult.builder()
                .stdout(out.toString(StandardCharsets.UTF_8))
                .stderr(err.toString(StandardCharsets.UTF_8))
                .exitCode(Objects.requireNonNull(inspectExec(execId).getExitCodeLong(), "命令没有退出状态"))
                .build();
    }

    /**
     * 仅子类可以提供有界的输入实现，禁止遗留 Docker 直连 stdin 的无限等待入口。
     */
    public abstract CmdExecResult execCmd(String[] cmd, String stdin) throws Exception;

    private void terminateCommand() {
        try {
            kill();
        } catch (NotFoundException | ConflictException alreadyStopped) {
            // 截止或 OOM 已终止容器时，清理状态不能覆盖原始 TLE/输出限制原因。
        }
    }
}

package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.exception.CommandTimeoutException;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.exception.MemoryLimitExceededException;
import com.ymcloud.judge.exception.OutputLimitExceededException;
import com.ymcloud.judge.exception.RuntimeErrorException;
import com.ymcloud.judge.exception.SandBoxException;
import com.ymcloud.judge.exception.TimeLimitExceededException;
import com.ymcloud.judge.io.WorkerPaths;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 现有 Docker 沙箱适配，资源单位统一为毫秒和 KiB。
 */
@Slf4j
public abstract class SandBox {

    private static final Pattern RESOURCE_PATTERN = Pattern.compile("__TIME__:(\\d+(?:\\.\\d+)?)\\s+(\\d+)");
    private final CodeExecContainer container;

    public SandBox(CodeExecContainer codeExecContainer) {
        this.container = codeExecContainer;
    }

    public void compile(CodeExecContainer container) {
        compile(container, 11000L);
    }

    public void compile(CodeExecContainer container, long remainingMillis) {
        try {
            CmdExecResult result = container.execCmd(combineCommands(
                    timeoutCommand(Math.min(10000L, remainingMillis)), buildCompileCommand()),
                    Math.max(1L, Math.min(11000L, remainingMillis)));
            if (isLauncherFailure(result.getExitCode())) {
                log.warn("编译工具无法启动 container={} exitCode={}", container.getContainerId(), result.getExitCode());
                throw new SandBoxException("编译工具无法启动，退出码: " + result.getExitCode());
            }
            if (result.getExitCode() != 0L) {
                throw new CompileException("编译错误: " + diagnostic(result.getStderr()));
            }
        } catch (CompileException | SandBoxException e) {
            throw e;
        } catch (CommandTimeoutException e) {
            throw new CompileException("编译超过时间限制");
        } catch (OutputLimitExceededException e) {
            throw new CompileException("编译输出超过大小限制");
        } catch (Exception e) {
            throw new SandBoxException("编译沙箱不可用", e);
        }
    }

    abstract String[] buildCompileCommand();

    abstract String[] buildRunCommand();

    abstract String buildCodeFileName();

    public CodeRunResult execute(CodeExecContainer container, String stdin, Long timeLimitMs) {
        try (InputStream input = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8))) {
            return execute(container, input, timeLimitMs, timeLimitMs + 2000L);
        } catch (IOException e) {
            throw new SandBoxException("无法读取自测输入", e);
        }
    }

    public CodeRunResult execute(CodeExecContainer container, InputStream stdin, long timeLimitMs,
                                 long remainingMillis) {
        CmdExecResult result;
        long previousOom = container.oomKillCount();
        try {
            result = container.execCmd(combineCommands(timeoutCommand(timeLimitMs),
                    new String[]{"/usr/bin/time", "-f", "__TIME__:%e %M"}, buildRunCommand()),
                    stdin, Math.max(1L, Math.min(timeLimitMs + 2000L, remainingMillis)));
        } catch (CommandTimeoutException e) {
            throw new TimeLimitExceededException();
        } catch (OutputLimitExceededException e) {
            throw new RuntimeErrorException(e.getMessage());
        } catch (Exception e) {
            throw new SandBoxException("运行沙箱不可用或输入校验失败", e);
        }
        long currentOom = container.isRunning() ? container.oomKillCount() : -1L;
        if (container.isOOMKilled() || (previousOom >= 0 && currentOom > previousOom)
                || (previousOom < 0 && result.getExitCode() == 137)
                || (this instanceof JavaSandBox && result.getStderr().contains("java.lang.OutOfMemoryError"))) {
            throw new MemoryLimitExceededException();
        }
        long exitCode = result.getExitCode();
        if (exitCode == 124 || exitCode == 142 || exitCode == 152) {
            throw new TimeLimitExceededException();
        }
        Matcher matcher = RESOURCE_PATTERN.matcher(result.getStderr());
        // timeout/time 未启动时没有统计；用户程序自行退出 125/126/127 仍归为 RE。
        if (isLauncherFailure(exitCode) && !matcher.find()) {
            log.warn("运行工具无法启动 container={} exitCode={}", container.getContainerId(), exitCode);
            throw new SandBoxException("运行工具无法启动，退出码: " + exitCode);
        }
        if (exitCode != 0) {
            throw new RuntimeErrorException("运行错误，退出码: " + exitCode);
        }
        long time = -1L;
        long memory = -1L;
        try {
            while (matcher.find()) {
                // time 输出十进制秒，避免浮点截断使 1.13 秒被记为 1129 毫秒。
                time = new BigDecimal(matcher.group(1)).movePointRight(3)
                        .setScale(0, RoundingMode.DOWN)
                        .longValueExact();
                memory = Long.parseLong(matcher.group(2));
            }
        } catch (ArithmeticException | NumberFormatException e) {
            log.warn("沙箱资源统计超出有效范围 container={} cause={}",
                    container.getContainerId(), e.getClass().getSimpleName());
            throw new SandBoxException("沙箱资源统计超出有效范围", e);
        }
        if (time < 0 || memory < 0) {
            log.warn("沙箱缺少资源统计 container={}", container.getContainerId());
            throw new SandBoxException("沙箱缺少资源统计");
        }
        return new CodeRunResult(result.getStdout(), null, time, memory);
    }

    public void saveCode(String code) {
        try {
            WorkerPaths.rejectSymbolicLinks(Path.of(container.getHostWorkingDir()));
            Path directory = Path.of(container.getHostWorkingDir()).toRealPath();
            Path target = directory.resolve(buildCodeFileName());
            if (Files.isSymbolicLink(target)) {
                throw new IllegalStateException("代码路径不能为符号链接");
            }
            Files.writeString(target, code, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        } catch (Exception e) {
            throw new SandBoxException("保存代码文件失败", e);
        }
    }

    private static boolean isLauncherFailure(long exitCode) {
        return exitCode >= 125 && exitCode <= 127;
    }

    private String[] timeoutCommand(long timeoutMillis) {
        return new String[]{"timeout", "-k", "1", String.format(Locale.ROOT, "%.3f",
                Math.max(1L, timeoutMillis) / 1000.0)};
    }

    private String[] combineCommands(String[]... commands) {
        int size = Arrays.stream(commands).mapToInt(command -> command.length).sum();
        String[] result = new String[size];
        int offset = 0;
        for (String[] command : commands) {
            System.arraycopy(command, 0, result, offset, command.length);
            offset += command.length;
        }
        return result;
    }

    private String diagnostic(String message) {
        if (message == null || message.isBlank()) {
            return "编译器返回失败状态";
        }
        return message.substring(0, Math.min(message.length(), 4000));
    }
}

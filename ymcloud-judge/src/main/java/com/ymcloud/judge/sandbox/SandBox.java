package com.ymcloud.judge.sandbox;

import com.ymcloud.common.exception.judge.*;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.domain.CmdExecResult;
import com.ymcloud.judge.domain.CodeRunResult;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: SandBox
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  00:26
 * @Description: 代码沙箱
 */
public abstract class SandBox {

    private final CodeExecContainer container;

    public SandBox(CodeExecContainer codeExecContainer) {
        this.container = codeExecContainer;
    }

    /**
     * 编译代码（如果是编译型语言）
     * @param container 当前容器对象
     */
    public void compile(CodeExecContainer container){
        String[] compileCommand = buildCompileCommand();
        CmdExecResult res = null;
        try {
            res = container.execCmd(compileCommand, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new SandBoxException("编译代码时被中断", e);
        }
        if (!res.getStderr().isEmpty()) {
            throw new CompileException("编译错误: " + res.getStderr());
        }
    }

    /**
     * 构建编译命令
     * @return 编译命令数组
     */
    abstract String[] buildCompileCommand();

    private String[] buildTimeCommand() {
        return new String[]{"time", "-f", "__TIME__:%U %S %E %M"};
    }

    private String[] combineCommands(String[] timeCommand, String[] runCommand) {
        String[] combined = new String[timeCommand.length + runCommand.length];
        System.arraycopy(timeCommand, 0, combined, 0, timeCommand.length);
        System.arraycopy(runCommand, 0, combined, timeCommand.length, runCommand.length);
        return combined;
    }

    /**
     * 执行代码
     * @param container 当前容器对象
     * @param stdin 标准输入流
     * @return 代码运行结果
     */
    public CodeRunResult execute(CodeExecContainer container, String stdin) {
        String[] cmd = combineCommands(buildTimeCommand(), buildRunCommand());
        CmdExecResult res;
        try {
            res = container.execCmd(cmd, stdin, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new SandBoxException("运行代码时出错", e);
        }
        //运行超时
        if(res.isTimeout()) throw new TimeLimitExceededException();
        // 检查容器是否oom
        if (Boolean.TRUE.equals(container.inspectContainer().getState().getOOMKilled())) {
            throw new MemoryLimitExceededException();
        }
        //读输出
        if (res.getStderr().startsWith("__TIME__:")) {
            String[] parts = res.getStderr().substring(9).trim().split(" ");
            long timeUsed = (long)(Double.parseDouble(parts[2].split(":")[1]) * 1000);
            long memoryUsed = Long.parseLong(parts[3]);
            return new CodeRunResult(res.getStdout(), null, timeUsed, memoryUsed);
        }else{
            throw new RuntimeErrorException(res.getStderr().split("__TIME__:")[0].trim());
        }
    }

    /**
     * 构建运行命令
     * @return 运行命令数组
     */
    abstract String[] buildRunCommand();

    /**
     * 构建代码文件名
     * @return 代码文件名
     */
    abstract String buildCodeFileName();

    /**
     * 保存代码到指定工作目录
     * @param code 代码内容
     */
    public void saveCode(String code) {
        String filename = container.getHostWorkingDir() + "/" + buildCodeFileName();
        try {
            FileUtils.writeFile(filename, code);
        } catch (Exception e) {
            throw new SandBoxException("保存代码文件失败", e);
        }
    }

}

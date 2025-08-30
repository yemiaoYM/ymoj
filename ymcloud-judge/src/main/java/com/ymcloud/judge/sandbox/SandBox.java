package com.ymcloud.judge.sandbox;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.exception.judge.*;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.bo.CodeRunResult;

import java.util.List;

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
    public void compile(CodeExecContainer container) {
        //编译时间默认设置为10s
        long timeLimitMs=10000L;
        String[] compileCommand = combineCommands(buildTimeOutCommand(timeLimitMs),buildCompileCommand());
        CmdExecResult res;
        try {
            res = container.execCmd(compileCommand);
        } catch (Exception e) {
            throw new SandBoxException("编译代码时被中断", e);
        }
        if (StrUtil.isNotEmpty(res.getStderr())) {
            throw new CompileException("编译错误: " + res.getStderr());
        }
    }

    /**
     * 构建编译命令
     * @return 编译命令数组
     */
    abstract String[] buildCompileCommand();

    /**
     * 构建时间内存获取命令
     * @return 时间内存获取命令数组
     */
    private String[] buildTimeOutCommand(long timeoutMs) {
        double timeoutSec = Math.max(1.0, timeoutMs / 1000.0);
        String timeoutStr = String.format("%.1f", timeoutSec);
        return new String[]{"timeout", timeoutStr};
    }

    /**
     * 构建时间内存获取命令
     * @return 时间内存获取命令数组
     */
    private String[] buildTimeCommand() {
        return new String[]{"time", "-f", "__TIME__:%e %M"};
    }

    /**
     * 合并多个命令数组
     * @param commands 多个命令数组，按照传入顺序合并
     * @return 合并后的命令数组
     */
    private String[] combineCommands(String[]... commands) {
        int totalLength = 0;
        for (String[] cmd : commands) {
            if (cmd != null) {
                totalLength += cmd.length;
            }
        }
        String[] combined = new String[totalLength];
        int index = 0;
        for (String[] cmd : commands) {
            if (cmd != null) {
                System.arraycopy(cmd, 0, combined, index, cmd.length);
                index += cmd.length;
            }
        }
        return combined;
    }

    /**
     * 执行代码
     * @param container 当前容器对象
     * @param stdin 标准输入流
     * @return 代码运行结果
     */
    public CodeRunResult execute(CodeExecContainer container, String stdin, Long timeLimitMs) {
        String[] cmd = combineCommands(
                buildTimeOutCommand(timeLimitMs),
                buildTimeCommand(),
                buildRunCommand());
        CmdExecResult res;
        try {
            res = container.execCmd(cmd, stdin);
        } catch (Exception e) {
            throw new SandBoxException("运行代码时出错", e);
        }

        // 检查容器是否oom
        if (Boolean.TRUE.equals(container.inspectContainer().getState().getOOMKilled())) {
            throw new MemoryLimitExceededException();
        }

        // 根据退出码来判断状态
        long exitCode = res.getExitCode();
        System.out.println(res.getExitCode());
        System.out.println(res.getStderr());


        if(exitCode == 0){
            if(!StrUtil.contains(res.getStderr(),"__TIME__:")){
                throw new SandBoxException("获取运行时间和内存异常");
            }

            String[] parts = StrUtil.split(res.getStderr(), "__TIME__:").get(1).split("\\s+");
            long timeUsed = (long) (Double.parseDouble(parts[0]) * 1000);
            long memoryUsed = Long.parseLong(parts[1]);

            return new CodeRunResult(res.getStdout(), null, timeUsed, memoryUsed);
        } else if (exitCode == 124 || exitCode == 142 || exitCode == 152) {
            throw new TimeLimitExceededException();
        } else if (exitCode == 137) {
            throw new MemoryLimitExceededException();
        } else if (exitCode == 139) {
            throw new RuntimeErrorException("段错误(Segmentation Fault)");
        } else if (exitCode == 136) {
            throw new RuntimeErrorException("算术错误(除零/溢出等)");
        } else {
            throw new RuntimeErrorException("System Run Error");
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

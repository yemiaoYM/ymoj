package com.ymcloud.judge.bo;

import lombok.Builder;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: CmdExecResult
 * @Author: Yemiao
 * @CreateTime: 2025-08-24  22:57
 * @Description: 命令执行结果
 */
@Data
@Builder
public class CmdExecResult {
    /**
     * 标准输出流
     */
    private String stdout;
    /**
     * 标准错误流
     */
    private String stderr;
    /**
     * 退出码
     */
    private long exitCode;
}

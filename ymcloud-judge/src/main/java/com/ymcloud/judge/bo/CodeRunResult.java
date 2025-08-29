package com.ymcloud.judge.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: CodeRunResult
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  00:33
 * @Description: 代码运行结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeRunResult {
    /**
     * 标准输出
     */
    private String stdout;
    /**
     * 错误信息
     */
    private String errMsg;
    /**
     * 运行时间
     */
    private Long timeUsed = 0L;
    /**
     * 内存占用
     */
    private Long memoryUsed = 0L;

    public CodeRunResult re(String errMsg) {
        return new CodeRunResult("", errMsg, 0L, 0L);
    }
}

package com.ymcloud.judge.domain;

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
    private String stdout;
    private String errMsg;
    private Long timeUsed = 0L;
    private Long memoryUsed = 0L;

    public CodeRunResult re(String errMsg) {
        return new CodeRunResult("", errMsg, 0L, 0L);
    }
}

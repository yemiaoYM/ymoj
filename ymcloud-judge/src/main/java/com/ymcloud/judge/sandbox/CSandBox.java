package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: CSandBox
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:09
 * @Description: c代码沙箱
 */
public class CSandBox extends SandBox {

    public CSandBox(CodeExecContainer codeExecContainer) {
        super(codeExecContainer);
    }

    @Override
    String[] buildCompileCommand() {
        return new String[] { "gcc", "-O2", "-std=c11", "main.c", "-lm", "-o", "main" };
    }

    @Override
    String[] buildRunCommand() {
        return new String[] { "./main" };
    }

    @Override
    String buildCodeFileName() {
        return "main.c";
    }
}
package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: CppSandBox
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:04
 * @Description: c++代码沙箱
 */
public class CppSandBox extends SandBox {

    public CppSandBox(CodeExecContainer codeExecContainer) {
        super(codeExecContainer);
    }

    @Override
    String[] buildCompileCommand() {
        return new String[] { "g++", "-O2", "-w", "-fmax-errors=3", "-std=c++17", "main.cpp", "-lm", "-o", "main" };
    }

    @Override
    String[] buildRunCommand() {
        return new String[]{ "./main" };
    }

    @Override
    String buildCodeFileName() {
        return "main.cpp";
    }
}
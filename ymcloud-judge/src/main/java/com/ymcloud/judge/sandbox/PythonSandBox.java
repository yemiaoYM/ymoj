package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: PythonSandBox
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:12
 * @Description: python代码沙箱
 */
public class PythonSandBox extends SandBox {

    public PythonSandBox(CodeExecContainer codeExecContainer) {
        super(codeExecContainer);
    }

    @Override
    String[] buildCompileCommand() {
        return new String[] { "python3", "-m", "py_compile", "main.py" };
    }

    @Override
    String[] buildRunCommand() {
        return new String[] { "python3", "main.py" };
    }

    @Override
    String buildCodeFileName() {
        return "main.py";
    }
}
package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: JavaSandBox
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  00:31
 * @Description: Java沙箱
 */
public class JavaSandBox extends SandBox {

    public JavaSandBox(CodeExecContainer codeExecContainer) {
        super(codeExecContainer);
    }

    @Override
    String[] buildCompileCommand() {
        return new String[]{"javac", "Main.java"};
    }

    @Override
    String[] buildRunCommand() {
        return new String[]{"java", "Main"};
    }

    @Override
    String buildCodeFileName() {
        return "Main.java";
    }

}

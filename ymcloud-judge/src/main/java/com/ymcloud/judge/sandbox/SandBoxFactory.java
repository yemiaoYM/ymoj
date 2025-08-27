package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: SandBoxFactory
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  00:29
 * @Description: 沙箱工厂
 */
public class SandBoxFactory {

    private final String language;

    public SandBoxFactory(String language) {
        this.language = language;
    }

    public SandBox createSandBox(CodeExecContainer container) {
        return switch (language) {
            case "Java" -> new JavaSandBox(container);
            case "C++" -> new CppSandBox(container);
            case "C" -> new CSandBox(container);
            case "Python" -> new PythonSandBox(container);
            default -> throw new IllegalArgumentException("不支持的语言类型: " + language);
        };
    }
}

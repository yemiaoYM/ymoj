package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.exception.SandBoxException;

/**
 * 根据现有语言创建 Docker 沙箱适配器。
 */
public class SandBoxFactory {

    private final String language;

    public SandBoxFactory(String language) {
        this.language = language;
    }

    /**
     * 根据语言返回代码沙箱
     *
     * @param container 执行容器
     * @return 代码沙箱
     */
    public SandBox createSandBox(CodeExecContainer container) {
        return switch (language) {
            case "Java" -> new JavaSandBox(container);
            case "C++" -> new CppSandBox(container);
            case "C" -> new CSandBox(container);
            case "Python" -> new PythonSandBox(container);
            default -> throw new SandBoxException("不支持的语言类型: " + language);
        };
    }
}

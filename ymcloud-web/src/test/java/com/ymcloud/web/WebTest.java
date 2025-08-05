package com.ymcloud.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: WebTest
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  21:55
 * @Description: 测试类
 */
@SpringBootTest
public class WebTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generatePassword() {
        String encode = passwordEncoder.encode("ojadmin+#");
        System.out.println(encode);

        encode = passwordEncoder.encode("12345");
        System.out.println(encode);
    }

    @Test
    public void testCMD() {
        // 参数
        String submissionId = "10009";
        String problemId = "1";
        String compileCmd = "g++ main.cpp -o main";
        String runCmd = "./main";
        String imageName = "oj-cpp-env";

        // 脚本路径（Linux/WSL 下的路径）
        String scriptPath = "/mnt/e/Java/ymcloud/files/private/docker/judge1.sh";

        // 确保 compileCmd / runCmd 不会被拆开
        String cmd = String.format(
                "bash %s %s %s \"%s\" \"%s\" %s",
                scriptPath,
                submissionId, problemId,
                compileCmd, runCmd, imageName
        );

        System.out.println("执行命令: " + cmd);

        // 执行
        String result = RuntimeUtil.execForStr(cmd);
        System.out.println(result);
    }

    @Test
    public void testCompile() {
        // 参数
        String submissionId = "10009";
        String problemId = "1";
        String compileCmd = "g++ main.cpp -o main";
        String runCmd = "./main";
        String imageName = "oj-cpp-env";

        // 脚本路径（Linux/WSL 下的路径）
        String scriptPath = "/mnt/e/Java/ymcloud/files/private/docker/judge1.sh";
        String compilePath = "/mnt/e/Java/ymcloud/files/private/docker/compile.sh";
        String codePath = "/mnt/e/Java/ymcloud/files/tmp";

        // 确保 compileCmd / runCmd 不会被拆开
        String cmd = String.format(
                "bash %s %s \"%s\" %s %s",
                compilePath,
                submissionId,
                compileCmd,
                imageName,
                codePath
        );

        System.out.println("执行命令: " + cmd);
        String result = RuntimeUtil.execForStr(cmd);
        System.out.println(result);
    }


}

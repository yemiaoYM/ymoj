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
        String encode = passwordEncoder.encode("ojamdin+#");
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
    public void testCompareText(){
        String a="E:\\Java\\ymcloud\\files\\tmp\\submission_10011\\1.out";
        String b="E:\\Java\\ymcloud\\files\\private\\problem\\problem_1\\1.out";
        boolean b1 = compareOutput(a, b);
        System.out.println(b1);
    }

    /**
     * 比较标准输出和用户输出（宽松模式）
     * @param standardFile 标准输出文件
     * @param userFile 用户输出文件
     * @return true 表示匹配
     */
    public static boolean compareOutput(String standardFile, String userFile) {
        List<String> standardLines = FileUtil.readUtf8Lines(standardFile);
        List<String> userLines = FileUtil.readUtf8Lines(userFile);

        // 去掉行尾空格
        standardLines.replaceAll(StrUtil::trimEnd);
        userLines.replaceAll(StrUtil::trimEnd);

        // 去掉末尾空行
        while (!standardLines.isEmpty() && StrUtil.isBlank(standardLines.get(standardLines.size() - 1))) {
            standardLines.remove(standardLines.size() - 1);
        }
        while (!userLines.isEmpty() && StrUtil.isBlank(userLines.get(userLines.size() - 1))) {
            userLines.remove(userLines.size() - 1);
        }

        return standardLines.equals(userLines);
    }

}

package com.ymcloud.web;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.vo.UserAcRankVO;
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
//@SpringBootTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebTest {
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserAcproblemMapper userAcproblemMapper;

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
    public void getDateStamp(){
        long current = DateUtil.current();
        long l = DateUtil.currentSeconds();
        System.out.println(current);
        System.out.println(l);
    }

    @Test
    public void test() {
        //从用户ac表中取数据
        List<UserAcRankVO> userAcRankVOS = userAcproblemMapper.selectUserAcRank();
        //这里注意时构造结果信息，于基于注释方式返回的类型保持一致
        Result<List<UserAcRankVO>> result=Result.ok(userAcRankVOS);
        //向redis中写入
        String key=CacheConstants.CACHE_NAME+"::"+CacheConstants.USER_AC_RANK_KEY;
        redisCache.setCacheObject(key, result);

        Result<List<UserAcRankVO>> userAcRank = redisCache.getCacheObject(key);
        System.out.println(userAcRank);
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
        String codePath = "/mnt/e/Java/ymcloud/files";

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

    @Test
    public void testRun() {
        // 参数
        String submissionId = "10009";
        String problemId = "1";
        String compileCmd = "g++ main.cpp -o main";
        String runCmd = "./main";
        String imageName = "oj-cpp-env";

        // 脚本路径（Linux/WSL 下的路径）
        String runPath = "/mnt/e/Java/ymcloud/files/private/docker/run_multi.sh";
        String compilePath = "/mnt/e/Java/ymcloud/files/private/docker/compile.sh";
        String codePath = "/mnt/e/Java/ymcloud/files";

        // 确保 compileCmd / runCmd 不会被拆开
        String cmd = String.format(
                "bash %s %s %s \"%s\" %s %s",
                runPath,
                submissionId,
                problemId,
                runCmd,
                imageName,
                codePath
        );

        System.out.println("执行命令: " + cmd);
        String result = RuntimeUtil.execForStr(cmd);
        System.out.println(result);
    }

    @Test
    public void testFile(){

        String path = "/Java/ymcloud/files/tmp";
        String filename = path + "/main.cpp";
        File touch = FileUtil.touch(filename);
        System.out.println(touch.getAbsolutePath());
    }

}

package com.ymcloud.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.MailUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.common.utils.ShortSnowflake;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.ContestProblemTableVO;
import com.ymcloud.pojo.vo.ContestSubmissionRecord;
import com.ymcloud.pojo.vo.ProblemVO;
import com.ymcloud.pojo.vo.UserAcRankVO;
import com.ymcloud.web.service.ContestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: WebTest
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  21:55
 * @Description: 测试类
 */
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SpringBootTest
public class WebTest {
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserAcproblemMapper userAcproblemMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private MailUtils mailUtils;

    @Test
    public void testCache(){
        String key="cache::problem";
        redisCache.setCacheObject(key,"hello");
    }

    @Test
    public void testMail(){
        ArrayList<String> tos = new ArrayList<>();
        tos.add("2458544202@qq.com");
        String code= RandomUtil.randomString(4);
        mailUtils.sendCodeMail(tos,code);
    }

    @Test
    public void testUserJudgeStatus(){
//        boolean exists = judgeEntityService.lambdaQuery()
//                .eq(Judge::getProblemId, 1L)
//                .eq(Judge::getUserId, 2L)
//                .eq(Judge::getContestId, 0L)
//                .eq(Judge::getShare, JudgeStatus.STATUS_ACCEPTED)
//                .exists();
//        System.out.println(exists);
        List<ContestSubmissionRecord> contestSubmissionRecordList = judgeEntityService.getContestSubmissionRecordList(1L, LocalDateTime.of(2025, 8, 10, 20, 0), null);
        contestSubmissionRecordList.forEach(System.out::println);
    }

    @Test
    public void generatePassword() {
        String encode = passwordEncoder.encode("ojadmin+#");
        System.out.println(encode);

        encode = passwordEncoder.encode("12345");
        System.out.println(encode);
    }

    @Test
    public void testShortSnowflake() {
        ShortSnowflake snowflake = new ShortSnowflake(1, 1);
        System.out.println(snowflake.nextId());
        //674857423948032
        //674857995364608
        //674858377103616
        //674858554624256
        //1954861094082318336
    }

    @Test
    public void getDateStamp(){
        List<ContestProblemTableVO> list = contestProblemMapper.getContestProblemList(1L, 1L, LocalDateTime.now());
        list.forEach(System.out::println);
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

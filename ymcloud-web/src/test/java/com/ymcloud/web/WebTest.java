package com.ymcloud.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.MailUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.common.utils.ShortSnowflake;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.bo.ContestSubmissionRecord;
import com.ymcloud.pojo.vo.oj.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: WebTest
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  21:55
 * @Description: 旧版手工调试用例，依赖业务数据、邮件服务和固定本地目录。
 * 显式设置 YMCLOUD_RUN_LEGACY_WEB_TESTS=true 后才运行，常规回归使用隔离测试。
 */
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_LEGACY_WEB_TESTS", matches = "true")
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

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private ContestMapper contestMapper;

    @Test
    public void getRank(){
        Page<ContestRankVO> page = new Page<>(1, 5);
        Page<ContestRankVO> oiContestRank = contestMapper.getOIContestRank(page, 10L, null);
        System.out.println(oiContestRank);
    }

    @Test
    public void testMenu(){
        List<Auth> auths = authMapper.selectMenuTreeAll();
        auths.forEach(System.out::println);
    }

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
    public void testFile(){

        String path = "/Java/ymcloud/files/tmp";
        String filename = path + "/main.cpp";
        File touch = FileUtil.touch(filename);
        System.out.println(touch.getAbsolutePath());
    }

}

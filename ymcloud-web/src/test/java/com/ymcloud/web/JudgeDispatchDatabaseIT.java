package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.dao.impl.ContestEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ContestProblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ContestUserEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.JudgeCaseEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.JudgeDispatchEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.JudgeEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.LanguageEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.ProblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.TestDataVersionEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.UserAcproblemEntityServiceImpl;
import com.ymcloud.core.web.dao.impl.UserEntityServiceImpl;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.SubmissionStatusDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.web.controller.admin.contest.AdminContestController;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchPublisher;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.admin.impl.AdminContestServiceImpl;
import com.ymcloud.web.service.common.PublicImageService;
import com.ymcloud.web.service.oj.JudgeService;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 仅连接本地 MySQL，在随机临时库验证真实事务、并发及任务恢复。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_JUDGE_DB_IT", matches = "true")
class JudgeDispatchDatabaseIT {

    static AnnotationConfigApplicationContext context;
    static DriverManagerDataSource administrator;
    static DruidDataSource prepared;
    static String schema;
    static boolean created;
    static JdbcTemplate jdbc;
    static JudgeResultService results;
    static JudgeService service;
    static JudgeDispatchEntityService dispatches;
    static JudgeEntityService judges;
    static JudgeMessageCodec codec;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void open() throws Exception {
        Path root = rootDirectory();
        Map<String, Object> configuration;
        Path developerConfiguration = root.resolve("ymcloud-web/src/main/resources/application-dev.yml");
        try (InputStream stream = Files.newInputStream(developerConfiguration)) {
            configuration = new Yaml().load(stream);
        }
        Map<String, Object> spring = (Map<String, Object>) configuration.get("spring");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        if (datasource.get("druid") instanceof Map<?, ?> druid && druid.containsKey("url")) {
            datasource = (Map<String, Object>) druid;
        }
        String url = String.valueOf(datasource.get("url"));
        if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/[a-zA-Z0-9_]+(\\?.*)?")
                || url.toLowerCase().contains("password=")) {
            throw new IllegalStateException("判题数据库测试仅允许本地连接，禁止使用生产数据库或带密码的 URL");
        }
        String username = String.valueOf(datasource.get("username"));
        String password = String.valueOf(datasource.get("password"));
        administrator = new DriverManagerDataSource(url, username, password);
        schema = "ymcloud_judge_it_" + UUID.randomUUID().toString().replace("-", "");
        verifySchema();
        new JdbcTemplate(administrator).execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        created = true;
        int databaseStart = url.indexOf('/', "jdbc:mysql://".length());
        int queryStart = url.indexOf('?');
        String testUrl = url.substring(0, databaseStart + 1) + schema
                + (queryStart < 0 ? "" : url.substring(queryStart));
        prepared = new DruidDataSource();
        prepared.setUrl(testUrl);
        prepared.setUsername(username);
        prepared.setPassword(password);
        prepared.setMaxActive(8);
        prepared.setMinIdle(0);
        prepared.setInitialSize(0);
        prepared.setMaxWait(10_000);
        try (Connection connection = prepared.getConnection()) {
            assertEquals(schema, connection.getCatalog());
            byte[] sql = Files.readString(root.resolve("sql/ymcloud.sql"), StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(sql), StandardCharsets.UTF_8));
        }
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
        jdbc = new JdbcTemplate(prepared);
        assertEquals(schema, jdbc.queryForObject("SELECT DATABASE()", String.class));
        results = context.getBean(JudgeResultService.class);
        service = context.getBean(JudgeService.class);
        dispatches = context.getBean(JudgeDispatchEntityService.class);
        judges = context.getBean(JudgeEntityService.class);
        codec = context.getBean(JudgeMessageCodec.class);
    }

    /**

     * 创建与业务库隔离的普通判题数据，每个测试从同一状态开始。

     */
    @BeforeEach
    void clear() {
        assertEquals(schema, jdbc.queryForObject("SELECT DATABASE()", String.class));
        List<String> fixtureTables = List.of("judge_dispatch", "judge_case", "user_acproblem",
                "judge", "test_data_version", "problem");
        for (String table : fixtureTables) {
            jdbc.execute("DELETE FROM `" + table + "`");
        }
        jdbc.execute("DELETE FROM `user` WHERE id>1");
        jdbc.update("INSERT INTO `user` (id,username,password,email,status) "
                + "VALUES (2,'judge-test','unused','judge-test@example.invalid',1)");
        jdbc.update("INSERT INTO problem "
                + "(id,problem_id,title,source,judge_mode,time_limit,memory_limit,visibility,author_id) "
                + "VALUES (10,'YMOJ-10','Isolated test',NULL,'default',1000,262144,1,2)");
        jdbc.update("INSERT INTO test_data_version (id,problem_id,manifest,manifest_sha256,created_time) "
                        + "VALUES (77,10,'{}',?,CURRENT_TIMESTAMP)",
                "ab".repeat(32));
        TestDataService testData = context.getBean(TestDataService.class);
        reset(testData, context.getBean(RedisCache.class));
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
    }

    /**
     * 验证重排状态及集合限制，编辑接口即使收到展示 ID 也只更新标题和颜色
     * @throws Exception 编辑接口请求失败
     */
    @Test
    void contestProblemOrderChecksStateAndMembership() throws Exception {
        AdminContestService admin = context.getBean(AdminContestService.class);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,creator_id,creator_username) "
                + "VALUES (98766,'Order IT','2099-01-01','2099-01-02',1,'admin')");
        jdbc.update("INSERT INTO contest_problem (id,contest_id,problem_id,display_id,display_title,color) "
                + "VALUES (987661,98766,10,'A','First','#123456'),(987662,98766,11,'B','Second','#654321')");
        try {
            long statementsBefore = prepared.getExecuteCount2();
            admin.reorderProblems(98766L, List.of(987662L, 987661L));
            assertEquals(4, prepared.getExecuteCount2() - statementsBefore,
                         "重排仅执行锁定、提交检查、集合查询和一次批量更新");
            assertEquals("A", jdbc.queryForObject("SELECT display_id FROM contest_problem WHERE id=987662", String.class));
            assertEquals("First", jdbc.queryForObject("SELECT display_title FROM contest_problem WHERE id=987661", String.class));
            assertThrows(ContestException.class, () -> admin.reorderProblems(98766L, List.of(987661L)));
            assertThrows(ContestException.class, () -> admin.reorderProblems(98766L, List.of(987661L, 987661L)));
            assertThrows(ContestException.class, () -> admin.reorderProblems(98766L, List.of(987661L, 999L)));
            jdbc.update("UPDATE contest SET start_time='2020-01-01' WHERE id=98766");
            assertThrows(ContestException.class, () -> admin.reorderProblems(98766L, List.of(987661L, 987662L)));
            AdminContestController controller = new AdminContestController();
            ReflectionTestUtils.setField(controller, "adminContestService", admin);
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(put("/admin/contest/98766/problems/987661")
                    .param("displayId", "Z")
                    .param("displayTitle", "Renamed")
                    .param("color", "#234567"))
                    .andExpect(status().isOk());
            assertEquals("B", jdbc.queryForObject("SELECT display_id FROM contest_problem WHERE id=987661", String.class));
            assertEquals("Renamed", jdbc.queryForObject("SELECT display_title FROM contest_problem WHERE id=987661", String.class));
            assertEquals("#234567", jdbc.queryForObject("SELECT color FROM contest_problem WHERE id=987661", String.class));
            admin.updateContestProblem(98766L, 987661L, "Renamed again", "#123456");
            jdbc.update("UPDATE contest SET start_time='2099-01-01' WHERE id=98766");
            formal(987660L);
            jdbc.update("UPDATE judge SET contest_id=98766 WHERE id=987660");
            assertThrows(ContestException.class, () -> admin.reorderProblems(98766L, List.of(987661L, 987662L)));
            assertEquals("B", jdbc.queryForObject("SELECT display_id FROM contest_problem WHERE id=987661", String.class));
        } finally {
            jdbc.update("DELETE FROM contest_problem WHERE contest_id=98766");
            jdbc.update("DELETE FROM contest WHERE id=98766");
        }
    }

    /**
     * 十五题重排仍仅执行一次写入，重复保存及其他比赛均不受影响
     */
    @Test
    void contestProblemOrderUsesOneUpdateAtLimit() {
        AdminContestService admin = context.getBean(AdminContestService.class);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,creator_id,creator_username) "
                + "VALUES (98770,'Batch IT','2099-01-01','2099-01-02',1,'admin')");
        List<Long> ids = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ids.add(987700L + i);
            rows.add("(?,98770,?,?,?)");
            parameters.add(987700L + i);
            parameters.add(1000 + i);
            parameters.add(String.valueOf((char) ('A' + i)));
            parameters.add("Problem " + i);
        }
        jdbc.update("INSERT INTO contest_problem (id,contest_id,problem_id,display_id,display_title) VALUES "
                + String.join(",", rows), parameters.toArray());
        jdbc.update("INSERT INTO contest_problem (id,contest_id,problem_id,display_id,display_title) "
                + "VALUES (987715,98771,1000,'Z','Untouched')");
        java.util.Collections.reverse(ids);
        try {
            long statementsBefore = prepared.getExecuteCount2();
            admin.reorderProblems(98770L, ids);
            assertEquals(4, prepared.getExecuteCount2() - statementsBefore);
            assertEquals(ids, jdbc.queryForList("SELECT id FROM contest_problem WHERE contest_id=98770 ORDER BY display_id", Long.class));
            assertEquals("O", jdbc.queryForObject("SELECT display_id FROM contest_problem WHERE id=987700", String.class));
            assertEquals("Problem 0", jdbc.queryForObject("SELECT display_title FROM contest_problem WHERE id=987700", String.class));
            assertEquals("Z", jdbc.queryForObject("SELECT display_id FROM contest_problem WHERE id=987715", String.class));
            admin.reorderProblems(98770L, ids);
            assertEquals(ids, jdbc.queryForList("SELECT id FROM contest_problem WHERE contest_id=98770 ORDER BY display_id", Long.class));
        } finally {
            jdbc.update("DELETE FROM contest_problem WHERE contest_id IN (98770,98771)");
            jdbc.update("DELETE FROM contest WHERE id=98770");
        }
    }

    /**
     * 并发添加共享最后一个名额时只允许一次成功，不改变被拒绝题目的权限
     * @throws Exception 并发任务执行失败时报告测试失败
     */
    @Test
    void contestProblemLimitSerializesConcurrentAdds() throws Exception {
        AdminContestService admin = context.getBean(AdminContestService.class);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,creator_id,creator_username) "
                + "VALUES (98767,'Limit IT','2099-01-01','2099-01-02',1,'admin')");
        for (int i = 0; i < 14; i++) {
            jdbc.update("INSERT INTO contest_problem (contest_id,problem_id,display_id,display_title) VALUES (98767,?,?,?)",
                        1000 + i, String.valueOf((char) ('A' + i)), "Existing");
        }
        jdbc.update("INSERT INTO problem (id,problem_id,title,judge_mode,visibility) VALUES (11,'LIMIT-11','Other','default',2)");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (long id : List.of(10L, 11L)) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    try {
                        admin.addProblemsToContest(98767L, List.of(id));
                        return true;
                    } catch (ContestException expected) {
                        assertTrue(expected.getMessage().contains("15"));
                        return false;
                    }
                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successful++;
                }
            }
            assertEquals(1, successful);
            assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM contest_problem WHERE contest_id=98767", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE id IN (10,11) AND visibility=3", Integer.class));
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM contest_problem WHERE contest_id=98767");
            jdbc.update("DELETE FROM contest WHERE id=98767");
        }
    }

    /**
     * 标题命中不能绕过难度条件或排除已添加题目的条件
     */
    @Test
    void contestProblemSearchCombinesKeywordAndFilters() {
        AdminContestService admin = context.getBean(AdminContestService.class);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,creator_id,creator_username) "
                + "VALUES (98768,'Search IT','2099-01-01','2099-01-02',1,'admin')");
        jdbc.update("INSERT INTO problem (id,problem_id,title,difficulty,judge_mode,visibility) "
                + "VALUES (11,'FILTER-11','Isolated match',2,'default',2),(12,'FILTER-12','Isolated match',2,'default',3)");
        jdbc.update("INSERT INTO contest_problem (contest_id,problem_id,display_id,display_title) VALUES (98768,12,'A','Added')");
        try {
            var query = new com.ymcloud.pojo.query.admin.AdminProblemPageQuery();
            query.setKeyword("Isolated");
            query.setDifficulty(2);
            var page = admin.getAvailableProblems(98768L, query);
            assertEquals(List.of(11L), page.getRecords().stream().map(Problem::getId).toList());
        } finally {
            jdbc.update("DELETE FROM contest_problem WHERE contest_id=98768");
            jdbc.update("DELETE FROM contest WHERE id=98768");
        }
    }

    /**
     * 批量重排中途写入失败时回滚所有题号
     */
    @Test
    void contestProblemOrderRollsBackAsOneTransaction() {
        AdminContestService admin = context.getBean(AdminContestService.class);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,creator_id,creator_username) "
                + "VALUES (98769,'Rollback IT','2099-01-01','2099-01-02',1,'admin')");
        jdbc.update("INSERT INTO contest_problem (id,contest_id,problem_id,display_id,display_title) "
                + "VALUES (987691,98769,10,'A','One'),(987692,98769,11,'B','Two')");
        jdbc.execute("CREATE TRIGGER contest_order_it_fail BEFORE UPDATE ON contest_problem FOR EACH ROW "
                + "BEGIN IF OLD.id=987692 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> admin.reorderProblems(98769L, List.of(987692L, 987691L)));
            assertEquals(List.of("A", "B"), jdbc.queryForList("SELECT display_id FROM contest_problem WHERE contest_id=98769 ORDER BY id", String.class));
        } finally {
            jdbc.execute("DROP TRIGGER contest_order_it_fail");
            jdbc.update("DELETE FROM contest_problem WHERE contest_id=98769");
            jdbc.update("DELETE FROM contest WHERE id=98769");
        }
    }

    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
        if (prepared != null) {
            prepared.close();
        }
        if (created) {
            verifySchema();
            new JdbcTemplate(administrator).execute("DROP DATABASE `" + schema + "`");
        }
    }

    /**

     * 验证实体映射可清空来源，且数据库拒绝非法难度和判题方式。

     */
    @Test
    void problemMetadataMappingAndConstraintsAreEffective() {
        ProblemEntityService problems = context.getBean(ProblemEntityService.class);
        Problem problem = problems.getById(10L);
        assertEquals("default", problem.getJudgeMode());
        assertEquals(1, problem.getDifficulty());
        problem.setSource("此题来源于洛谷 P1000");
        problem.setDifficulty(5);
        assertTrue(problems.updateById(problem));
        assertEquals(problem.getSource(), problems.getById(10L).getSource());
        problem.setSource(null);
        assertTrue(problems.updateById(problem));
        assertNull(problems.getById(10L).getSource());
        assertEquals(5, problems.getById(10L).getDifficulty());
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE problem SET difficulty=0 WHERE id=10"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE problem SET judge_mode='unknown' WHERE id=10"));
        assertEquals("default", problems.getById(10L).getJudgeMode());
        assertEquals(0, count("judge"));
    }

    @Test
    void repeatedAndOutOfOrderResultsHaveOneBusinessEffect() {
        JudgeDispatch dispatch = formal(100L);
        JudgeResult result = accepted(dispatch);
        results.receive(result);
        results.receive(result);
        JudgeResult progress = accepted(dispatch);
        progress.setSequence(1);
        progress.setTerminal(false);
        progress.setStatus(JudgeVerdict.STATUS_COMPILING);
        progress.setTestCaseResults(null);
        results.receive(progress);
        assertEquals(2, count("judge_case"));
        assertEquals(1, count("user_acproblem"));
        assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(100L).getStatus());
        assertEquals(3, dispatches.getById(dispatch.getId()).getLastResultSequence());
    }

    @Test
    void databaseFailureRollsBackDispatchJudgeCasesAndAcTogether() {
        JudgeDispatch dispatch = formal(100L);
        jdbc.execute("CREATE TRIGGER judge_it_fail BEFORE INSERT ON judge_case FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated test failure'");
        try {
            assertThrows(RuntimeException.class, () -> results.receive(accepted(dispatch)));
        } finally {
            jdbc.execute("DROP TRIGGER judge_it_fail");
        }
        assertEquals(JudgeStatus.STATUS_PENDING, judges.getById(100L).getStatus());
        assertEquals(JudgeDispatchState.READY, dispatches.getById(dispatch.getId()).getDispatchState());
        assertEquals(0, dispatches.getById(dispatch.getId()).getLastResultSequence());
        assertEquals(0, count("judge_case"));
        assertEquals(0, count("user_acproblem"));
        results.receive(accepted(dispatch));
        assertEquals(1, count("user_acproblem"));
    }

    @Test
    void simultaneousDuplicateResultsAndIndependentAcceptedSubmissionsAreIdempotent() throws Exception {
        long started = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            // 空表及不断增长的索引末端都要覆盖；直接调用真实事务，不依靠监听重试掩盖死锁。
            for (int round = 0; round < 10; round++) {
                JudgeDispatch first = formal(100L + round * 2);
                JudgeDispatch second = formal(101L + round * 2);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int index = 0; index < 12; index++) {
                    JudgeResult message = accepted(index % 2 == 0 ? first : second);
                    futures.add(executor.submit(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        results.receive(message);
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
                assertEquals((round + 1) * 4, count("judge_case"));
                assertEquals(1, count("user_acproblem"));
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(40, count("judge_case"));
        assertEquals(1, count("user_acproblem"));
        System.out.println("Judge database concurrency: 120 deliveries / 20 submissions / 4 threads / "
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + " ms");
    }

    @Test
    void wrongExecutionOrVersionNeverChangesCurrentSubmission() {
        JudgeDispatch dispatch = formal(100L);
        JudgeResult result = accepted(dispatch);
        result.setExecutionId(UUID.randomUUID().toString());
        results.receive(result);
        result = accepted(dispatch);
        result.setTestDataVersionId(78L);
        results.receive(result);
        assertEquals(JudgeStatus.STATUS_PENDING, judges.getById(100L).getStatus());
        assertEquals(0, count("judge_case"));
        assertEquals(0, count("user_acproblem"));
    }

    @Test
    void outboxInsertFailureRollsBackSubmissionCreation() {
        jdbc.execute("CREATE TRIGGER judge_it_fail_outbox BEFORE INSERT ON judge_dispatch FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated outbox failure'");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            JudgeDTO request = new JudgeDTO();
            request.setIsContest(false);
            request.setProblemId("YMOJ-10");
            request.setLanguage("Python");
            request.setCode("print(input())");
            assertThrows(RuntimeException.class, () -> service.submitJudge(request));
        } finally {
            jdbc.execute("DROP TRIGGER judge_it_fail_outbox");
        }
        assertEquals(0, count("judge"));
        assertEquals(0, count("judge_dispatch"));
    }

    @Test
    void submissionInsertFailureRollsBackReservedTaskWithoutCreationSuccessLogOrCacheWrite() {
        Logger logger = (Logger) LoggerFactory.getLogger(JudgeServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        jdbc.execute("CREATE TRIGGER judge_it_fail_submission BEFORE INSERT ON judge FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated submission failure'");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            JudgeDTO request = new JudgeDTO();
            request.setIsContest(false);
            request.setProblemId("YMOJ-10");
            request.setLanguage("Python");
            request.setCode("print(input())");
            assertThrows(RuntimeException.class, () -> service.submitJudge(request));
            assertEquals(0, count("judge"));
            assertEquals(0, count("judge_dispatch"));
            assertTrue(appender.list.isEmpty());
            verifyNoInteractions(context.getBean(RedisCache.class));
        } finally {
            jdbc.execute("DROP TRIGGER judge_it_fail_submission");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void taskReservationRequiresAnExistingSubmissionTransaction() {
        JudgeDispatchIdAllocator allocator = context.getBean(JudgeDispatchIdAllocator.class);
        assertThrows(IllegalTransactionStateException.class,
                () -> allocator.reserve(new JudgeDispatch()));
        assertEquals(0, count("judge_dispatch"));
    }

    @Test
    void snapshotUpdateFailureRollsBackTheReservedIdAndFormalSubmission() {
        jdbc.execute("CREATE TRIGGER judge_it_fail_snapshot BEFORE UPDATE ON judge_dispatch FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated snapshot failure'");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser)
                    .thenReturn(context.getBean(UserEntityService.class).getById(2L));
            JudgeDTO request = new JudgeDTO();
            request.setIsContest(false);
            request.setProblemId("YMOJ-10");
            request.setLanguage("Python");
            request.setCode("print(input())");
            assertThrows(RuntimeException.class, () -> service.submitJudge(request));
        } finally {
            jdbc.execute("DROP TRIGGER judge_it_fail_snapshot");
        }
        assertEquals(0, count("judge_dispatch"));
        assertEquals(0, count("judge"));
    }

    @Test
    void freshEmptyOutboxHasAValidInitialSubmissionCounter() {
        jdbc.execute("ALTER TABLE judge_dispatch AUTO_INCREMENT=1");
        JudgeDispatchIdAllocator allocator = createJudgeDispatchIdAllocator(dispatches, judges, jdbc);
        assertDoesNotThrow(allocator::verifyCounter);
    }

    @Test
    void emptyOutboxWithoutAutoIncrementCannotPassTheStartupGuard() {
        jdbc.execute("ALTER TABLE judge_dispatch MODIFY id BIGINT UNSIGNED NOT NULL");
        try {
            String extra = jdbc.queryForObject("SELECT EXTRA FROM information_schema.columns "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch' AND COLUMN_NAME='id'",
                    String.class);
            assertNotNull(extra);
            assertFalse(extra.contains("auto_increment"));
            JudgeDispatchIdAllocator allocator = createJudgeDispatchIdAllocator(dispatches, judges, jdbc);
            assertThrows(IllegalStateException.class, allocator::verifyCounter);
        } finally {
            jdbc.execute("ALTER TABLE judge_dispatch MODIFY id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT");
        }
    }

    @Test
    void selfTestSurvivesCacheLossAndResultCreatesNoFormalOrAcRecords() {
        TestJudgeDTO request = new TestJudgeDTO();
        request.setLanguage("Python");
        request.setCode("print(input())");
        request.setInput("  raw\n\n");
        request.setExpectedOutput("  raw\n");
        Long id;
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            id = service.testJudge(request);
        }
        JudgeDispatch dispatch = dispatches.lambdaQuery()
                .eq(JudgeDispatch::getSubmissionId, id)
                .one();
        JudgeResult result = accepted(dispatch);
        result.setTestCaseResults(List.of(TestCaseResult.builder()
                .caseIndex(1)
                .status(JudgeVerdict.STATUS_ACCEPTED)
                .output("  raw\n")
                .build()));
        results.receive(result);
        when(context.getBean(RedisCache.class).getCacheObject(anyString()))
                .thenThrow(new IllegalStateException("offline"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            assertEquals(request.getInput(), service.getStatus(id).getInput());
            assertEquals("  raw\n", service.getStatus(id).getOutput());
            assertEquals(JudgeStatus.STATUS_ACCEPTED, service.getStatus(id).getStatus());
            security.when(SecurityUtils::getUserId).thenReturn(3L);
            assertThrows(RuntimeException.class, () -> service.getStatus(id));
        }
        assertEquals(0, count("judge"));
        assertEquals(0, count("judge_case"));
        assertEquals(0, count("user_acproblem"));
    }

    @Test
    void oversizedUtf8AndEscapedSelfTestsRollBackTheReservedTaskWithAUserFacingError() {
        TestJudgeDTO request = new TestJudgeDTO();
        request.setLanguage("Python");
        request.setCode("print(input())");
        request.setExpectedOutput("");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            for (String input : List.of("中".repeat(1024 * 1024), "\0".repeat(600 * 1024))) {
                request.setInput(input);
                JudgeException exception = assertThrows(JudgeException.class, () -> service.testJudge(request));
                assertTrue(exception.getMessage().contains("3MiB"));
                assertEquals(0, count("judge_dispatch"));
                assertEquals(0, count("judge"));
                verifyNoInteractions(context.getBean(RedisCache.class));
            }
        }
    }

    @Test
    void confirmedTaskRecoveryIsFiniteAndKeepsExecutionAndPinnedData() {
        JudgeDispatch dispatch = formal(100L);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        JudgeDispatchPublisher publisher = createJudgeDispatchPublisher(dispatches, results, rabbit, codec,
                context.getBean(JudgeDispatchProperties.class), "isolated.exchange", "judge");
        publisher.tick();
        assertEquals(JudgeDispatchState.WAITING, dispatches.getById(dispatch.getId()).getDispatchState());
        for (int attempt = 0; attempt < 3; attempt++) {
            jdbc.update("UPDATE judge_dispatch SET next_attempt_time=? WHERE id=?",
                    LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), dispatch.getId());
            publisher.tick();
            assertEquals(JudgeDispatchState.WAITING, dispatches.getById(dispatch.getId()).getDispatchState(),
                    "立即恢复的任务应在本轮调度重新发布，不能因 DATETIME 舍入滞留 READY");
        }
        JudgeDispatch recovered = dispatches.getById(dispatch.getId());
        assertEquals(2, recovered.getRecoveryAttempts());
        assertEquals(3, recovered.getPublishAttempts());
        assertEquals(dispatch.getExecutionId(), recovered.getExecutionId());
        assertEquals(77L, codec.task(recovered.getTaskPayload()).getTestDataVersionId());
        verify(rabbit, times(3)).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        jdbc.update("UPDATE judge_dispatch SET deadline_time=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), dispatch.getId());
        publisher.tick();
        results.receive(accepted(dispatch));
        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, judges.getById(100L).getStatus());
        assertEquals(JudgeDispatchState.COMPLETED, dispatches.getById(dispatch.getId()).getDispatchState());
    }

    @Test
    void recoveryPublishFailureCannotOverrideAnAlreadyRunningWorker() {
        JudgeDispatch dispatch = formal(100L);
        JudgeResult progress = accepted(dispatch);
        progress.setTerminal(false);
        progress.setSequence(1);
        progress.setStatus(JudgeVerdict.STATUS_COMPILING);
        progress.setTestCaseResults(null);
        results.receive(progress);
        jdbc.update("UPDATE judge_dispatch SET dispatch_state=0,publish_attempts=5 WHERE id=?", dispatch.getId());
        results.failPublishing(100L, dispatch.getExecutionId(), "恢复发送失败");
        assertEquals(JudgeDispatchState.WAITING, dispatches.getById(dispatch.getId()).getDispatchState());
        assertEquals(JudgeStatus.STATUS_COMPILING, judges.getById(100L).getStatus());
        results.receive(accepted(dispatch));
        assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(100L).getStatus());
        assertEquals(1, count("user_acproblem"));
    }

    @Test
    void formalAndSelfTestShareUniqueJsSafeDatabaseIdsUnderConcurrency() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        Set<Long> ids = new HashSet<>();
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                final boolean formal = index % 2 == 0;
                futures.add(executor.submit(() -> {
                    start.await();
                    try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
                         MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
                        security.when(SecurityUtils::getUser)
                                .thenReturn(context.getBean(UserEntityService.class).getById(2L));
                        if (formal) {
                            JudgeDTO request = new JudgeDTO();
                            request.setIsContest(false);
                            request.setProblemId("YMOJ-10");
                            request.setLanguage("Python");
                            request.setCode("print(input())");
                            return service.submitJudge(request);
                        }
                        TestJudgeDTO request = new TestJudgeDTO();
                        request.setLanguage("Python");
                        request.setCode("print(input())");
                        return service.testJudge(request);
                    }
                }));
            }
            start.countDown();
            for (Future<Long> future : futures) {
                Long id = future.get(30, TimeUnit.SECONDS);
                assertTrue(id <= JudgeDispatchIdAllocator.MAX_SAFE_ID);
                assertEquals(id.longValue(), (long) id.doubleValue());
                assertTrue(ids.add(id));
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(12, count("judge_dispatch"));
        assertEquals(6, count("judge"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM judge_dispatch "
                        + "WHERE submission_id IS NULL OR task_payload=''",
                Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM judge_dispatch WHERE id<>submission_id",
                Integer.class));
        for (JudgeDispatch dispatch : dispatches.list()) {
            assertEquals(dispatch.getSubmissionId(), codec.task(dispatch.getTaskPayload()).getSubmissionId());
        }
    }

    @Test
    void startupGuardDetectsMissingLegacyIdSeedAndAcceptsCorrectUpgrade() {
        long legacy = 1_000_000_000_000L;
        formal(legacy);
        JudgeDispatchIdAllocator allocator = createJudgeDispatchIdAllocator(dispatches, judges, jdbc);
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        jdbc.execute("ALTER TABLE judge_dispatch AUTO_INCREMENT=" + (legacy + 1));
        assertDoesNotThrow(allocator::verifyCounter);
    }

    @Test
    void unsafeIdFailsWithoutPersistingAPartialTask() {
        jdbc.execute("ALTER TABLE judge_dispatch AUTO_INCREMENT=" + (JudgeDispatchIdAllocator.MAX_SAFE_ID + 1));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            TestJudgeDTO request = new TestJudgeDTO();
            request.setLanguage("Python");
            request.setCode("print(input())");
            assertThrows(JudgeException.class, () -> service.testJudge(request));
            assertEquals(0, count("judge_dispatch"));
            assertEquals(0, count("judge"));
        } finally {
            jdbc.execute("ALTER TABLE judge_dispatch AUTO_INCREMENT=1");
        }
    }

    /**
     * 重评读取当前配置，清除旧结果和 AC，晚到旧结果不能覆盖新执行
     */
    @Test
    void rejudgeUsesCurrentSnapshotAndDiscardsPreviousResults() {
        JudgeDispatch old = formal(800L);
        results.receive(accepted(old));
        LocalDateTime createdTime = judges.getById(800L).getCreatedTime();
        jdbc.update("INSERT INTO test_data_version (id,problem_id,manifest,manifest_sha256,created_time) "
                + "VALUES (88,10,'{}',?,CURRENT_TIMESTAMP)", "cd".repeat(32));
        jdbc.update("UPDATE problem SET time_limit=2345,memory_limit=1048576 WHERE id=10");
        jdbc.update("UPDATE language SET version='rejudge-current' WHERE name='Python'");
        when(context.getBean(TestDataService.class).getCurrentVersion(10L)).thenReturn(88L);
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            service.rejudge(800L, old.getExecutionId());
        }
        Judge judge = judges.getById(800L);
        JudgeDispatch current = dispatches.lambdaQuery().eq(JudgeDispatch::getSubmissionId, 800L).one();
        JudgeTask task = codec.task(current.getTaskPayload());
        assertEquals(800L, task.getSubmissionId());
        assertNotEquals(old.getExecutionId(), task.getExecutionId());
        assertEquals(88L, task.getTestDataVersionId());
        assertEquals("cd".repeat(32), task.getManifestSha256());
        assertEquals(2345L, task.getTimeLimit());
        assertEquals(1048576L, task.getMemoryLimit());
        assertEquals("print(1)", task.getCode());
        assertEquals("Python", task.getLanguageConfig().getName());
        assertEquals("rejudge-current", task.getLanguageConfig().getVersion());
        assertEquals(createdTime, judge.getCreatedTime());
        assertEquals(2L, judge.getUserId());
        assertEquals(JudgeStatus.STATUS_PENDING, judge.getStatus());
        assertNull(judge.getScore());
        assertNull(judge.getTime());
        assertNull(judge.getMemory());
        assertNull(judge.getErrorMessage());
        assertEquals(0, count("judge_case"));
        assertEquals(0, count("user_acproblem"));
        results.receive(accepted(old));
        assertEquals(JudgeStatus.STATUS_PENDING, judges.getById(800L).getStatus());
        results.receive(accepted(current));
        assertEquals(2, count("judge_case"));
        assertEquals(1, count("user_acproblem"));
    }

    /**
     * 同一原执行的并发重评最多创建一次新执行
     * @throws Exception 并发任务失败时向测试框架报告
     */
    @Test
    void concurrentRejudgeCommandsCreateOneExecution() throws Exception {
        JudgeDispatch old = formal(801L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    try (MockedStatic<SecurityUtils> security = superAdmin()) {
                        service.rejudge(801L, old.getExecutionId());
                        return true;
                    } catch (SubmissionException e) {
                        assertEquals(409, e.getCode());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, count("judge_dispatch"));
            assertEquals(1, count("judge"));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 人工状态阻断旧 Worker，AC 撤回时保留同用户同题的其他 AC
     */
    @Test
    void manualStatusReconcilesAcAndRejectsLateResults() {
        JudgeDispatch first = formal(802L);
        JudgeDispatch second = formal(803L);
        results.receive(accepted(first));
        results.receive(accepted(second));
        SubmissionStatusDTO request = new SubmissionStatusDTO();
        request.setExecutionId(first.getExecutionId());
        request.setStatus(-1);
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            service.updateSubmissionStatus(802L, request);
            assertThrows(SubmissionException.class, () -> service.updateSubmissionStatus(802L, request));
            assertEquals(JudgeStatus.STATUS_WRONG_ANSWER, service.getStatus(802L).getStatus());
            assertEquals(803L, jdbc.queryForObject("SELECT submission_id FROM user_acproblem", Long.class));
            request.setExecutionId(second.getExecutionId());
            service.updateSubmissionStatus(803L, request);
        }
        assertEquals(0, count("user_acproblem"));
        assertEquals(0, count("judge_case"));
        results.receive(accepted(first));
        results.receive(accepted(second));
        assertEquals(JudgeStatus.STATUS_WRONG_ANSWER, judges.getById(802L).getStatus());
        assertEquals(1, judges.getById(802L).getIsManual());
        request.setExecutionId(judges.getById(802L).getExecutionId());
        request.setStatus(0);
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            service.updateSubmissionStatus(802L, request);
        }
        assertEquals(1, count("user_acproblem"));
    }

    /**
     * 历史提交无任务箱也可重新评测，保留提交编号
     */
    @Test
    void legacySubmissionCanBeRejudged() {
        formal(804L);
        jdbc.update("DELETE FROM judge_dispatch WHERE submission_id=804");
        jdbc.update("UPDATE judge SET execution_id=NULL,test_data_version_id=NULL WHERE id=804");
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            service.rejudge(804L, null);
            assertThrows(SubmissionException.class, () -> service.rejudge(804L, null));
        }
        assertEquals(1, count("judge"));
        assertEquals(77L, judges.getById(804L).getTestDataVersionId());
        assertNotNull(judges.getById(804L).getExecutionId());
    }

    /**
     * 新任务写入失败时旧任务、测试点、提交和 AC 必须一起回滚
     */
    @Test
    void rejudgeInsertFailureRestoresAllPreviousState() {
        JudgeDispatch old = formal(805L);
        results.receive(accepted(old));
        jdbc.execute("CREATE TRIGGER fail_rejudge BEFORE INSERT ON judge_dispatch FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated failure'");
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            assertThrows(RuntimeException.class, () -> service.rejudge(805L, old.getExecutionId()));
        } finally {
            jdbc.execute("DROP TRIGGER fail_rejudge");
        }
        assertEquals(old.getExecutionId(), judges.getById(805L).getExecutionId());
        assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(805L).getStatus());
        assertEquals(old.getExecutionId(), dispatches.lambdaQuery().eq(JudgeDispatch::getSubmissionId, 805L).one().getExecutionId());
        assertEquals(2, count("judge_case"));
        assertEquals(1, count("user_acproblem"));
    }

    /**
     * 当前测试数据不可用时拒绝重评，原评测状态和结果保持完整
     */
    @Test
    void unavailableCurrentDataLeavesOriginalResultUntouched() {
        JudgeDispatch old = formal(807L);
        results.receive(accepted(old));
        when(context.getBean(TestDataService.class).getCurrentVersion(10L)).thenReturn(null);
        try (MockedStatic<SecurityUtils> security = superAdmin()) {
            assertThrows(JudgeException.class, () -> service.rejudge(807L, old.getExecutionId()));
        }
        assertEquals(old.getExecutionId(), judges.getById(807L).getExecutionId());
        assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(807L).getStatus());
        assertEquals(2, count("judge_case"));
        assertEquals(1, count("user_acproblem"));
    }

    /**
     * 重评与旧结果并发时最终保持新执行，其他提交的 AC 完成仍能正确汇总
     * @throws Exception 并发任务异常时向测试框架报告
     */
    @Test
    void concurrentRejudgeAndResultsKeepCurrentExecutionAndAcRecord() throws Exception {
        JudgeDispatch old = formal(808L);
        JudgeDispatch other = formal(809L);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> rejudge = executor.submit(() -> {
                start.await();
                try (MockedStatic<SecurityUtils> security = superAdmin()) {
                    service.rejudge(808L, old.getExecutionId());
                }
                return null;
            });
            Future<?> oldResult = executor.submit(() -> {
                start.await();
                results.receive(accepted(old));
                return null;
            });
            Future<?> otherResult = executor.submit(() -> {
                start.await();
                results.receive(accepted(other));
                return null;
            });
            start.countDown();
            rejudge.get(15, TimeUnit.SECONDS);
            oldResult.get(15, TimeUnit.SECONDS);
            otherResult.get(15, TimeUnit.SECONDS);
            assertEquals(JudgeStatus.STATUS_PENDING, judges.getById(808L).getStatus());
            assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(809L).getStatus());
            assertEquals(809L, jdbc.queryForObject("SELECT submission_id FROM user_acproblem", Long.class));
            assertEquals(2, count("judge_case"));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 已结束的比赛提交仍可重评并按当前赛制计分，人工分数不污染普通 AC
     */
    @Test
    void contestRejudgeAndManualScoresPreserveContestIdentity() {
        JudgeDispatch old = formal(806L);
        jdbc.update("INSERT INTO contest (id,title,start_time,end_time,mode,creator_id,creator_username) "
                + "VALUES (98765,'Rejudge IT','2020-01-01','2020-01-02',1,1,'admin')");
        try {
            jdbc.update("UPDATE judge SET contest_id=98765,contest_problem_id=123,created_time='2020-01-01 01:00:00' WHERE id=806");
            try (MockedStatic<SecurityUtils> security = superAdmin()) {
                service.rejudge(806L, old.getExecutionId());
                JudgeDispatch current = dispatches.lambdaQuery().eq(JudgeDispatch::getSubmissionId, 806L).one();
                assertEquals(1, codec.task(current.getTaskPayload()).getMode());
                assertEquals(123L, judges.getById(806L).getContestProblemId());
                ContestEntityService contests = context.getBean(ContestEntityService.class);
                var pendingRank = contests.getOIContestRank(new Page<>(1, 20), 98765L, null).getRecords().get(0);
                assertEquals(0, pendingRank.getAc());
                assertEquals(0, pendingRank.getTotalScore());
                assertEquals(0, pendingRank.getSubmissionInfo().get("YMOJ-10").getErrorNum());
                SubmissionStatusDTO request = new SubmissionStatusDTO();
                request.setExecutionId(current.getExecutionId());
                request.setStatus(8);
                request.setScore(45);
                service.updateSubmissionStatus(806L, request);
                assertEquals(45, judges.getById(806L).getScore());
                assertEquals(45, contests.getOIContestRank(new Page<>(1, 20), 98765L, null).getRecords().get(0).getTotalScore());
                request.setExecutionId(judges.getById(806L).getExecutionId());
                request.setStatus(0);
                request.setScore(null);
                service.updateSubmissionStatus(806L, request);
                assertEquals(100, judges.getById(806L).getScore());
                var acceptedRank = contests.getOIContestRank(new Page<>(1, 20), 98765L, null).getRecords().get(0);
                assertEquals(1, acceptedRank.getAc());
                assertEquals(100, acceptedRank.getTotalScore());
                jdbc.update("UPDATE contest SET mode=0 WHERE id=98765");
                service.rejudge(806L, judges.getById(806L).getExecutionId());
                var pendingAcm = contests.getAcmContestRank(new Page<>(1, 20), 98765L, null).getRecords().get(0);
                assertEquals(0, pendingAcm.getAc());
                assertEquals(0, pendingAcm.getTotalTime());
                assertEquals(0, pendingAcm.getSubmissionInfo().get("YMOJ-10").getErrorNum());
            }
            assertEquals(0, count("user_acproblem"));
        } finally {
            jdbc.update("DELETE FROM contest WHERE id=98765");
        }
    }

    /**
     * 为当前测试线程提供超级管理员身份
     * @return 由调用方关闭的安全上下文桩
     */
    private MockedStatic<SecurityUtils> superAdmin() {
        MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(1L);
        security.when(() -> SecurityUtils.isSuperAdmin(1L)).thenReturn(true);
        return security;
    }

    private JudgeDispatch formal(Long id) {
        String execution = UUID.randomUUID().toString();
        Judge judge = Judge.builder()
                .id(id)
                .problemId(10L)
                .testDataVersionId(77L)
                .executionId(execution)
                .problemTitle("Isolated test")
                .displayId("YMOJ-10")
                .userId(2L)
                .username("judge-test")
                .language("Python")
                .code("print(1)")
                .contestId(0L)
                .status(JudgeStatus.STATUS_PENDING)
                .build();
        assertTrue(judges.save(judge));
        JudgeTask task = new JudgeTask();
        task.setSubmissionId(id);
        task.setExecutionId(execution);
        task.setStrategy(0);
        task.setProblemId(10L);
        task.setTestDataVersionId(77L);
        task.setManifestSha256("ab".repeat(32));
        task.setDeadlineEpochMs(System.currentTimeMillis() + 1_200_000);
        JudgeDispatch dispatch = new JudgeDispatch();
        dispatch.setSubmissionId(id);
        dispatch.setExecutionId(execution);
        dispatch.setUserId(2L);
        dispatch.setStrategy(0);
        dispatch.setProblemId(10L);
        dispatch.setTestDataVersionId(77L);
        dispatch.setTaskPayload(codec.write(task));
        dispatch.setDispatchState(JudgeDispatchState.READY);
        dispatch.setStatus(JudgeStatus.STATUS_PENDING);
        dispatch.setLastResultSequence(0);
        dispatch.setPublishAttempts(0);
        dispatch.setRecoveryAttempts(0);
        dispatch.setNextAttemptTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20));
        assertTrue(dispatches.save(dispatch));
        return dispatch;
    }

    private JudgeResult accepted(JudgeDispatch dispatch) {
        JudgeResult result = new JudgeResult();
        result.setSubmissionId(dispatch.getSubmissionId());
        result.setExecutionId(dispatch.getExecutionId());
        result.setProblemId(dispatch.getProblemId());
        result.setTestDataVersionId(dispatch.getTestDataVersionId());
        result.setStrategy(dispatch.getStrategy());
        result.setTerminal(true);
        result.setSequence(3);
        result.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        result.setWorkerId("isolated-worker");
        result.setTime(10L);
        result.setMemory(128L);
        result.setTestCaseResults(List.of(
                TestCaseResult.builder()
                        .caseIndex(1)
                        .status(JudgeVerdict.STATUS_ACCEPTED)
                        .time(10L)
                        .memory(128L)
                        .build(),
                TestCaseResult.builder()
                        .caseIndex(2)
                        .status(JudgeVerdict.STATUS_ACCEPTED)
                        .time(10L)
                        .memory(128L)
                        .build()));
        return result;
    }

    private int count(String table) {
        if (!List.of("judge", "judge_dispatch", "judge_case", "user_acproblem").contains(table)) {
            throw new IllegalArgumentException("未知隔离测试表");
        }
        return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
    }

    private static Path rootDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("sql/ymcloud.sql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("找不到正式初始化 SQL");
        }
        return current;
    }

    private static void verifySchema() {
        if (schema == null || !schema.matches("ymcloud_judge_it_[0-9a-f]{32}")) {
            throw new IllegalStateException("临时数据库名称未通过安全校验");
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.ymcloud.core.web.mapper")
    @Import({JudgeDispatchEntityServiceImpl.class, JudgeEntityServiceImpl.class, JudgeCaseEntityServiceImpl.class,
            UserEntityServiceImpl.class, UserAcproblemEntityServiceImpl.class, ProblemEntityServiceImpl.class,
            LanguageEntityServiceImpl.class, TestDataVersionEntityServiceImpl.class, ContestEntityServiceImpl.class,
            ContestProblemEntityServiceImpl.class, ContestUserEntityServiceImpl.class, JudgeServiceImpl.class,
            AdminContestServiceImpl.class,
            JudgeResultService.class, JudgeMessageCodec.class, JudgeStatusCache.class, JudgeDispatchIdAllocator.class})
    static class DatabaseConfiguration {

        @Bean
        DataSource dataSource() {
            return prepared;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        /**
         * 装配隔离数据库访问及实际比赛排行 SQL
         * @param dataSource 隔离测试数据库
         * @return 使用项目枚举映射的会话工厂
         * @throws Exception 会话工厂初始化失败时向测试框架报告
         */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new ClassPathResource("mapper/ContestMapper.xml"),
                                       new ClassPathResource("mapper/ContestProblemMapper.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JudgeDispatchProperties properties() {
            return new JudgeDispatchProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RedisCache redis() {
            return mock(RedisCache.class);
        }

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        /**
         * 隔离比赛封面上传，不调用外部存储
         * @return 图片服务桩
         */
        @Bean
        PublicImageService publicImages() {
            return mock(PublicImageService.class);
        }

        /**
         * 隔离比赛封面引用校验
         * @return 文件服务桩
         */
        @Bean
        StoredFileService storedFiles() {
            return mock(StoredFileService.class);
        }

        @Bean
        TestDataService testData() {
            return mock(TestDataService.class);
        }
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param dispatches 任务箱实体服务
     * @param judges 提交实体服务
     * @param jdbc 测试数据库连接或隔离桩
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeDispatchIdAllocator createJudgeDispatchIdAllocator(
            JudgeDispatchEntityService dispatches,
            JudgeEntityService judges,
            JdbcTemplate jdbc) {
        JudgeDispatchIdAllocator component = new JudgeDispatchIdAllocator();
        ReflectionTestUtils.setField(component, "dispatches", dispatches);
        ReflectionTestUtils.setField(component, "judges", judges);
        ReflectionTestUtils.setField(component, "jdbc", jdbc);
        return component;
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param dispatches 任务箱实体服务
     * @param results 结果落库入口
     * @param rabbitTemplate 可控制确认和退回的消息模板
     * @param codec 严格判题消息编解码器
     * @param properties 本测试使用的容量、截止或发布配置
     * @param exchange 测试交换机名
     * @param routingKey 测试任务路由
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeDispatchPublisher createJudgeDispatchPublisher(
            JudgeDispatchEntityService dispatches,
            JudgeResultService results,
            RabbitTemplate rabbitTemplate,
            JudgeMessageCodec codec,
            JudgeDispatchProperties properties,
            String exchange,
            String routingKey) {
        JudgeDispatchPublisher component = new JudgeDispatchPublisher();
        ReflectionTestUtils.setField(component, "dispatches", dispatches);
        ReflectionTestUtils.setField(component, "results", results);
        ReflectionTestUtils.setField(component, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(component, "codec", codec);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "exchange", exchange);
        ReflectionTestUtils.setField(component, "routingKey", routingKey);
        return component;
    }
}

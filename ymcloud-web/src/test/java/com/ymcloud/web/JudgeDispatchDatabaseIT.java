package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
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
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchPublisher;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.oj.JudgeService;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
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
import org.springframework.core.io.support.EncodedResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
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

/** 显式启用且仅连接本地 MySQL，在随机临时库验证真实事务、并发及任务恢复。 */
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
                + "(id,problem_id,title,source,mode,time_limit,memory_limit,visibility,author_id) "
                + "VALUES (10,'YMOJ-10','Isolated test','myy',0,1000,262144,1,2)");
        jdbc.update("INSERT INTO test_data_version (id,problem_id,manifest,manifest_sha256,created_time) "
                        + "VALUES (77,10,'{}',?,CURRENT_TIMESTAMP)",
                "ab".repeat(32));
        TestDataService testData = context.getBean(TestDataService.class);
        reset(testData, context.getBean(RedisCache.class));
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
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
        JudgeDispatchIdAllocator allocator = new JudgeDispatchIdAllocator(dispatches, judges, jdbc);
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
            JudgeDispatchIdAllocator allocator = new JudgeDispatchIdAllocator(dispatches, judges, jdbc);
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
        JudgeDispatchPublisher publisher = new JudgeDispatchPublisher(dispatches, results, rabbit, codec,
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
        JudgeDispatchIdAllocator allocator = new JudgeDispatchIdAllocator(dispatches, judges, jdbc);
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

    @Test
    void stageOneUpgradePreservesHistoryAndSeedsTheSharedSubmissionSequence() throws Exception {
        long legacy = 765_432L;
        JudgeDispatch dispatch = formal(legacy);
        results.receive(accepted(dispatch));
        Map<String, Object> history = jdbc.queryForMap("SELECT id,problem_id,test_data_version_id,status,time,memory,"
                + "score,code,created_time,updated_time FROM judge WHERE id=?", legacy);
        Map<String, Object> version = jdbc.queryForMap("SELECT * FROM test_data_version WHERE id=77");
        List<Map<String, Object>> historicalCases = jdbc.queryForList(
                "SELECT * FROM judge_case WHERE judge_id=? ORDER BY id", legacy);
        List<Map<String, Object>> historicalAccepted = jdbc.queryForList("SELECT * FROM user_acproblem ORDER BY id");

        jdbc.execute("DROP TABLE judge_dispatch");
        jdbc.execute("ALTER TABLE judge DROP COLUMN execution_id");
        byte[] upgrade = Files.readString(rootDirectory().resolve("sql/upgrade-worker.sql"), StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8);
        try (Connection connection = prepared.getConnection()) {
            assertEquals(schema, connection.getCatalog());
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(upgrade), StandardCharsets.UTF_8));
        }

        assertEquals(history, jdbc.queryForMap("SELECT id,problem_id,test_data_version_id,status,time,memory,"
                + "score,code,created_time,updated_time FROM judge WHERE id=?", legacy));
        assertEquals(version, jdbc.queryForMap("SELECT * FROM test_data_version WHERE id=77"));
        assertEquals(historicalCases,
                jdbc.queryForList("SELECT * FROM judge_case WHERE judge_id=? ORDER BY id", legacy));
        assertEquals(historicalAccepted, jdbc.queryForList("SELECT * FROM user_acproblem ORDER BY id"));
        assertNull(judges.getById(legacy).getExecutionId());
        assertEquals(0, count("judge_dispatch"));
        JudgeDispatchIdAllocator allocator = new JudgeDispatchIdAllocator(dispatches, judges, jdbc);
        assertDoesNotThrow(allocator::verifyCounter);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(context.getBean(UserEntityService.class).getById(2L));
            TestJudgeDTO request = new TestJudgeDTO();
            request.setLanguage("Python");
            request.setCode("print(input())");
            Long id = service.testJudge(request);
            assertEquals(legacy + 1, id);
            assertEquals(id, dispatches.getById(id).getSubmissionId());
        }
        assertEquals(1, count("judge"));
        assertEquals(JudgeStatus.STATUS_ACCEPTED, judges.getById(legacy).getStatus());
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

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
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

        @Bean
        TestDataService testData() {
            return mock(TestDataService.class);
        }
    }
}

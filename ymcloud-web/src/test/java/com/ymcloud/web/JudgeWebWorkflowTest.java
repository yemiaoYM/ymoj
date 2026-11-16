package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.enums.RoleType;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import com.ymcloud.web.service.problem.TestDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 验证本站提交、版本快照、比赛模式、自测持久化及状态恢复。
 */
class JudgeWebWorkflowTest {

    JudgeEntityService judges;
    JudgeDispatchEntityService dispatches;
    ProblemEntityService problems;
    LanguageEntityService languages;
    ContestEntityService contests;
    ContestProblemEntityService contestProblems;
    ContestUserEntityService contestUsers;
    TestDataService testData;
    TestDataVersionEntityService versions;
    JudgeCaseEntityService cases;
    UserEntityService users;
    UserAcproblemEntityService accepted;
    RedisCache redis;
    JudgeDispatchProperties properties;
    JudgeMessageCodec codec;
    JudgeStatusCache cache;
    JudgeResultService results;
    JudgeServiceImpl service;

    /**
     * 装配判题服务及可控制的持久化依赖
     */
    @BeforeEach
    void prepare() {
        judges = mock(JudgeEntityService.class);
        dispatches = mock(JudgeDispatchEntityService.class);
        problems = mock(ProblemEntityService.class);
        languages = mock(LanguageEntityService.class);
        contests = mock(ContestEntityService.class);
        contestProblems = mock(ContestProblemEntityService.class);
        contestUsers = mock(ContestUserEntityService.class);
        testData = mock(TestDataService.class);
        versions = mock(TestDataVersionEntityService.class);
        cases = mock(JudgeCaseEntityService.class);
        users = mock(UserEntityService.class);
        accepted = mock(UserAcproblemEntityService.class);
        redis = mock(RedisCache.class);
        properties = new JudgeDispatchProperties();
        codec = new JudgeMessageCodec(new ObjectMapper());
        cache = createJudgeStatusCache(redis);
        results = createJudgeResultService(dispatches, judges, cases, users, accepted, codec, cache, properties);
        AtomicLong sequence = new AtomicLong(100L);
        when(dispatches.save(any())).thenAnswer(invocation -> {
            JudgeDispatch dispatch = invocation.getArgument(0);
            dispatch.setId(sequence.getAndIncrement());
            return true;
        });
        doReturn(update()).when(dispatches).lambdaUpdate();
        service = createJudgeServiceImpl(judges, dispatches, problems, languages, contests, contestProblems,
                contestUsers, testData, versions, properties, codec, cache, results,
                createJudgeDispatchIdAllocator(dispatches, judges, mock(JdbcTemplate.class)));
    }

    /**
     * 保存任务时固定完整 SPJ 版本，题目后续编辑不会改变已入队内容。
     */
    @Test
    void spjSubmissionPinsCheckerSource() {
        stubProblemAndLanguage();
        Problem problem = problem();
        problem.setJudgeMode("spj");
        SpjProgram spj = new SpjProgram();
        spj.setLanguage("C++");
        spj.setCode("#include \"testlib.h\"");
        spj.setDigest(spj.sourceDigest());
        problem.setSpjLanguage(spj.getLanguage());
        problem.setSpjCode(spj.getCode());
        problem.setSpjDigest(spj.getDigest());
        doReturn(query(problem)).when(problems).lambdaQuery();
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
        doReturn(query(version())).when(versions).lambdaQuery();
        when(judges.save(any())).thenReturn(true);
        try (var security = authenticatedUser(); var ip = mockStatic(IpUtils.class)) {
            service.submitJudge(request(false));
        }
        ArgumentCaptor<JudgeDispatch> dispatch = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(dispatch.capture());
        problem.setSpjCode("changed after enqueue");
        JudgeTask task = codec.task(dispatch.getValue().getTaskPayload());
        assertEquals(spj, task.getSpj());
        assertEquals(77L, task.getTestDataVersionId());
    }

    @Test
    void normalSubmissionPinsRecordAndDurableTaskBeforeVersionChanges() {
        stubProblemAndLanguage();
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
        doReturn(query(version())).when(versions).lambdaQuery();
        when(judges.save(any())).thenAnswer(invocation -> {
            when(testData.getCurrentVersion(10L)).thenReturn(88L);
            return true;
        });
        try (MockedStatic<SecurityUtils> security = authenticatedUser();
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            Long id = service.submitJudge(request(false));
            ArgumentCaptor<Judge> record = ArgumentCaptor.forClass(Judge.class);
            ArgumentCaptor<JudgeDispatch> dispatch = ArgumentCaptor.forClass(JudgeDispatch.class);
            verify(judges).save(record.capture());
            verify(dispatches).save(dispatch.capture());
            JudgeTask task = codec.task(dispatch.getValue().getTaskPayload());
            assertEquals(id, record.getValue().getId());
            assertEquals(id, task.getSubmissionId());
            assertEquals(JudgeConstants.ACM, task.getMode());
            assertEquals(77L, record.getValue().getTestDataVersionId());
            assertEquals(77L, task.getTestDataVersionId());
            assertEquals(version().getManifestSha256(), task.getManifestSha256());
            assertEquals(record.getValue().getExecutionId(), task.getExecutionId());
            assertEquals(2L, dispatch.getValue().getUserId());
            assertEquals(JudgeDispatchState.READY, dispatch.getValue().getDispatchState());
            assertTrue(task.getDeadlineEpochMs() > task.getSubmittedAtEpochMs());
        }
        verify(testData, times(1)).getCurrentVersion(10L);
    }

    /**
     * 外站来源仅作出处，普通提交和比赛提交都必须选择本站启用的语言。
     * @param contestSubmission 是否从比赛入口提交
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void externalProblemSourceStillUsesLocalJudge(boolean contestSubmission) {
        Problem problem = problem();
        problem.setSource("此题来源于 Codeforces 1A");
        doReturn(query(problem)).when(problems).lambdaQuery();
        LambdaQueryChainWrapper<Language> languageQuery = query(language());
        doReturn(languageQuery).when(languages).lambdaQuery();
        if (contestSubmission) {
            Contest contest = new Contest();
            contest.setId(20L);
            contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
            contest.setStartTime(LocalDateTime.now().minusMinutes(1));
            contest.setEndTime(LocalDateTime.now().plusMinutes(1));
            contest.setMode(JudgeConstants.ACM);
            ContestProblem mapping = new ContestProblem();
            mapping.setId(30L);
            mapping.setProblemId(problem.getId());
            mapping.setDisplayId("A");
            doReturn(query(contest)).when(contests).lambdaQuery();
            doReturn(query(mapping)).when(contestProblems).lambdaQuery();
        }
        when(testData.getCurrentVersion(problem.getId())).thenReturn(77L);
        doReturn(query(version())).when(versions).lambdaQuery();
        when(judges.save(any())).thenReturn(true);
        try (MockedStatic<SecurityUtils> security = authenticatedUser();
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            service.submitJudge(request(contestSubmission));
        }
        verify(languageQuery).eq(any(), eq(LanguageConstants.LOCAL_PLATFORM));
        verify(languageQuery, never()).eq(any(), eq(problem.getSource()));
        ArgumentCaptor<JudgeDispatch> created = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(created.capture());
        JudgeTask task = codec.task(created.getValue().getTaskPayload());
        assertEquals(JudgeDispatchState.FORMAL, task.getStrategy());
        assertEquals(problem.getId(), task.getProblemId());
        assertEquals(77L, task.getTestDataVersionId());
    }

    /**
     * ACM 使用全对判定，OI 与 IOI 使用相同的测试点评分，并保留比赛归属检查。
     * @param mode 比赛模式：0=ACM，1=OI，2=IOI
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void contestTaskUsesContestModeAndMembershipChecks(int mode) {
        stubProblemAndLanguage();
        Contest contest = new Contest();
        contest.setId(20L);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusMinutes(1));
        contest.setEndTime(LocalDateTime.now().plusMinutes(1));
        contest.setMode(mode);
        ContestProblem contestProblem = new ContestProblem();
        contestProblem.setId(30L);
        contestProblem.setProblemId(10L);
        contestProblem.setDisplayId("A");
        contestProblem.setDisplayTitle("Contest problem");
        doReturn(query(contest)).when(contests).lambdaQuery();
        doReturn(query(contestProblem)).when(contestProblems).lambdaQuery();
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
        doReturn(query(version())).when(versions).lambdaQuery();
        when(judges.save(any())).thenReturn(true);
        try (MockedStatic<SecurityUtils> security = authenticatedUser();
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            service.submitJudge(request(true));
        }
        ArgumentCaptor<JudgeDispatch> dispatch = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(dispatch.capture());
        assertEquals(mode == ContestConstants.MODE_ACM ? JudgeConstants.ACM : JudgeConstants.OI,
                codec.task(dispatch.getValue().getTaskPayload()).getMode());
        verifyNoInteractions(contestUsers);
    }

    /**
     * 独立校验判题方式，拒绝尚未实现的模式，不能降级后发出普通任务。
     * @param judgeMode 不支持或缺失的判题方式
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"interactive", "unknown"})
    void unsupportedJudgeModeDoesNotEnqueue(String judgeMode) {
        Problem problem = problem();
        problem.setJudgeMode(judgeMode);
        doReturn(query(problem)).when(problems).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            assertThrows(JudgeException.class, () -> service.submitJudge(request(false)));
        }
        verifyNoInteractions(testData, judges, dispatches, languages);
    }

    @Test
    void missingVersionRejectsBeforeAnySubmissionTaskOrCacheWrite() {
        stubProblemAndLanguage();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            assertThrows(JudgeException.class, () -> service.submitJudge(request(false)));
        }
        verifyNoInteractions(judges, dispatches, redis, versions);
    }

    @Test
    void privateProblemStillRequiresAuthorBeforeVersionReads() {
        Problem problem = problem();
        problem.setVisibility(ProblemConstants.PRIVATE);
        problem.setAuthorId(3L);
        doReturn(query(problem)).when(problems).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            assertThrows(ProblemPermissionException.class, () -> service.submitJudge(request(false)));
        }
        verifyNoInteractions(testData, judges, dispatches);
    }

    @Test
    void selfTestPreservesWhitespaceAndStoresOwnerWithoutProblemOrVersion() {
        doReturn(query(language())).when(languages).lambdaQuery();
        TestJudgeDTO request = new TestJudgeDTO();
        request.setCode("print(input())");
        request.setLanguage("Python");
        request.setInput("  text\n\n");
        request.setExpectedOutput("  text\n");
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            service.testJudge(request);
            service.testJudge(request);
        }
        ArgumentCaptor<JudgeDispatch> dispatchesCreated = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches, times(2)).save(dispatchesCreated.capture());
        JudgeDispatch first = dispatchesCreated.getAllValues().get(0);
        JudgeDispatch second = dispatchesCreated.getAllValues().get(1);
        JudgeTask task = codec.task(first.getTaskPayload());
        assertEquals(request.getInput(), task.getInput());
        assertNull(task.getProblemId());
        assertNull(task.getTestDataVersionId());
        assertEquals(2L, first.getUserId());
        assertNotEquals(first.getSubmissionId(), second.getSubmissionId());
        assertNotEquals(first.getExecutionId(), second.getExecutionId());
        verifyNoInteractions(testData, judges, versions);
    }

    /**
     * 正常提交与结果接收在事务提交前后都不产生流水日志
     */
    @Test
    void normalSubmissionAndResultDoNotProduceLifecycleLogs() {
        doReturn(query(language())).when(languages).lambdaQuery();
        Logger submissions = (Logger) LoggerFactory.getLogger(JudgeServiceImpl.class);
        Logger received = (Logger) LoggerFactory.getLogger(JudgeResultService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        submissions.addAppender(appender);
        received.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            TestJudgeDTO request = new TestJudgeDTO();
            request.setLanguage("Python");
            request.setCode("print('private-code-sentinel')");
            request.setInput("private-input-sentinel");
            request.setExpectedOutput("private-expected-sentinel");
            service.testJudge(request);
            JudgeDispatch dispatch = selfDispatch();
            doReturn(query(dispatch)).when(dispatches).lambdaQuery();
            JudgeResult result = finalResult(dispatch);
            result.setErrorMessage("private-error-sentinel");
            result.getTestCaseResults().get(0).setOutput("private-output-sentinel");
            results.receive(result);
            assertTrue(appender.list.isEmpty());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertTrue(appender.list.isEmpty());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            submissions.detachAppender(appender);
            received.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 自测缺省输入按空文本处理，未支持的语言不得创建任务
     */
    @Test
    void absentSelfTestInputIsEmptyAndUnsupportedLanguageIsRejected() {
        Language language = language();
        language.setEnv(null);
        language.setRunCmd(null);
        doReturn(query(language)).when(languages).lambdaQuery();
        TestJudgeDTO request = new TestJudgeDTO();
        request.setCode("print(input())");
        request.setLanguage("Python");
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            service.testJudge(request);
            ArgumentCaptor<JudgeDispatch> created = ArgumentCaptor.forClass(JudgeDispatch.class);
            verify(dispatches).save(created.capture());
            JudgeTask task = codec.task(created.getValue().getTaskPayload());
            assertEquals("", task.getInput());
            assertEquals("", task.getExpectedOutput());
            language.setName("Go");
            assertThrows(JudgeException.class, () -> service.testJudge(request));
            verify(dispatches, times(1)).save(any());
        }
    }

    /**
     * 任务只保存固定语言入口，不携带语言表中的命令及环境配置
     * @param name Worker 支持的语言
     * @throws Exception 消息 JSON 解析失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void languageSnapshotExcludesUnusedExecutionMetadata(String name) throws Exception {
        Language language = language();
        language.setName(name);
        language.setType(2);
        language.setFileName("unused-entry.txt");
        language.setImage("unused-toolchain");
        language.setEnv("unused-environment");
        language.setCompileCmd("unused-compile-command");
        language.setRunCmd("unused-run-command");
        language.setVersion("unused-version");
        doReturn(query(problem())).when(problems).lambdaQuery();
        doReturn(query(language)).when(languages).lambdaQuery();
        doReturn(query(version())).when(versions).lambdaQuery();
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
        when(judges.save(any())).thenReturn(true);
        JudgeDTO request = request(false);
        request.setLanguage(name);

        try (MockedStatic<SecurityUtils> security = authenticatedUser();
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            service.submitJudge(request);
        }
        ArgumentCaptor<JudgeDispatch> created = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(created.capture());
        String payload = created.getValue().getTaskPayload();
        language.setName("changed-language");
        JudgeTask snapshot = codec.task(payload);
        assertEquals(name, snapshot.getLanguageConfig().getName());
        assertEquals(77L, snapshot.getTestDataVersionId());
        var languageJson = new ObjectMapper().readTree(payload).get("languageConfig");
        assertEquals(1, languageJson.size());
        assertEquals(name, languageJson.get("name").asText());
        verify(languages, never()).lambdaUpdate();
    }

    @Test
    void invalidProblemResourcesAndDispatchTimeoutConfigurationFailEarly() {
        Problem problem = problem();
        problem.setMemoryLimit(2 * 1024 * 1024L + 1);
        doReturn(query(problem)).when(problems).lambdaQuery();
        doReturn(query(language())).when(languages).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            assertThrows(JudgeException.class, () -> service.submitJudge(request(false)));
        }
        verifyNoInteractions(testData, judges, dispatches);
        properties.setTotalTimeout(java.time.Duration.ofHours(2));
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void cacheFailureDoesNotLoseSelfTestAndOtherUserCannotPoll() {
        JudgeDispatch dispatch = selfDispatch();
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        when(dispatches.getById(dispatch.getId())).thenReturn(dispatch);
        when(redis.getCacheObject(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            assertEquals("  raw\n", service.getStatus(dispatch.getSubmissionId()).getInput());
            security.when(SecurityUtils::getUserId).thenReturn(3L);
            assertThrows(JudgeException.class, () -> service.getStatus(dispatch.getSubmissionId()));
        }
    }

    @Test
    void duplicateFinalAndOutOfOrderProgressNeverWriteAgain() {
        JudgeDispatch dispatch = selfDispatch();
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        LambdaUpdateChainWrapper<JudgeDispatch> update = update();
        doReturn(update).when(dispatches).lambdaUpdate();
        JudgeResult result = finalResult(dispatch);
        results.receive(result);
        results.receive(result);
        JudgeResult earlier = finalResult(dispatch);
        earlier.setTerminal(false);
        earlier.setSequence(1);
        earlier.setStatus(JudgeVerdict.STATUS_COMPILING);
        earlier.setTestCaseResults(null);
        results.receive(earlier);
        verify(update, times(1)).update();
        assertEquals(JudgeStatus.STATUS_ACCEPTED, dispatch.getStatus());
        verifyNoInteractions(judges, cases, accepted);
    }

    @Test
    void staleExecutionOrVersionAndDuplicateCaseIndicesAreRejected() {
        JudgeDispatch dispatch = selfDispatch();
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        JudgeResult result = finalResult(dispatch);
        result.setExecutionId(UUID.randomUUID().toString());
        results.receive(result);
        verify(dispatches, never()).lambdaUpdate();
        result = finalResult(dispatch);
        result.setTestDataVersionId(99L);
        results.receive(result);
        verify(dispatches, never()).lambdaUpdate();
        result = finalResult(dispatch);
        result.setTestCaseResults(List.of(result.getTestCaseResults().get(0), result.getTestCaseResults().get(0)));
        JudgeResult invalid = result;
        assertThrows(IllegalArgumentException.class, () -> results.receive(invalid));
    }

    @Test
    void expiredTaskStaysSystemErrorEvenIfAcceptedResultArrivesLater() {
        JudgeDispatch dispatch = selfDispatch();
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        doReturn(update()).when(dispatches).lambdaUpdate();
        results.expire(dispatch.getSubmissionId(), dispatch.getExecutionId());
        results.receive(finalResult(dispatch));
        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, dispatch.getStatus());
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, codec.result(dispatch.getResultPayload()).getStatus());
    }

    @Test
    void completedResultRecoveredAfterDeadlineIsKeptWhenTimeoutHasNotBeenSaved() {
        JudgeDispatch dispatch = selfDispatch();
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        JudgeResult recovered = finalResult(dispatch);
        recovered.setEmittedAtEpochMs(System.currentTimeMillis() - 10_000);
        results.receive(recovered);
        results.expire(dispatch.getSubmissionId(), dispatch.getExecutionId());
        assertEquals(JudgeStatus.STATUS_ACCEPTED, dispatch.getStatus());
        assertEquals(JudgeDispatchState.COMPLETED, dispatch.getDispatchState());
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, codec.result(dispatch.getResultPayload()).getStatus());
    }

    @Test
    void malformedSelfTestPayloadCanStillBecomeAStableSystemError() {
        JudgeDispatch dispatch = selfDispatch();
        dispatch.setTaskPayload("invalid json");
        doReturn(query(dispatch)).when(dispatches).lambdaQuery();
        doReturn(update()).when(dispatches).lambdaUpdate();
        results.failPublishing(dispatch.getSubmissionId(), dispatch.getExecutionId(), "任务快照无效");
        assertEquals(JudgeDispatchState.COMPLETED, dispatch.getDispatchState());
        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, dispatch.getStatus());
        assertEquals("任务快照无效", results.toView(dispatch).getErrorMessage());
        assertNull(results.toView(dispatch).getInput());
    }

    @Test
    void nullSelfTestPayloadAndResultUseDurableStatusWithoutThrowing() {
        JudgeDispatch dispatch = selfDispatch();
        dispatch.setTaskPayload("null");
        dispatch.setResultPayload("null");
        dispatch.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, results.toView(dispatch).getStatus());
        assertNull(results.toView(dispatch).getInput());
        assertNull(results.toView(dispatch).getOutput());
    }

    @Test
    void invalidCaseScoreNeverTouchesPersistentState() {
        JudgeDispatch dispatch = selfDispatch();
        JudgeResult result = finalResult(dispatch);
        result.getTestCaseResults().get(0).setScore(101);
        assertThrows(IllegalArgumentException.class, () -> results.receive(result));
        verifyNoInteractions(dispatches, judges, cases, accepted);
        result.getTestCaseResults().get(0).setScore(-1);
        assertThrows(IllegalArgumentException.class, () -> results.receive(result));
        verifyNoInteractions(dispatches, judges, cases, accepted);
    }

    /**
     * 终态必须具有对应序号与判定，非法结果在数据库操作前拒绝
     * @param sequence 不属于终态的消息序号
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void invalidTerminalSequenceNeverTouchesPersistentState(int sequence) {
        JudgeResult result = finalResult(selfDispatch());
        result.setSequence(sequence);
        assertThrows(IllegalArgumentException.class, () -> results.receive(result));
        result.setSequence(3);
        result.setStatus(null);
        assertThrows(IllegalArgumentException.class, () -> results.receive(result));
        verifyNoInteractions(dispatches, judges, cases, accepted);
    }

    @Test
    void corruptOrMismatchedDurableResultNeverLeaksAnotherExecutionOutput() {
        JudgeDispatch dispatch = selfDispatch();
        JudgeResult wrongExecution = finalResult(dispatch);
        wrongExecution.setExecutionId(UUID.randomUUID().toString());
        wrongExecution.getTestCaseResults().get(0).setOutput("other-execution-private-output");
        dispatch.setResultPayload(codec.write(wrongExecution));
        assertNull(results.toView(dispatch).getOutput());
        JudgeResult corrupt = finalResult(dispatch);
        corrupt.setTestCaseResults(java.util.Arrays.asList((TestCaseResult) null));
        dispatch.setResultPayload(codec.write(corrupt));
        assertDoesNotThrow(() -> results.toView(dispatch));
        assertNull(results.toView(dispatch).getOutput());
        JudgeTask wrongTask = codec.task(dispatch.getTaskPayload());
        wrongTask.setExecutionId(UUID.randomUUID().toString());
        wrongTask.setInput("other-execution-private-input");
        dispatch.setTaskPayload(codec.write(wrongTask));
        assertNull(results.toView(dispatch).getInput());
    }

    /**
     * 人工改判移除评测任务后仍可读取持久状态，且继续校验查看权限
     */
    @Test
    void manuallyJudgedStatusRemainsAvailableWithoutAWorkerDispatch() {
        doReturn(query(null)).when(dispatches).lambdaQuery();
        Judge submission = new Judge();
        submission.setUserId(2L);
        submission.setContestId(0L);
        submission.setProblemId(10L);
        submission.setIsManual(1);
        when(problems.getById(10L)).thenReturn(problem());
        submission.setStatus(JudgeStatus.STATUS_ACCEPTED);
        submission.setTime(10L);
        doReturn(query(submission)).when(judges).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = authenticatedUser()) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            assertEquals(JudgeStatus.STATUS_ACCEPTED, service.getStatus(1L).getStatus());
            assertEquals(10L, service.getStatus(1L).getTime());
            security.when(SecurityUtils::getUserId).thenReturn(3L);
            assertThrows(JudgeException.class, () -> service.getStatus(1L));
        }
        verify(judges, never()).getById(any());
        verifyNoInteractions(redis);
    }

    private void stubProblemAndLanguage() {
        doReturn(query(problem())).when(problems).lambdaQuery();
        doReturn(query(language())).when(languages).lambdaQuery();
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setId(10L);
        problem.setProblemId("YMOJ-10");
        problem.setTitle("Versioned problem");
        problem.setVisibility(ProblemConstants.PUBLIC);
        problem.setSource(null);
        problem.setJudgeMode(ProblemConstants.JUDGE_MODE_DEFAULT);
        problem.setTimeLimit(1000L);
        problem.setMemoryLimit(262144L);
        return problem;
    }

    private Language language() {
        Language language = new Language();
        language.setName("Python");
        language.setEnv("oj-python-env");
        language.setType(1);
        language.setFileName("main.py");
        language.setRunCmd("python3 main.py");
        return language;
    }

    private TestDataVersion version() {
        TestDataVersion version = new TestDataVersion();
        version.setId(77L);
        version.setProblemId(10L);
        version.setManifestSha256("ab".repeat(32));
        return version;
    }

    /**
     * 为判题请求提供已登录的普通用户身份
     * @return 由调用方关闭的安全工具桩
     */
    private MockedStatic<SecurityUtils> authenticatedUser() {
        MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
        User currentUser = user(2L);
        security.when(SecurityUtils::getUser).thenReturn(currentUser);
        security.when(SecurityUtils::getUserId).thenReturn(currentUser.getId());
        security.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(currentUser, List.of(RoleType.USER.getCode()), List.of()));
        return security;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("judge-test-" + id);
        return user;
    }

    private JudgeDTO request(boolean contest) {
        JudgeDTO request = new JudgeDTO();
        request.setIsContest(contest);
        request.setContestId(20L);
        request.setProblemId(contest ? "A" : "YMOJ-10");
        request.setLanguage("Python");
        request.setCode("print(input())");
        return request;
    }

    private JudgeDispatch selfDispatch() {
        JudgeTask task = new JudgeTask();
        task.setSubmissionId(100L);
        task.setExecutionId(UUID.randomUUID().toString());
        task.setStrategy(1);
        task.setInput("  raw\n");
        JudgeDispatch dispatch = new JudgeDispatch();
        dispatch.setId(1L);
        dispatch.setSubmissionId(task.getSubmissionId());
        dispatch.setExecutionId(task.getExecutionId());
        dispatch.setUserId(2L);
        dispatch.setStrategy(1);
        dispatch.setTaskPayload(codec.write(task));
        dispatch.setDispatchState(0);
        dispatch.setStatus(JudgeStatus.STATUS_PENDING);
        dispatch.setLastResultSequence(0);
        dispatch.setNextAttemptTime(LocalDateTime.now(ZoneOffset.UTC));
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        return dispatch;
    }

    private JudgeResult finalResult(JudgeDispatch dispatch) {
        JudgeResult result = new JudgeResult();
        result.setSubmissionId(dispatch.getSubmissionId());
        result.setExecutionId(dispatch.getExecutionId());
        result.setStrategy(1);
        result.setTerminal(true);
        result.setSequence(3);
        result.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        result.setTestCaseResults(List.of(TestCaseResult.builder()
                .caseIndex(1)
                .status(JudgeVerdict.STATUS_ACCEPTED)
                .output("  raw\n")
                .build()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> LambdaQueryChainWrapper<T> query(T value) {
        LambdaQueryChainWrapper<T> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.select(any(SFunction[].class))).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last(anyString())).thenReturn(query);
        when(query.one()).thenReturn(value);
        return query;
    }

    @SuppressWarnings("unchecked")
    private <T> LambdaUpdateChainWrapper<T> update() {
        LambdaUpdateChainWrapper<T> update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.ne(any(), any())).thenReturn(update);
        when(update.lt(any(), any())).thenReturn(update);
        when(update.isNull(any())).thenReturn(update);
        when(update.set(any(), nullable(Object.class))).thenReturn(update);
        when(update.update()).thenReturn(true);
        return update;
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
     * @param judges 提交实体服务
     * @param cases 测试点实体服务
     * @param users 用户实体服务
     * @param accepted 用户 AC 记录实体服务
     * @param codec 严格判题消息编解码器
     * @param cache 本测试的版本或状态缓存
     * @param properties 本测试使用的容量、截止或发布配置
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeResultService createJudgeResultService(
            JudgeDispatchEntityService dispatches,
            JudgeEntityService judges,
            JudgeCaseEntityService cases,
            UserEntityService users,
            UserAcproblemEntityService accepted,
            JudgeMessageCodec codec,
            JudgeStatusCache cache,
            JudgeDispatchProperties properties) {
        JudgeResultService component = new JudgeResultService();
        ReflectionTestUtils.setField(component, "dispatches", dispatches);
        ReflectionTestUtils.setField(component, "judges", judges);
        ReflectionTestUtils.setField(component, "cases", cases);
        ReflectionTestUtils.setField(component, "users", users);
        ReflectionTestUtils.setField(component, "accepted", accepted);
        ReflectionTestUtils.setField(component, "codec", codec);
        ReflectionTestUtils.setField(component, "cache", cache);
        ReflectionTestUtils.setField(component, "properties", properties);
        return component;
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param judges 提交实体服务
     * @param dispatches 任务箱实体服务
     * @param problems 题目实体服务
     * @param languages 语言实体服务
     * @param contests 比赛实体服务
     * @param contestProblems 比赛题目实体服务
     * @param contestUsers 参赛记录实体服务
     * @param testData 固定测试版本访问服务
     * @param versions 测试版本实体服务
     * @param properties 本测试使用的容量、截止或发布配置
     * @param codec 严格判题消息编解码器
     * @param cache 本测试的版本或状态缓存
     * @param results 结果落库入口
     * @param allocator 提交序号分配器
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeServiceImpl createJudgeServiceImpl(
            JudgeEntityService judges,
            JudgeDispatchEntityService dispatches,
            ProblemEntityService problems,
            LanguageEntityService languages,
            ContestEntityService contests,
            ContestProblemEntityService contestProblems,
            ContestUserEntityService contestUsers,
            TestDataService testData,
            TestDataVersionEntityService versions,
            JudgeDispatchProperties properties,
            JudgeMessageCodec codec,
            JudgeStatusCache cache,
            JudgeResultService results,
            JudgeDispatchIdAllocator allocator) {
        JudgeServiceImpl component = new JudgeServiceImpl();
        ResourceAccessService access = new ResourceAccessService();
        ReflectionTestUtils.setField(access, "problems", problems);
        ReflectionTestUtils.setField(access, "contests", contests);
        ReflectionTestUtils.setField(access, "contestUsers", contestUsers);
        ReflectionTestUtils.setField(component, "resourceAccess", access);
        ReflectionTestUtils.setField(component, "judges", judges);
        ReflectionTestUtils.setField(component, "dispatches", dispatches);
        ReflectionTestUtils.setField(component, "problems", problems);
        ReflectionTestUtils.setField(component, "languages", languages);
        ReflectionTestUtils.setField(component, "contests", contests);
        ReflectionTestUtils.setField(component, "contestProblems", contestProblems);
        ReflectionTestUtils.setField(component, "testData", testData);
        ReflectionTestUtils.setField(component, "versions", versions);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "codec", codec);
        ReflectionTestUtils.setField(component, "cache", cache);
        ReflectionTestUtils.setField(component, "results", results);
        ReflectionTestUtils.setField(component, "allocator", allocator);
        return component;
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param redisCache 隔离的缓存边界
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeStatusCache createJudgeStatusCache(RedisCache redisCache) {
        JudgeStatusCache component = new JudgeStatusCache();
        ReflectionTestUtils.setField(component, "redisCache", redisCache);
        return component;
    }
}

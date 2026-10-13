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
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
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
import com.ymcloud.pojo.enums.ProblemSource;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** 提交版本快照、比赛模式、自测持久化、状态所属用户与缓存故障回退。 */
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
        cache = new JudgeStatusCache(redis);
        results = new JudgeResultService(dispatches, judges, cases, users, accepted, codec, cache, properties);
        AtomicLong sequence = new AtomicLong(100L);
        when(dispatches.save(any())).thenAnswer(invocation -> {
            JudgeDispatch dispatch = invocation.getArgument(0);
            dispatch.setId(sequence.getAndIncrement());
            return true;
        });
        doReturn(update()).when(dispatches).lambdaUpdate();
        service = new JudgeServiceImpl(judges, dispatches, problems, languages, contests, contestProblems,
                contestUsers, testData, versions, properties, codec, cache, results,
                new JudgeDispatchIdAllocator(dispatches, judges, mock(JdbcTemplate.class)));
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
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            Long id = service.submitJudge(request(false));
            ArgumentCaptor<Judge> record = ArgumentCaptor.forClass(Judge.class);
            ArgumentCaptor<JudgeDispatch> dispatch = ArgumentCaptor.forClass(JudgeDispatch.class);
            verify(judges).save(record.capture());
            verify(dispatches).save(dispatch.capture());
            JudgeTask task = codec.task(dispatch.getValue().getTaskPayload());
            assertEquals(id, record.getValue().getId());
            assertEquals(id, task.getSubmissionId());
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

    @Test
    void contestTaskUsesContestModeAndMembershipChecks() {
        stubProblemAndLanguage();
        Contest contest = new Contest();
        contest.setId(20L);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusMinutes(1));
        contest.setEndTime(LocalDateTime.now().plusMinutes(1));
        contest.setMode(JudgeConstants.OI);
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
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
            service.submitJudge(request(true));
        }
        ArgumentCaptor<JudgeDispatch> dispatch = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(dispatch.capture());
        assertEquals(JudgeConstants.OI, codec.task(dispatch.getValue().getTaskPayload()).getMode());
        verifyNoInteractions(contestUsers);
    }

    @Test
    void missingVersionRejectsBeforeAnySubmissionTaskOrCacheWrite() {
        stubProblemAndLanguage();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
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
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
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
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
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

    @Test
    void lifecycleSuccessLogsWaitForCommitAndNeverIncludePrivateBodies() {
        doReturn(query(language())).when(languages).lambdaQuery();
        Logger submissions = (Logger) LoggerFactory.getLogger(JudgeServiceImpl.class);
        Logger received = (Logger) LoggerFactory.getLogger(JudgeResultService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        submissions.addAppender(appender);
        received.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
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
            assertTrue(appender.list.isEmpty(), "提交成功和终态成功只能在数据库提交后记录");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertEquals(2, appender.list.size());
            String diagnostic = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (first, second) -> first + "\n" + second);
            assertTrue(diagnostic.contains("创建判题任务成功"));
            assertTrue(diagnostic.contains("保存判题结果成功"));
            assertTrue(diagnostic.contains(dispatch.getExecutionId()));
            assertFalse(diagnostic.contains("private-"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            submissions.detachAppender(appender);
            received.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void absentSelfTestInputIsEmptyAndOnlyInvalidLanguageMetadataIsRejected() {
        Language language = language();
        language.setEnv(null);
        language.setRunCmd(null);
        doReturn(query(language)).when(languages).lambdaQuery();
        TestJudgeDTO request = new TestJudgeDTO();
        request.setCode("print(input())");
        request.setLanguage("Python");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
            service.testJudge(request);
            ArgumentCaptor<JudgeDispatch> created = ArgumentCaptor.forClass(JudgeDispatch.class);
            verify(dispatches).save(created.capture());
            JudgeTask task = codec.task(created.getValue().getTaskPayload());
            assertEquals("", task.getInput());
            assertEquals("", task.getExpectedOutput());
            language.setName("Go");
            assertThrows(JudgeException.class, () -> service.testJudge(request));
            language.setName("Python");
            language.setFileName("main.txt");
            assertThrows(JudgeException.class, () -> service.testJudge(request));
            language.setFileName("main.py");
            language.setType(2);
            assertThrows(JudgeException.class, () -> service.testJudge(request));
            verify(dispatches, times(1)).save(any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void legacyEnvironmentAndEmptyCommandsRemainSnapshotMetadata(String name) {
        Language language = language();
        language.setName(name);
        language.setType("Python".equals(name) ? 1 : 0);
        language.setFileName(switch (name) {
            case "Java" -> "Main.java";
            case "C" -> "main.c";
            case "C++" -> "main.cpp";
            default -> "main.py";
        });
        language.setImage("legacy-vendor/toolchain:old");
        language.setEnv("retired-custom-environment");
        language.setCompileCmd(null);
        language.setRunCmd("");
        language.setVersion("legacy-version");
        doReturn(query(problem())).when(problems).lambdaQuery();
        doReturn(query(language)).when(languages).lambdaQuery();
        doReturn(query(version())).when(versions).lambdaQuery();
        when(testData.getCurrentVersion(10L)).thenReturn(77L);
        when(judges.save(any())).thenReturn(true);
        JudgeDTO request = request(false);
        request.setLanguage(name);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            service.submitJudge(request);
        }
        ArgumentCaptor<JudgeDispatch> created = ArgumentCaptor.forClass(JudgeDispatch.class);
        verify(dispatches).save(created.capture());
        String historicalPayload = created.getValue().getTaskPayload();
        language.setImage("updated-image");
        language.setEnv("updated-environment");
        language.setCompileCmd("updated-compile-command");
        language.setRunCmd("updated-run-command");
        language.setVersion("updated-version");

        JudgeTask historical = codec.task(historicalPayload);
        assertEquals(name, historical.getLanguageConfig().getName());
        assertEquals("legacy-vendor/toolchain:old", historical.getLanguageConfig().getImage());
        assertEquals("retired-custom-environment", historical.getLanguageConfig().getEnv());
        assertNull(historical.getLanguageConfig().getCompileCmd());
        assertEquals("", historical.getLanguageConfig().getRunCmd());
        assertEquals("legacy-version", historical.getLanguageConfig().getVersion());
        assertEquals(77L, historical.getTestDataVersionId());
        verify(languages, never()).lambdaUpdate();
    }

    @Test
    void invalidProblemResourcesAndDispatchTimeoutConfigurationFailEarly() {
        Problem problem = problem();
        problem.setMemoryLimit(2 * 1024 * 1024L + 1);
        doReturn(query(problem)).when(problems).lambdaQuery();
        doReturn(query(language())).when(languages).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user(2L));
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
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
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

    @Test
    void legacyStatusRequiresOwnerAndRemainsAvailableWithoutAWorkerDispatch() {
        doReturn(query(null)).when(dispatches).lambdaQuery();
        Judge historical = new Judge();
        historical.setUserId(2L);
        historical.setStatus(JudgeStatus.STATUS_ACCEPTED);
        historical.setTime(10L);
        doReturn(query(historical)).when(judges).lambdaQuery();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
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
        problem.setSource(ProblemSource.LOCAL);
        problem.setMode(JudgeConstants.ACM);
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
}

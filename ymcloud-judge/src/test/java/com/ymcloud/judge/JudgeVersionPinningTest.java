package com.ymcloud.judge;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.judge.service.impl.JudgeServiceImpl;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.ProblemSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 提交记录与排队任务使用同一个不可变版本，自测不依赖题目数据。
 */
@ExtendWith(MockitoExtension.class)
class JudgeVersionPinningTest {

    @Mock
    private MessageQueueUtils messageQueueUtils;
    @Mock
    private RedisCache redisCache;
    @Mock
    private JudgeEntityService judgeEntityService;
    @Mock
    private ProblemEntityService problemEntityService;
    @Mock
    private LanguageEntityService languageEntityService;
    @Mock
    private ContestEntityService contestEntityService;
    @Mock
    private ContestProblemEntityService contestProblemEntityService;
    @Mock
    private ContestUserEntityService contestUserEntityService;
    @Mock
    private TestDataService testDataService;
    @InjectMocks
    private JudgeServiceImpl judgeService;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normalSubmissionKeepsPinnedVersionWhenCurrentVersionChangesDuringInsert(boolean isolatedQueue) {
        configureQueue(isolatedQueue);
        Problem problem = problem();
        doReturn(query(problem)).when(problemEntityService).lambdaQuery();
        doReturn(query(language())).when(languageEntityService).lambdaQuery();
        AtomicLong currentVersion = new AtomicLong(77L);
        when(testDataService.getCurrentVersion(10L)).thenAnswer(invocation -> currentVersion.get());
        when(judgeEntityService.save(any(Judge.class))).thenAnswer(invocation -> {
            Judge judge = invocation.getArgument(0);
            judge.setId(100L);
            currentVersion.set(88L);
            return true;
        });

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user());
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            assertEquals(100L, judgeService.submitJudge(request(false)));
        }

        ArgumentCaptor<Judge> record = ArgumentCaptor.forClass(Judge.class);
        ArgumentCaptor<JudgeTask> task = ArgumentCaptor.forClass(JudgeTask.class);
        verify(judgeEntityService).save(record.capture());
        verify(messageQueueUtils).sendWithDelay(
                eq(exchange(isolatedQueue)),
                eq(routingKey(isolatedQueue)), task.capture(), eq(5000L));
        assertEquals(77L, record.getValue().getTestDataVersionId());
        assertEquals(77L, task.getValue().getTestDataVersionId());
        verify(testDataService).getCurrentVersion(10L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contestSubmissionPersistsAndQueuesTheSameVersionAndUsesContestMode(boolean isolatedQueue) {
        configureQueue(isolatedQueue);
        Contest contest = new Contest();
        contest.setId(20L);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        contest.setEndTime(LocalDateTime.now().plusHours(1));
        contest.setMode(JudgeConstants.OI);
        ContestProblem contestProblem = new ContestProblem();
        contestProblem.setId(30L);
        contestProblem.setProblemId(10L);
        contestProblem.setDisplayId("A");
        contestProblem.setDisplayTitle("Versioned contest problem");
        when(contestEntityService.getById(20L)).thenReturn(contest);
        doReturn(query(contestProblem)).when(contestProblemEntityService).lambdaQuery();
        when(problemEntityService.getById(10L)).thenReturn(problem());
        doReturn(query(language())).when(languageEntityService).lambdaQuery();
        when(testDataService.getCurrentVersion(10L)).thenReturn(77L);
        when(judgeEntityService.save(any(Judge.class))).thenAnswer(invocation -> {
            Judge judge = invocation.getArgument(0);
            judge.setId(101L);
            return true;
        });

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<IpUtils> ip = mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user());
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            assertEquals(101L, judgeService.submitJudge(request(true)));
        }

        ArgumentCaptor<Judge> record = ArgumentCaptor.forClass(Judge.class);
        ArgumentCaptor<JudgeTask> task = ArgumentCaptor.forClass(JudgeTask.class);
        verify(judgeEntityService).save(record.capture());
        verify(messageQueueUtils).send(
                eq(exchange(isolatedQueue)),
                eq(routingKey(isolatedQueue)), task.capture());
        assertEquals(77L, record.getValue().getTestDataVersionId());
        assertEquals(77L, task.getValue().getTestDataVersionId());
        assertEquals(JudgeConstants.OI, task.getValue().getMode());
        verify(testDataService).getCurrentVersion(10L);
    }

    @Test
    void missingVersionRejectsSubmissionBeforeRecordCacheOrQueueWrites() {
        doReturn(query(problem())).when(problemEntityService).lambdaQuery();
        doReturn(query(language())).when(languageEntityService).lambdaQuery();
        when(testDataService.getCurrentVersion(10L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user());
            assertThrows(JudgeException.class, () -> judgeService.submitJudge(request(false)));
        }

        verify(judgeEntityService, never()).save(any(Judge.class));
        verifyNoInteractions(redisCache, messageQueueUtils);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selfTestPreservesInlineInputAndDoesNotResolveDataVersion(boolean isolatedQueue) {
        configureQueue(isolatedQueue);
        doReturn(query(language())).when(languageEntityService).lambdaQuery();
        TestJudgeDTO request = new TestJudgeDTO();
        request.setCode("print(input())");
        request.setLanguage("Python");
        request.setInput("  text\n\n");
        request.setExpectedOutput("text\n");

        Long submissionId = judgeService.testJudge(request);

        ArgumentCaptor<JudgeTask> task = ArgumentCaptor.forClass(JudgeTask.class);
        verify(messageQueueUtils).sendWithDelay(
                eq(exchange(isolatedQueue)),
                eq(routingKey(isolatedQueue)), task.capture(), eq(5000L));
        assertEquals(submissionId, task.getValue().getSubmissionId());
        assertEquals(request.getInput(), task.getValue().getInput());
        assertNull(task.getValue().getTestDataVersionId());
        verifyNoInteractions(testDataService, judgeEntityService);
    }

    private void configureQueue(boolean isolatedQueue) {
        if (isolatedQueue) {
            ReflectionTestUtils.setField(judgeService, "judgeExchange", exchange(true));
            ReflectionTestUtils.setField(judgeService, "judgeTaskRoutingKey", routingKey(true));
        }
    }

    private String exchange(boolean isolatedQueue) {
        return isolatedQueue ? "judge.storage-v1.topic" : RabbitMQConstants.JUDGE_EXCHANGE;
    }

    private String routingKey(boolean isolatedQueue) {
        return isolatedQueue ? "storage-v1.judge" : RabbitMQConstants.JUDGE_ROUTING_KEY;
    }

    @SuppressWarnings("unchecked")
    private <T> LambdaQueryChainWrapper<T> query(T result) {
        LambdaQueryChainWrapper<T> query = mock(LambdaQueryChainWrapper.class);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(result);
        return query;
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
        return language;
    }

    private User user() {
        User user = new User();
        user.setId(2L);
        user.setUsername("version-test");
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
}

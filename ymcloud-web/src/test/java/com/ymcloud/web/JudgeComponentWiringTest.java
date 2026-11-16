package com.ymcloud.web;

import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.service.ResourceAccessService;
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
import com.ymcloud.pojo.contract.judge.SpjProgram;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchPublisher;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeMessagingConfig;
import com.ymcloud.web.judge.JudgeResultListener;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.judge.SpjCompilation;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import com.ymcloud.web.service.problem.TestDataService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 通过真实 Spring 注入处理器验证判题组件依赖与配置，不连接外部服务。
 */
class JudgeComponentWiringTest {

    /**
     * 正式判题与 SPJ 共享唯一模板，编译回复等待保持有界
     */
    @Test
    void messagingConfigurationSharesTemplateAcrossJudgeAndSpj() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("connectionFactory", mock(ConnectionFactory.class));
            context.getBeanFactory().registerSingleton("judgeMessageCodec", mock(JudgeMessageCodec.class));
            context.getBeanFactory().registerSingleton("redisCache", mock(RedisCache.class));
            context.getBeanFactory().registerSingleton("judgeDispatchEntityService", mock(JudgeDispatchEntityService.class));
            context.getBeanFactory().registerSingleton("judgeResultService", mock(JudgeResultService.class));
            context.getBeanFactory().registerSingleton("judgeDispatchProperties", mock(JudgeDispatchProperties.class));
            context.register(JudgeMessagingConfig.class, JudgeDispatchPublisher.class, SpjCompilation.class);
            context.refresh();

            RabbitTemplate sharedTemplate = context.getBean("judgeRabbitTemplate", RabbitTemplate.class);
            assertEquals(1, context.getBeansOfType(RabbitTemplate.class).size());
            assertSame(sharedTemplate, context.getBean(RabbitTemplate.class));
            assertEquals(SpjProgram.COMPILE_TIMEOUT_MILLIS,
                    ReflectionTestUtils.getField(sharedTemplate, "replyTimeout"));
            assertSame(sharedTemplate,
                    ReflectionTestUtils.getField(context.getBean(JudgeDispatchPublisher.class), "rabbitTemplate"));
            assertSame(sharedTemplate,
                    ReflectionTestUtils.getField(context.getBean(SpjCompilation.class), "rabbitTemplate"));
        }
    }

    /**
     * 注入每个显式依赖和配置值，Web 发布器须选中专用消息模板。
     */
    @Test
    void springInjectsRequiredDependenciesAndConfiguredRoutes() {
        try (var context = new AnnotationConfigApplicationContext()) {
            JudgeDispatchEntityService judgeDispatchEntityService = mock(JudgeDispatchEntityService.class);
            JudgeEntityService judgeEntityService = mock(JudgeEntityService.class);
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            JudgeResultService judgeResultService = mock(JudgeResultService.class);
            RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
            JudgeMessageCodec judgeMessageCodec = mock(JudgeMessageCodec.class);
            JudgeDispatchProperties judgeDispatchProperties = mock(JudgeDispatchProperties.class);
            JudgeCaseEntityService judgeCaseEntityService = mock(JudgeCaseEntityService.class);
            UserEntityService userEntityService = mock(UserEntityService.class);
            UserAcproblemEntityService userAcproblemEntityService = mock(UserAcproblemEntityService.class);
            JudgeStatusCache judgeStatusCache = mock(JudgeStatusCache.class);
            RedisCache redisCache = mock(RedisCache.class);
            ProblemEntityService problemEntityService = mock(ProblemEntityService.class);
            LanguageEntityService languageEntityService = mock(LanguageEntityService.class);
            ContestEntityService contestEntityService = mock(ContestEntityService.class);
            ContestProblemEntityService contestProblemEntityService = mock(ContestProblemEntityService.class);
            ContestUserEntityService contestUserEntityService = mock(ContestUserEntityService.class);
            TestDataService testDataService = mock(TestDataService.class);
            TestDataVersionEntityService testDataVersionEntityService = mock(TestDataVersionEntityService.class);
            JudgeDispatchIdAllocator judgeDispatchIdAllocator = mock(JudgeDispatchIdAllocator.class);
            context.getBeanFactory().registerSingleton("judgeDispatchEntityService", judgeDispatchEntityService);
            context.getBeanFactory().registerSingleton("judgeEntityService", judgeEntityService);
            context.getBeanFactory().registerSingleton("jdbcTemplate", jdbcTemplate);
            context.getBeanFactory().registerSingleton("judgeResultService", judgeResultService);
            context.getBeanFactory().registerSingleton("judgeRabbitTemplate", rabbitTemplate);
            context.getBeanFactory().registerSingleton("judgeMessageCodec", judgeMessageCodec);
            context.getBeanFactory().registerSingleton("judgeDispatchProperties", judgeDispatchProperties);
            context.getBeanFactory().registerSingleton("judgeCaseEntityService", judgeCaseEntityService);
            context.getBeanFactory().registerSingleton("userEntityService", userEntityService);
            context.getBeanFactory().registerSingleton("userAcproblemEntityService", userAcproblemEntityService);
            context.getBeanFactory().registerSingleton("judgeStatusCache", judgeStatusCache);
            context.getBeanFactory().registerSingleton("redisCache", redisCache);
            context.getBeanFactory().registerSingleton("problemEntityService", problemEntityService);
            context.getBeanFactory().registerSingleton("languageEntityService", languageEntityService);
            context.getBeanFactory().registerSingleton("contestEntityService", contestEntityService);
            context.getBeanFactory().registerSingleton("contestProblemEntityService", contestProblemEntityService);
            context.getBeanFactory().registerSingleton("contestUserEntityService", contestUserEntityService);
            context.getBeanFactory().registerSingleton("testDataService", testDataService);
            context.getBeanFactory().registerSingleton("testDataVersionEntityService", testDataVersionEntityService);
            context.getBeanFactory().registerSingleton("judgeDispatchIdAllocator", judgeDispatchIdAllocator);
            context.getBeanFactory().registerSingleton("otherRabbitTemplate", mock(RabbitTemplate.class));
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("wiring", Map.of(
                    "ymcloud.judge.exchange", "wiring.exchange",
                    "ymcloud.judge.task-routing-key", "wiring.route")));
            ResourceAccessService resourceAccess = mock(ResourceAccessService.class);
            context.getBeanFactory().registerSingleton("resourceAccessService", resourceAccess);
            context.refresh();

            JudgeDispatchIdAllocator judgeDispatchIdAllocatorComponent = new JudgeDispatchIdAllocator();
            context.getAutowireCapableBeanFactory().autowireBean(judgeDispatchIdAllocatorComponent);
            assertSame(judgeDispatchEntityService, ReflectionTestUtils.getField(judgeDispatchIdAllocatorComponent, "dispatches"));
            assertSame(judgeEntityService, ReflectionTestUtils.getField(judgeDispatchIdAllocatorComponent, "judges"));
            assertSame(jdbcTemplate, ReflectionTestUtils.getField(judgeDispatchIdAllocatorComponent, "jdbc"));

            JudgeDispatchPublisher judgeDispatchPublisherComponent = new JudgeDispatchPublisher();
            context.getAutowireCapableBeanFactory().autowireBean(judgeDispatchPublisherComponent);
            assertSame(judgeDispatchEntityService, ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "dispatches"));
            assertSame(judgeResultService, ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "results"));
            assertSame(rabbitTemplate, ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "rabbitTemplate"));
            assertSame(judgeMessageCodec, ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "codec"));
            assertSame(judgeDispatchProperties, ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "properties"));
            assertEquals("wiring.exchange", ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "exchange"));
            assertEquals("wiring.route", ReflectionTestUtils.getField(judgeDispatchPublisherComponent, "routingKey"));

            JudgeResultService judgeResultServiceComponent = new JudgeResultService();
            context.getAutowireCapableBeanFactory().autowireBean(judgeResultServiceComponent);
            assertSame(judgeDispatchEntityService, ReflectionTestUtils.getField(judgeResultServiceComponent, "dispatches"));
            assertSame(judgeEntityService, ReflectionTestUtils.getField(judgeResultServiceComponent, "judges"));
            assertSame(judgeCaseEntityService, ReflectionTestUtils.getField(judgeResultServiceComponent, "cases"));
            assertSame(userEntityService, ReflectionTestUtils.getField(judgeResultServiceComponent, "users"));
            assertSame(userAcproblemEntityService, ReflectionTestUtils.getField(judgeResultServiceComponent, "accepted"));
            assertSame(judgeMessageCodec, ReflectionTestUtils.getField(judgeResultServiceComponent, "codec"));
            assertSame(judgeStatusCache, ReflectionTestUtils.getField(judgeResultServiceComponent, "cache"));
            assertSame(judgeDispatchProperties, ReflectionTestUtils.getField(judgeResultServiceComponent, "properties"));

            JudgeResultListener judgeResultListenerComponent = new JudgeResultListener();
            context.getAutowireCapableBeanFactory().autowireBean(judgeResultListenerComponent);
            assertSame(judgeMessageCodec, ReflectionTestUtils.getField(judgeResultListenerComponent, "codec"));
            assertSame(judgeResultService, ReflectionTestUtils.getField(judgeResultListenerComponent, "results"));

            JudgeStatusCache judgeStatusCacheComponent = new JudgeStatusCache();
            context.getAutowireCapableBeanFactory().autowireBean(judgeStatusCacheComponent);
            assertSame(redisCache, ReflectionTestUtils.getField(judgeStatusCacheComponent, "redisCache"));

            JudgeServiceImpl judgeServiceImplComponent = new JudgeServiceImpl();
            context.getAutowireCapableBeanFactory().autowireBean(judgeServiceImplComponent);
            assertSame(judgeEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "judges"));
            assertSame(judgeDispatchEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "dispatches"));
            assertSame(problemEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "problems"));
            assertSame(languageEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "languages"));
            assertSame(contestEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "contests"));
            assertSame(contestProblemEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "contestProblems"));
            assertSame(resourceAccess, ReflectionTestUtils.getField(judgeServiceImplComponent, "resourceAccess"));
            assertSame(testDataService, ReflectionTestUtils.getField(judgeServiceImplComponent, "testData"));
            assertSame(testDataVersionEntityService, ReflectionTestUtils.getField(judgeServiceImplComponent, "versions"));
            assertSame(judgeDispatchProperties, ReflectionTestUtils.getField(judgeServiceImplComponent, "properties"));
            assertSame(judgeMessageCodec, ReflectionTestUtils.getField(judgeServiceImplComponent, "codec"));
            assertSame(judgeStatusCache, ReflectionTestUtils.getField(judgeServiceImplComponent, "cache"));
            assertSame(judgeResultService, ReflectionTestUtils.getField(judgeServiceImplComponent, "results"));
            assertSame(judgeDispatchIdAllocator, ReflectionTestUtils.getField(judgeServiceImplComponent, "allocator"));
        }
    }

    /**
     * 必需依赖缺失时装配失败，不能延迟到消息处理时空指针。
     */
    @Test
    void missingRequiredDependencyFailsInjection() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            assertThrows(UnsatisfiedDependencyException.class,
                    () -> context.getAutowireCapableBeanFactory().autowireBean(new JudgeResultListener()));
        }
    }
}

package com.ymcloud.web;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.web.config.JudgeWorkerIdentityProperties;
import com.ymcloud.web.config.JudgeWorkerSecurityConfig;
import com.ymcloud.web.controller.internal.JudgeWorkerHealthController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 就绪探针必须通过现有服务身份校验，任一基础服务故障均关闭就绪状态且不返回敏感内容。
 */
class JudgeWorkerHealthSecurityTest {

    private static final String TOKEN = "deployment-health-token-32-bytes-minimum";
    private static final String PATH = "/internal/judge-worker/health";

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({JudgeWorkerSecurityConfig.class, JudgeWorkerHealthController.class})
    static class Config {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        StringRedisTemplate redis() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ConnectionFactory rabbit() {
            return mock(ConnectionFactory.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private DataSource dataSource;
    private StringRedisTemplate redis;
    private ConnectionFactory rabbit;
    private Connection database;
    private RedisConnection redisConnection;
    private org.springframework.amqp.rabbit.connection.Connection rabbitConnection;

    @BeforeEach
    void setup() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        context.getBean(JudgeWorkerIdentityProperties.class).setToken(TOKEN);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        dataSource = context.getBean(DataSource.class);
        redis = context.getBean(StringRedisTemplate.class);
        rabbit = context.getBean(ConnectionFactory.class);
        database = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(database);
        when(database.isValid(2)).thenReturn(true);
        RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);
        redisConnection = mock(RedisConnection.class);
        when(redis.getRequiredConnectionFactory()).thenReturn(redisFactory);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        rabbitConnection = mock(org.springframework.amqp.rabbit.connection.Connection.class);
        when(rabbit.createConnection()).thenReturn(rabbitConnection);
        when(rabbitConnection.isOpen()).thenReturn(true);
        clearInvocations(dataSource, redis, rabbit);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void readinessRejectsMissingWrongDuplicateOrUserTokensBeforeAccessingDependencies() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(get(PATH).header("Authorization", "Bearer user-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN, TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(dataSource, redis, rabbit);
    }

    @Test
    void readinessChecksAllDependenciesAndReleasesConnections() throws Exception {
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk()).andExpect(content().string(""));
        verify(database).isValid(2);
        verify(redisConnection).ping();
        verify(rabbitConnection).isOpen();
        verify(database).close();
        verify(redisConnection).close();
        verify(rabbitConnection).close();
    }

    @Test
    void databaseFailureDoesNotExposeCredentialsOrProbeOtherDependencies() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("private-database-url-and-password"));
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
        verifyNoInteractions(redis, rabbit);
    }

    @Test
    void redisAndRabbitFailuresDoNotReportReadyOrExposeDetails() throws Exception {
        when(redisConnection.ping()).thenThrow(new IllegalStateException("private-redis-password"));
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
        verifyNoInteractions(rabbit);
        doReturn("PONG").when(redisConnection).ping();
        when(rabbitConnection.isOpen()).thenReturn(false);
        mvc.perform(get(PATH).header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
    }
}

package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCacheProperties;
import com.ymcloud.judge.data.WorkerDataProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

/**
 * 独立判题进程，仅扫描 Worker，不加载 Web、ORM 或用户认证配置。
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.ymcloud.judge")
@EnableConfigurationProperties({WorkerProperties.class, WorkerDataProperties.class, TestDataCacheProperties.class})
@EnableScheduling
public class JudgeWorkerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JudgeWorkerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = application.run(args);
        WorkerProperties worker = context.getBean(WorkerProperties.class);
        TestDataCacheProperties cache = context.getBean(TestDataCacheProperties.class);
        log.info("判题 Worker 已启动，实例：{}，并发：{}，最大尝试次数：{}，输出上限：{} 字节",
                worker.getId(), worker.getConcurrency(), worker.getMaxAttempts(), worker.getMaxOutputBytes());
        log.info("判题 Worker 本地目录，工作目录：{}，结果目录：{}，缓存目录：{}",
                worker.getWorkDir().toAbsolutePath().normalize(),
                worker.getJournalDir().toAbsolutePath().normalize(),
                Path.of(cache.getDirectory()).toAbsolutePath().normalize());
    }
}

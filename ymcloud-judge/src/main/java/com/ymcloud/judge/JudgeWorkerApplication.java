package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCacheProperties;
import com.ymcloud.judge.data.WorkerDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 独立判题进程，仅扫描 Worker，不加载 Web、ORM 或用户认证配置。
 */
@SpringBootApplication(scanBasePackages = "com.ymcloud.judge")
@EnableConfigurationProperties({WorkerProperties.class, WorkerDataProperties.class, TestDataCacheProperties.class})
@EnableScheduling
public class JudgeWorkerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JudgeWorkerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}

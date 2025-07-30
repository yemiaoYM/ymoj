package com.ymcloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
//开启事务管理功能
@EnableTransactionManagement
//开启缓存注解功能
@EnableCaching
//开启定时任务功能
@EnableScheduling
//开启SpringSecurity安全框架
@EnableWebSecurity
public class YmCloudWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(YmCloudWebApplication.class, args);
    }

}

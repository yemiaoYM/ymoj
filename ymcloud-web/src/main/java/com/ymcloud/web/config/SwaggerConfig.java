package com.ymcloud.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * @PackageName: com.ymcloud.web.config
 * @ClassName: SwaggerConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  14:32
 * @Description: Swagger后台测试接口文档配置
 */
@Configuration
@Slf4j
public class SwaggerConfig {

    /**
     * 配置swagger基本信息
     * @return
     */
    @Bean
    public OpenAPI swaggerOpenApi() {
        return new OpenAPI().info(getApiInfo());
    }

    /**
     * 添加摘要信息
     */
    public Info getApiInfo() {
        return new Info()
                // 设置标题
                .title("YM平台_接口文档")
                // 描述
                .description("YM平台测试接口")
                // 作者信息
                .contact(new Contact().name("Yemiao"))
                // 版本
                .version("版本号: 1.0.0");
    }
}

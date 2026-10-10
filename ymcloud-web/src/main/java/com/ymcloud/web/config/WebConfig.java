package com.ymcloud.web.config;

import com.ymcloud.core.interceptor.RepeatSubmitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @PackageName: com.ymcloud.web.config
 * @ClassName: WebConfig
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  15:58
 * @Description: WebMvc配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RepeatSubmitInterceptor repeatSubmitInterceptor;

    /**
     * 自定义拦截规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/**");
    }

}

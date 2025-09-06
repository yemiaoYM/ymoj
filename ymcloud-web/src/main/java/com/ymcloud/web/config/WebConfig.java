package com.ymcloud.web.config;

import com.ymcloud.core.interceptor.RepeatSubmitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
     * 文件路径
     */
    @Value("${ymcloud.file.base-dir}")
    private String baseDir;

    /**
     * 自定义拦截规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/**");
    }

    /**
     * 配置资源的访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //只向外透露public文件
        registry.addResourceHandler("/files/public/**")
                .addResourceLocations("file:" + baseDir + "/public/")
                .setCachePeriod(1800);//缓存半小时
    }

}

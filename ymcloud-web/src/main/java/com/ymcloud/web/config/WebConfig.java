package com.ymcloud.web.config;

import cn.hutool.core.io.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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

    /**
     * 文件路径
     */
    @Value("${file.base-dir}")
    private String FILE_DIR;

    /**
     * 配置资源的访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //项目所在文件的位置
        String path = System.getProperty("user.dir");
        //公共文件基础路径
        String basePath = path + FileUtil.FILE_SEPARATOR + FILE_DIR + FileUtil.FILE_SEPARATOR + "public";
        //只向外透露public文件
        registry.addResourceHandler("/files/public/**")
                .addResourceLocations("file:" + basePath + FileUtil.FILE_SEPARATOR)
                .setCachePeriod(1800);//缓存半小时
    }

}

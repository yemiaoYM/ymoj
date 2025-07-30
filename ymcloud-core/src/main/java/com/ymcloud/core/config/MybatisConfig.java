package com.ymcloud.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: MybatisConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  20:48
 * @Description: MybatisPlus配置
 */
@Configuration
@MapperScan("com.ymcloud.core.web.mapper")
public class MybatisConfig {

    /**
     * mybatis-plus插件配置
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        //分页插件
        //如果配置多个插件, 切记分页最后添加
        //如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(50L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        return interceptor;
    }
}

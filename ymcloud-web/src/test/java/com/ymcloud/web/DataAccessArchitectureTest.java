package com.ymcloud.web;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 验证每表实体访问层、Mapper XML 和真实 Bean 装配；不连接数据库
 */
class DataAccessArchitectureTest {

    /**
     * 加载所有 Mapper XML 并装配 DAO；检查接口、实现及表映射一一对应。
     * @throws Exception 类加载、XML 解析或装配失败
     */
    @Test
    void everyBusinessTableHasOneRegisteredEntityServiceMapperAndXml() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var xmls = resolver.getResources("classpath*:mapper/*Mapper.xml");
        DataSource dataSource = mock(DataSource.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(new MybatisConfiguration());
        factory.setMapperLocations(xmls);
        SqlSessionFactory sqlSessionFactory = factory.getObject();

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("sqlSessionFactory", SqlSessionFactory.class, () -> sqlSessionFactory);
            context.registerBean(SqlSessionTemplate.class, () -> new SqlSessionTemplate(sqlSessionFactory));
            context.registerBean(MapperScannerConfigurer.class, () -> {
                MapperScannerConfigurer scanner = new MapperScannerConfigurer();
                scanner.setBasePackage("com.ymcloud.core.web.mapper");
                return scanner;
            });
            context.scan("com.ymcloud.core.web.dao.impl");
            context.refresh();

            Set<String> tables = new HashSet<>();
            var interfaces = resolver.getResources("classpath*:com/ymcloud/core/web/dao/*EntityService.class");
            for (var resource : interfaces) {
                String name = resource.getFilename().replace("EntityService.class", "");
                Class<?> entity = Class.forName("com.ymcloud.pojo.entity." + name);
                Class<?> service = Class.forName("com.ymcloud.core.web.dao." + name + "EntityService");
                Class<?> implementation = Class.forName("com.ymcloud.core.web.dao.impl." + name + "EntityServiceImpl");
                Class<?> mapper = Class.forName("com.ymcloud.core.web.mapper." + name + "Mapper");
                assertTrue(service.isInterface(), name);
                assertNull(service.getAnnotation(Service.class), name);
                assertNotNull(implementation.getAnnotation(Service.class), name);
                assertNotNull(mapper.getAnnotation(Mapper.class), name);
                assertEquals(entity, ResolvableType.forClass(service).as(IService.class).resolveGeneric(0), name);
                assertEquals(entity, ResolvableType.forClass(mapper).as(BaseMapper.class).resolveGeneric(0), name);
                assertEquals(mapper, ResolvableType.forClass(implementation).as(ServiceImpl.class).resolveGeneric(0), name);
                assertEquals(1, context.getBeansOfType(service).size(), name);
                assertTrue(implementation.isInstance(context.getBean(service)), name);
                assertEquals(1, context.getBeansOfType(mapper).size(), name);

                var xml = resolver.getResource("classpath:mapper/" + name + "Mapper.xml");
                assertTrue(xml.exists(), name);
                assertTrue(xml.getContentAsString(StandardCharsets.UTF_8)
                        .contains("namespace=\"" + mapper.getName() + "\""), name);
                assertTrue(sqlSessionFactory.getConfiguration().hasMapper(mapper), name);
                assertTrue(tables.add(TableInfoHelper.getTableInfo(entity).getTableName()), name);
            }
            Path sql = Path.of("sql/ymcloud.sql");
            if (!Files.exists(sql)) {
                sql = Path.of("../sql/ymcloud.sql");
            }
            Set<String> schemaTables = Pattern.compile("CREATE TABLE `([^`]+)`")
                    .matcher(Files.readString(sql, StandardCharsets.UTF_8)).results()
                    .map(match -> match.group(1)).collect(Collectors.toSet());
            assertEquals(schemaTables, tables);
            assertEquals(tables.size(), xmls.length);
        }
        verifyNoInteractions(dataSource);
    }
}

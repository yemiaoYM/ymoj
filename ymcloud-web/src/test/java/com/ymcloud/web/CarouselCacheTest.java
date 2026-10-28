package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.config.RedisConfig;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.core.web.mapper.SystemSettingMapper;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.controller.admin.common.AdminCarouselController;
import com.ymcloud.web.controller.common.PublicCarouselController;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.common.impl.CarouselSettingServiceImpl;
import com.ymcloud.web.service.common.impl.PublicImageServiceImpl;
import com.ymcloud.web.service.oj.FileService;
import com.ymcloud.web.service.oj.impl.FileServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 缓存代理、项目序列化和事务失效回归；隔离 Redis 与数据库网络
 */
class CarouselCacheTest {

    private static final String REDIS_KEY = CacheConstants.CACHE_NAME + ":" + CacheConstants.HOME_CAROUSEL_KEY;

    private final Map<String, byte[]> values = new HashMap<>();
    private AnnotationConfigApplicationContext context;
    private CarouselSettingService service;
    private SystemSettingMapper settingMapper;
    private FileMapper fileMapper;
    private StoredFileService storage;
    private RedisCache redisCache;
    private RedisCacheWriter writer;
    private TestTransactionManager transactions;

    /** 启用真实缓存拦截器，不直接调用被缓存的实现对象。 */
    @org.springframework.context.annotation.Configuration
    @EnableCaching
    static class CacheConfig {
    }

    /** 装配真实业务和 RedisCacheManager；模拟字节传输、DAO 及存储边界。 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), SystemSetting.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), File.class);
        settingMapper = mock(SystemSettingMapper.class);
        SystemSettingEntityService settings = mock(SystemSettingEntityService.class);
        when(settings.lambdaQuery()).thenAnswer(call -> new LambdaQueryChainWrapper<>(settingMapper, SystemSetting.class));
        when(settings.lambdaUpdate()).thenAnswer(call -> new LambdaUpdateChainWrapper<>(settingMapper));
        when(settingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("[]"));
        fileMapper = mock(FileMapper.class);
        FileEntityService files = mock(FileEntityService.class);
        when(files.lambdaQuery()).thenAnswer(call -> new LambdaQueryChainWrapper<>(fileMapper, File.class));
        storage = mock(StoredFileService.class);
        redisCache = mock(RedisCache.class);
        when(redisCache.deleteObject(anyString())).thenAnswer(call -> values.remove(call.getArgument(0)) != null);
        writer = mock(RedisCacheWriter.class);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Controller 注解必须在 Service 短事务提交之后清理");
            values.remove(key(call.getArgument(1)));
            return null;
        }).when(writer).remove(anyString(), any(byte[].class));
        when(writer.get(anyString(), any(byte[].class))).thenAnswer(call -> values.get(key(call.getArgument(1))));
        doAnswer(call -> {
            assertEquals(Duration.ZERO, call.getArgument(3), "沿用长期缓存，不设置 TTL");
            values.put(key(call.getArgument(1)), call.getArgument(2));
            return null;
        }).when(writer).put(anyString(), any(byte[].class), any(byte[].class), any(Duration.class));

        // 使用生产键前缀和序列化，仅替换 Redis 网络 writer
        RedisCacheManager production = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class));
        production.afterPropertiesSet();
        var config = ((org.springframework.data.redis.cache.RedisCache)
                production.getCache(CacheConstants.CACHE_NAME)).getCacheConfiguration();
        RedisCacheManager manager = RedisCacheManager.builder(writer).cacheDefaults(config).build();
        transactions = new TestTransactionManager();
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("systemSettingEntityService", settings);
        context.getBeanFactory().registerSingleton("fileEntityService", files);
        context.getBeanFactory().registerSingleton("storedFileService", storage);
        context.getBeanFactory().registerSingleton("redisCache", redisCache);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean("cacheManager", RedisCacheManager.class, () -> manager);
        context.registerBean("transactionManager", TestTransactionManager.class, () -> transactions);
        context.register(CacheConfig.class, PublicImageServiceImpl.class, CarouselSettingServiceImpl.class, FileServiceImpl.class,
                PublicCarouselController.class, AdminCarouselController.class);
        context.refresh();
        service = context.getBean(CarouselSettingService.class);
    }

    /** 关闭本次缓存代理和事务上下文。 */
    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /** 缓存命中不查库；record 图片经过项目序列化仍保持 ID 和顺序。 */
    @Test
    void cachesPublicResultUsingExistingSerializerAndPhysicalKey() {
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("[2,1]"));
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(image(1), image(2)));
        CarouselSettingVO first = readPublic();
        assertEquals(first, readPublic());
        assertEquals(List.of("2", "1"), first.images().stream().map(CarouselSettingVO.Image::id).toList());
        assertNotNull(values.get(REDIS_KEY));
        verify(settingMapper).selectOne(any(Wrapper.class));
        verify(fileMapper).selectList(any(Wrapper.class));
    }

    /** 缺失和明确清空分别缓存；清空不能恢复内置图片。 */
    @Test
    void cachesMissingAndExplicitlyEmptyConfiguration() {
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertFalse(readPublic().configured());
        assertFalse(readPublic().configured());
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("[]"));
        saveFromAdmin();
        assertTrue(readPublic().configured());
        assertTrue(readPublic().images().isEmpty());
        verify(settingMapper, times(4)).selectOne(any(Wrapper.class));
    }

    /** 后台直读数据库，Controller 通过业务接口命中公开缓存。 */
    @Test
    void adminReadsDatabaseWhilePublicControllerUsesCache() {
        readPublic();
        context.getBean(PublicCarouselController.class).get();
        service.get();
        verify(settingMapper, times(2)).selectOne(any(Wrapper.class));
    }

    /** Controller 注解在 Service 短事务提交后清理，下次请求重新装载。 */
    @Test
    void successfulSaveEvictsAfterServiceTransactionCommits() {
        readPublic();
        saveFromAdmin();
        assertFalse(values.containsKey(REDIS_KEY));
        verify(writer).remove(eq(CacheConstants.CACHE_NAME), any(byte[].class));
        verify(redisCache, never()).deleteObject(REDIS_KEY);
        assertTrue(readPublic().images().isEmpty());
        assertTrue(values.containsKey(REDIS_KEY));
    }

    /** 图片删除加入外层事务时，回滚保留原缓存；不模拟数据库回滚能力。 */
    @Test
    void outerRollbackKeepsPreviousCache() {
        File file = image(1);
        when(storage.resolvePublicUrl(file.getUrl())).thenReturn(file);
        readPublic();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            context.getBean(FileService.class).deleteFileByUrl(file.getUrl());
            status.setRollbackOnly();
        });
        assertTrue(values.containsKey(REDIS_KEY));
        verify(redisCache, never()).deleteObject(REDIS_KEY);
    }

    /** 写入失败保留旧缓存，失败不转成功。 */
    @Test
    void failedSaveKeepsPreviousCache() {
        readPublic();
        when(settingMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class, this::saveFromAdmin);
        verify(writer, never()).remove(anyString(), any(byte[].class));
        assertTrue(values.containsKey(REDIS_KEY));
        verify(redisCache, never()).deleteObject(REDIS_KEY);
    }

    /** 图片删除成功提交才清缓存，删除异常保留旧值。 */
    @Test
    void deletingImageInvalidatesThroughBusinessService() {
        File file = image(1);
        when(storage.resolvePublicUrl(file.getUrl())).thenReturn(file);
        FileService fileService = context.getBean(FileService.class);
        readPublic();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            fileService.deleteFileByUrl(file.getUrl());
            assertTrue(values.containsKey(REDIS_KEY));
        });
        assertFalse(values.containsKey(REDIS_KEY));
        readPublic();
        doThrow(new IllegalStateException("storage failure")).when(storage).delete(1L);
        assertThrows(IllegalStateException.class, () -> fileService.deleteFileByUrl(file.getUrl()));
        assertTrue(values.containsKey(REDIS_KEY));
        verify(redisCache).deleteObject(REDIS_KEY);
    }

    /** 损坏配置和 Redis 故障保持失败语义，不以空列表掩盖。 */
    @Test
    void dependencyAndConfigurationFailuresAreNotHidden() {
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("broken"));
        assertThrows(ServiceException.class, () -> readPublic());
        assertTrue(values.isEmpty());
        when(writer.get(anyString(), any(byte[].class))).thenThrow(new IllegalStateException("redis down"));
        assertThrows(IllegalStateException.class, () -> readPublic());
        verify(settingMapper).selectOne(any(Wrapper.class));
    }

    /** 提交后 Redis 清理失败向调用方暴露，不能声称缓存已更新。 */
    @Test
    void evictionFailureIsReported() {
        readPublic();
        doThrow(new IllegalStateException("redis down")).when(writer).remove(anyString(), any(byte[].class));
        assertThrows(IllegalStateException.class, this::saveFromAdmin);
        assertTrue(values.containsKey(REDIS_KEY));
    }

    /** 通过后台 Controller 代理保存，实际执行 CacheEvict。 */
    private void saveFromAdmin() {
        context.getBean(AdminCarouselController.class).save(emptyRequest());
    }

    /**
     * 通过 Controller 代理读取，只解开现有一层 HTTP 响应壳。
     * @return 公开配置
     */
    private CarouselSettingVO readPublic() {
        return context.getBean(PublicCarouselController.class).get().getData();
    }

    /**
     * 还原 Redis 物理键。
     * @param bytes UTF-8 键字节
     * @return 字符串键
     */
    private static String key(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 构造关闭轮播的请求。
     * @return 空文件列表请求
     */
    private static CarouselSettingDTO emptyRequest() {
        CarouselSettingDTO request = new CarouselSettingDTO();
        request.setFileIds(List.of());
        return request;
    }

    /**
     * 构造轮播配置。
     * @param value 文件 ID JSON
     * @return 配置实体
     */
    private static SystemSetting setting(String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue(value);
        return setting;
    }

    /**
     * 构造公开图片。
     * @param id 文件编号
     * @return 图片元数据
     */
    private static File image(long id) {
        File file = new File();
        file.setId(id);
        file.setBizType(FileBizType.IMAGE);
        file.setIsPublic(1);
        file.setUrl("/files/public/test/" + id + ".png");
        return file;
    }

    /** 提供真实 REQUIRED 嵌套和同步回调，不连接数据库。 */
    static class TestTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<boolean[]> active = ThreadLocal.withInitial(() -> new boolean[1]);

        /**
         * 获取当前线程的模拟事务状态，供嵌套调用共享。
         * @return 单元素数组，记录事务是否已经开始
         */
        @Override
        protected Object doGetTransaction() {
            return active.get();
        }

        /**
         * 判断当前线程是否已有事务，使 REQUIRED 调用加入外层事务。
         * @param transaction 当前线程共享的模拟事务状态
         * @return true 表示已有活动事务
         */
        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((boolean[]) transaction)[0];
        }

        /**
         * 标记模拟事务开始，不连接数据库。
         * @param transaction 当前线程共享的模拟事务状态
         * @param definition Spring 传入的事务定义，本测试不模拟隔离级别和超时
         */
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((boolean[]) transaction)[0] = true;
        }

        /**
         * 模拟成功提交；没有数据库操作，同步回调由父类触发。
         * @param status 待提交的事务状态
         */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        /**
         * 模拟回滚；没有数据库操作，由父类完成回滚回调且不执行 afterCommit。
         * @param status 待回滚的事务状态
         */
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        /**
         * 移除线程事务状态，避免影响下一次测试调用。
         * @param transaction 已完成提交或回滚的模拟事务状态
         */
        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.remove();
        }
    }
}

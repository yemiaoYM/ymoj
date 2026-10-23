package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.core.web.dao.TagClassificationEntityService;
import com.ymcloud.core.web.dao.TagEntityService;
import com.ymcloud.core.web.mapper.TagMapper;
import com.ymcloud.core.web.mapper.TagClassificationMapper;
import com.ymcloud.core.web.mapper.ProblemTagMapper;
import com.ymcloud.pojo.dto.admin.TagClassificationDTO;
import com.ymcloud.pojo.dto.admin.TagDTO;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.entity.TagClassification;
import com.ymcloud.web.service.admin.impl.AdminTagServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 标签写入与缓存提交时序回归；不连接数据库或 Redis。 */
@ExtendWith(MockitoExtension.class)
class AdminTagCacheTest {

    @Mock
    private TagEntityService tagEntityService;
    @Mock
    private TagClassificationEntityService tagClassificationEntityService;
    @Mock
    private ProblemTagEntityService problemTagEntityService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private TagClassificationMapper classificationMapper;
    @Mock
    private ProblemTagMapper relationMapper;
    @InjectMocks
    private AdminTagServiceImpl service;

    private final Cache cache = new ConcurrentMapCache(CacheConstants.CACHE_NAME);
    private TransactionTemplate transaction;

    enum Mutation {
        ADD_TAG, UPDATE_TAG, DELETE_TAG, ADD_CLASSIFICATION, UPDATE_CLASSIFICATION, DELETE_CLASSIFICATION
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        for (Class<?> entity : new Class<?>[]{Tag.class, TagClassification.class, ProblemTag.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), entity);
        }
        lenient().when(tagEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(tagMapper, Tag.class));
        lenient().when(tagClassificationEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(classificationMapper, TagClassification.class));
        lenient().when(tagEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(tagMapper));
        lenient().when(tagClassificationEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(classificationMapper));
        lenient().when(problemTagEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(relationMapper));
        lenient().when(tagMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        lenient().when(classificationMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        lenient().when(tagEntityService.getById(1L)).thenReturn(new Tag());
        lenient().when(tagClassificationEntityService.getById(1L)).thenReturn(new TagClassification());
        lenient().when(tagEntityService.save(any(Tag.class))).thenReturn(true);
        lenient().when(tagClassificationEntityService.save(any(TagClassification.class))).thenReturn(true);
        lenient().when(tagEntityService.removeById(1L)).thenReturn(true);
        lenient().when(tagClassificationEntityService.removeById(1L)).thenReturn(true);
        lenient().when(tagMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        lenient().when(classificationMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        lenient().when(cacheManager.getCache(CacheConstants.CACHE_NAME)).thenReturn(cache);
        cache.put(CacheConstants.TAG_LIST_KEY, "old tags");
        cache.put("unrelated", "keep");
        transaction = new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object state, TransactionDefinition definition) {
                // 无外部资源，只验证 Spring 事务回调的提交/回滚时序。
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                assertNotNull(cache.get(CacheConstants.TAG_LIST_KEY), "资源提交前不能失效缓存");
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // 模拟资源回滚；缓存不应收到提交回调。
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void eachMutationEvictsOnlyPublicTagsAfterCommit(Mutation mutation) {
        transaction.executeWithoutResult(status -> {
            mutate(mutation);
            assertNotNull(cache.get(CacheConstants.TAG_LIST_KEY));
        });
        assertNull(cache.get(CacheConstants.TAG_LIST_KEY));
        assertEquals("keep", cache.get("unrelated", String.class));
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void rolledBackMutationKeepsCache(Mutation mutation) {
        transaction.executeWithoutResult(status -> {
            mutate(mutation);
            status.setRollbackOnly();
        });
        assertEquals("old tags", cache.get(CacheConstants.TAG_LIST_KEY, String.class));
    }

    @Test
    void deletingEmptyClassificationSucceedsAndEvictsCache() {
        when(tagMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        transaction.executeWithoutResult(status -> service.deleteTagClassification(1L));
        verify(tagClassificationEntityService).removeById(1L);
        assertNull(cache.get(CacheConstants.TAG_LIST_KEY));
    }

    @Test
    void failedWriteDoesNotEvictCache() {
        when(tagEntityService.save(any(Tag.class))).thenReturn(false);
        assertThrows(ServiceException.class, () -> transaction.executeWithoutResult(status -> mutate(Mutation.ADD_TAG)));
        assertEquals("old tags", cache.get(CacheConstants.TAG_LIST_KEY, String.class));
    }

    private void mutate(Mutation mutation) {
        TagDTO tag = new TagDTO();
        tag.setName("动态规划");
        tag.setColor("#409eff");
        TagClassificationDTO classification = new TagClassificationDTO();
        classification.setName("算法");
        classification.setPriority(1);
        switch (mutation) {
            case ADD_TAG -> service.addTag(tag);
            case UPDATE_TAG -> service.updateTag(1L, tag);
            case DELETE_TAG -> service.deleteTag(1L);
            case ADD_CLASSIFICATION -> service.addTagClassification(classification);
            case UPDATE_CLASSIFICATION -> service.updateTagClassification(1L, classification);
            case DELETE_CLASSIFICATION -> service.deleteTagClassification(1L);
        }
    }
}

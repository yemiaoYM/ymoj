package com.ymcloud.web;

import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.dao.StorageConfigStateEntityService;
import com.ymcloud.web.service.file.impl.StorageConfigCatalogImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 验证存储配置缺表故障不会降级为空列表或默认本地存储
 */
class StorageConfigFailureTest {

    /**
     * 缺少配置表时保留原始数据库异常，不让调用方把故障误认成配置不存在
     */
    @Test
    void missingConfigurationTableDoesNotBecomeAnEmptyResult() {
        StorageConfigEntityService configs = mock(StorageConfigEntityService.class);
        StorageConfigCatalogImpl catalog = new StorageConfigCatalogImpl();
        ReflectionTestUtils.setField(catalog, "configs", configs);
        BadSqlGrammarException failure = new BadSqlGrammarException("lookup", "redacted",
                new SQLException("missing storage table", "42S02", 1146));
        when(configs.lambdaQuery()).thenThrow(failure);

        assertSame(failure, assertThrows(BadSqlGrammarException.class, catalog::list));
        assertSame(failure, assertThrows(BadSqlGrammarException.class, () -> catalog.find("local")));
    }

    /**
     * 默认配置状态读取失败时明确失败，不回退静态本地配置
     */
    @Test
    void missingStateTableDoesNotActivateLocalStorage() {
        StorageConfigStateEntityService states = mock(StorageConfigStateEntityService.class);
        StorageConfigCatalogImpl catalog = new StorageConfigCatalogImpl();
        ReflectionTestUtils.setField(catalog, "states", states);
        ReflectionTestUtils.setField(catalog, "properties", new StorageProperties());
        BadSqlGrammarException failure = new BadSqlGrammarException("lookup", "redacted",
                new SQLException("missing storage state", "42S02", 1146));
        when(states.lambdaQuery()).thenThrow(failure);

        assertSame(failure, assertThrows(BadSqlGrammarException.class, catalog::active));
    }
}

package com.ymcloud.web;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.query.oj.ProblemPageQuery;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import com.ymcloud.web.controller.common.PublicController;
import com.ymcloud.web.service.oj.ProblemService;
import com.ymcloud.web.service.oj.impl.ProblemServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 公开题库多标签筛选的 HTTP 绑定、业务传递与分页 SQL 回归
 */
class ProblemTagFilterTest {

    /**
     * 查询入口接受逗号分隔的多标签并去重，拒绝非法 ID 和超过上限的集合
     * @throws Exception MVC 请求失败
     */
    @Test
    void bindsAndValidatesTagSelection() throws Exception {
        ProblemService service = mock(ProblemService.class);
        PublicController controller = new PublicController();
        ReflectionTestUtils.setField(controller, "problemService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/public/problems").param("tagIds", "1,2,1")
                .param("keyword", "排序").param("difficulty", "2")).andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(ProblemPageQuery.class);
        verify(service).pageQuery(captor.capture());
        assertEquals(Set.of(1L, 2L), captor.getValue().getTagIds());
        assertEquals("排序", captor.getValue().getKeyword());
        assertEquals(2, captor.getValue().getDifficulty());
        for (String invalid : List.of("-1", "0", "abc", "1,,2",
                LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(",")))) {
            mvc.perform(get("/public/problems").param("tagIds", invalid)).andExpect(status().isBadRequest());
        }
    }

    /**
     * 未传标签及空标签保持原有不筛选语义
     * @throws Exception MVC 请求失败
     */
    @Test
    void acceptsRequestsWithoutTags() throws Exception {
        ProblemService service = mock(ProblemService.class);
        PublicController controller = new PublicController();
        ReflectionTestUtils.setField(controller, "problemService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/public/problems")).andExpect(status().isOk());
        mvc.perform(get("/public/problems").param("tagIds", "")).andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(ProblemPageQuery.class);
        verify(service, org.mockito.Mockito.times(2)).pageQuery(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(query -> query.getTagIds().isEmpty()));
    }

    /**
     * 服务保留标签条件和分页总数，空结果不查询提交统计
     */
    @Test
    void forwardsFiltersAndPreservesEmptyPage() {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        JudgeEntityService judges = mock(JudgeEntityService.class);
        ProblemServiceImpl service = new ProblemServiceImpl();
        ReflectionTestUtils.setField(service, "problemEntityService", problems);
        ReflectionTestUtils.setField(service, "judgeEntityService", judges);
        ReflectionTestUtils.setField(service, "resourceAccess", new ResourceAccessService());
        ProblemPageQuery query = new ProblemPageQuery();
        query.setTagIds(Set.of(1L, 2L));
        query.setKeyword("排序");
        query.setDifficulty(2);
        when(problems.pageQuery(any(), eq("排序"), eq(2), eq(Set.of(1L, 2L)), any()))
                .thenReturn(new Page<ProblemTableVO>(1, 15, 0));
        var result = service.pageQuery(query);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(problems).pageQuery(any(), eq("排序"), eq(2), eq(Set.of(1L, 2L)), any());
        verifyNoInteractions(judges);
    }

    /**
     * 实际 Mapper 按标签生成参数化 EXISTS，计数优化保留全部条件，并保留状态常量绑定
     * @throws Exception Mapper 解析失败
     */
    @Test
    void generatesPublicIntersectionWithoutDuplicateRows() throws Exception {
        Configuration configuration = new Configuration();
        var resource = new ClassPathResource("mapper/ProblemMapper.xml");
        try (var stream = resource.getInputStream()) {
            new XMLMapperBuilder(stream, configuration, resource.toString(), configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.ymcloud.core.web.mapper.ProblemMapper.pageQuery");
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        for (List<Long> tags : List.of(List.<Long>of(), List.of(1L), List.of(1L, 2L), List.of(999L))) {
            Map<String, Object> params = new HashMap<>();
            params.put("tagIds", new LinkedHashSet<>(tags));
            params.put("difficulty", -1);
            params.put("scope", new ResourceAccessService().problemScope());
            var bound = statement.getBoundSql(params);
            String sql = bound.getSql();
            assertTrue(sql.contains("p.visibility = ?"));
            assertEquals(1, bound.getAdditionalParameter("publicVisibility"));
            assertEquals(0L, bound.getAdditionalParameter("originalSourceId"));
            assertEquals(tags.size(), sql.split("AND EXISTS", -1).length - 1);
            List<Object> values = bound.getParameterMappings().stream()
                    .filter(mapping -> mapping.getProperty().startsWith("__frch_"))
                    .map(mapping -> bound.getAdditionalParameter(mapping.getProperty())).toList();
            assertEquals(tags, values);
            String countSql = pagination.autoCountSql(new Page<>(), sql);
            assertTrue(countSql.startsWith("SELECT COUNT(*) AS total FROM problem p"));
            assertEquals(tags.size() + 1, countSql.split("EXISTS", -1).length - 1);
        }
        Map<String, Object> combined = new HashMap<>();
        combined.put("keyword", "排序");
        combined.put("difficulty", 2);
        combined.put("source", "洛谷");
        combined.put("tagIds", Set.of(1L, 2L));
        combined.put("scope", new ResourceAccessService().problemScope());
        var bound = statement.getBoundSql(combined);
        assertTrue(bound.getSql().contains("p.difficulty = ?"));
        assertFalse(bound.getSql().contains("p.source = ?"));
        assertFalse(bound.getSql().contains("排序"));
    }
}

package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.AnnouncementEntityService;
import com.ymcloud.core.web.mapper.AnnouncementMapper;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.AnnouncementVO;
import com.ymcloud.web.controller.common.PublicController;
import com.ymcloud.web.service.oj.AnnouncementService;
import com.ymcloud.web.service.oj.impl.AnnouncementServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 公告分页、公开字段与请求契约回归，不访问真实数据库
 */
class AnnouncementServiceTest {

    private AnnouncementEntityService entityService;
    private AnnouncementMapper mapper;
    private AnnouncementService service;
    private AnnotationConfigApplicationContext context;

    /** 初始化真实查询包装器，数据库访问在Mapper边界模拟。 */
    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Announcement.class);
        entityService = mock(AnnouncementEntityService.class);
        mapper = mock(AnnouncementMapper.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(AnnouncementEntityService.class, () -> entityService);
        context.register(AnnouncementServiceImpl.class);
        context.refresh();
        service = context.getBean(AnnouncementService.class);
        when(entityService.lambdaQuery()).thenAnswer(ignored -> new LambdaQueryChainWrapper<>(mapper, Announcement.class));
    }

    /** 关闭本次测试的 Spring 装配上下文。 */
    @AfterEach
    void tearDown() {
        context.close();
    }

    /** 固定五条分页、可见性过滤和确定性排序；重复查询仍访问数据库。 */
    @Test
    @SuppressWarnings("unchecked")
    void readsFiveVisibleAnnouncementsInStableOrderWithoutCache() {
        Announcement entity = new Announcement();
        entity.setId(7L);
        entity.setTitle("比赛通知");
        entity.setContent("正文");
        entity.setAuthorId(1L);
        entity.setStatus(true);
        Page<Announcement> result = new Page<>(2, 5, 6);
        result.setRecords(List.of(entity));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);

        PageVO<AnnouncementVO> actual = service.pageQuery(2);
        service.pageQuery(2);
        assertEquals(6, actual.getTotal());
        assertEquals(2L, actual.getPages());
        assertEquals(7L, actual.getRecords().get(0).getId());
        assertEquals("正文", actual.getRecords().get(0).getContent());

        ArgumentCaptor<Page<Announcement>> pages = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<Announcement>> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(2)).selectPage(pages.capture(), queries.capture());
        assertEquals(2, pages.getValue().getCurrent());
        assertEquals(5, pages.getValue().getSize());
        String sql = queries.getValue().getSqlSegment();
        assertTrue(sql.contains("status ="));
        assertTrue(sql.contains("ORDER BY pinned DESC,priority DESC,updated_time DESC,id DESC"));
        assertFalse(queries.getValue().getSqlSelect().contains("author_id"));
    }

    /** 空数据和越界页保持合法分页语义，不伪造错误。 */
    @Test
    @SuppressWarnings("unchecked")
    void emptyAndOutOfRangePagesReturnEmptyRecords() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(1, 5, 0));
        assertTrue(service.pageQuery(1).getRecords().isEmpty());
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(3, 5, 6));
        PageVO<AnnouncementVO> page = service.pageQuery(3);
        assertTrue(page.getRecords().isEmpty());
        assertEquals(2L, page.getPages());
    }

    /** 非法页码在查询前拒绝，使用现有400业务错误码。 */
    @Test
    void rejectsInvalidPageNumbersBeforeQuery() {
        for (Integer page : new Integer[]{null, 0, -1}) {
            ServiceException exception = assertThrows(ServiceException.class, () -> service.pageQuery(page));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getCode());
        }
        verifyNoInteractions(mapper);
    }

    /** 请求默认第一页且只包装一次；旧pageSize/sortBy参数不能控制查询。
     * @throws Exception MVC请求执行失败时抛出
     */
    @Test
    @SuppressWarnings("unchecked")
    void publicEndpointReturnsPageEnvelopeAndIgnoresClientPageSize() throws Exception {
        Page<Announcement> result = new Page<>(1, 5, 0);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(result);
        PublicController controller = new PublicController();
        ReflectionTestUtils.setField(controller, "announcementService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/public/announcements").param("pageSize", "9999").param("sortBy", "content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(HttpStatus.SUCCESS))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.pages").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.code").doesNotExist());
        ArgumentCaptor<Page<Announcement>> pages = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPage(pages.capture(), any(Wrapper.class));
        assertEquals(1, pages.getValue().getCurrent());
        assertEquals(5, pages.getValue().getSize());
        mvc.perform(get("/public/announcements").param("pageNo", "abc"))
                .andExpect(status().isBadRequest());
    }
}

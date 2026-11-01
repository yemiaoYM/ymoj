package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.oj.ContestRankPageQuery;
import com.ymcloud.pojo.vo.oj.ContestRankVO;
import com.ymcloud.web.service.oj.impl.ContestServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: ACM 保持原排名，OI 与 IOI 暂共用每题最高分查询。
 */
class ContestRankModeTest {

    /**
     * 返回原赛制标识，依据赛制选择已有的排名查询。
     * @param mode 数据库比赛计分模式
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void selectsRankingWithoutChangingContestMode(int mode) {
        ContestEntityService contests = mock(ContestEntityService.class);
        ContestProblemEntityService problems = mock(ContestProblemEntityService.class);
        Contest contest = new Contest();
        contest.setId(20L);
        contest.setMode(mode);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusHours(1));
        when(contests.getById(20L)).thenReturn(contest);
        when(problems.getContestProblemList(20L, 2L)).thenReturn(List.of());
        ContestRankVO row = new ContestRankVO();
        Page<ContestRankVO> page = new Page<>(1, 5);
        page.setRecords(List.of(row));
        page.setTotal(1);
        if (mode == ContestConstants.MODE_ACM) {
            when(contests.getAcmContestRank(any(), eq(20L), any())).thenReturn(page);
        } else {
            when(contests.getOIContestRank(any(), eq(20L), any())).thenReturn(page);
        }
        ContestServiceImpl service = new ContestServiceImpl();
        ReflectionTestUtils.setField(service, "resourceAccess", mock(ResourceAccessService.class));
        ReflectionTestUtils.setField(service, "contestEntityService", contests);
        ReflectionTestUtils.setField(service, "contestProblemEntityService", problems);
        ContestRankPageQuery query = new ContestRankPageQuery();
        query.setContestId(20L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            var result = service.getContestRankList(query);
            assertEquals(mode, result.getMode());
            assertEquals(List.of(row), result.getUserRankData());
            assertEquals(1, result.getTotal());
        }
        if (mode == ContestConstants.MODE_ACM) {
            verify(contests, never()).getOIContestRank(any(), any(), any());
        } else {
            verify(contests, never()).getAcmContestRank(any(), any(), any());
        }
    }
}

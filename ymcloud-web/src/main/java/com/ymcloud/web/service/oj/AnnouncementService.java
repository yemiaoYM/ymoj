package com.ymcloud.web.service.oj;

import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.AnnouncementVO;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 公开公告分页查询契约
 */
public interface AnnouncementService {

    /**
     * 按置顶、权重、更新时间和 ID 降序查询可见公告。
     * @param pageNo 页码至少 1，每页固定 5 条；不使用页面缓存
     * @return 公告分页；无数据时 records 为空列表
     */
    PageVO<AnnouncementVO> pageQuery(Integer pageNo);
}

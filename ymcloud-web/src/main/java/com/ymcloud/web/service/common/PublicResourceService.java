package com.ymcloud.web.service.common;

import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.vo.oj.LanguageTableVO;
import com.ymcloud.pojo.vo.oj.UserAcRankVO;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 公开标签、语言和用户 AC 排名的业务查询
 */
public interface PublicResourceService {

    /**
     * 获取公开标签，保持既有字段契约。
     * @return 标签列表，无数据时为空列表
     */
    List<Tag> listTags();

    /**
     * 获取本站语言选项。
     * @return 语言展示列表
     */
    List<LanguageTableVO> listLanguages();

    /**
     * 查询用户 AC 数排名，供缓存未命中时使用。
     * @return 排名列表
     */
    List<UserAcRankVO> listUserAcRank();
}

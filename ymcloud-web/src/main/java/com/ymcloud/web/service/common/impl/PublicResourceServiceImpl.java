package com.ymcloud.web.service.common.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.TagEntityService;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.enums.ProblemSource;
import com.ymcloud.pojo.vo.oj.LanguageTableVO;
import com.ymcloud.pojo.vo.oj.UserAcRankVO;
import com.ymcloud.web.service.common.PublicResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 通过实体服务读取公开基础数据，HTTP 缓存和响应由 Controller 负责
 */
@Service
public class PublicResourceServiceImpl implements PublicResourceService {

    @Autowired
    private TagEntityService tagEntityService;

    @Autowired
    private LanguageEntityService languageEntityService;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    /**
     * 查询公开题目标签。
     * @return 标签列表，无标签时为空列表
     */
    @Override
    public List<Tag> listTags() {
        return tagEntityService.lambdaQuery()
                .list();
    }

    /**
     * 查询本地判题语言并转换为公开展示字段。
     * @return 语言列表，无匹配项时为空列表
     */
    @Override
    public List<LanguageTableVO> listLanguages() {
        List<Language> languages = languageEntityService.lambdaQuery()
                .eq(Language::getOj, ProblemSource.LOCAL.getCode())
                .list();
        return BeanUtil.copyToList(languages, LanguageTableVO.class);
    }

    /**
     * 查询按 AC 题目数降序排列的前十名用户。
     * @return 用户排名，无 AC 记录时为空列表
     */
    @Override
    public List<UserAcRankVO> listUserAcRank() {
        return userAcproblemEntityService.listUserAcRank();
    }
}

package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.mapper.LanguageMapper;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.vo.oj.LanguageVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: LanguageEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-24  20:14
 * @Description: LanguageEntityServiceImpl
 */
@Service
public class LanguageEntityServiceImpl extends ServiceImpl<LanguageMapper, Language> implements LanguageEntityService {

    /**
     * 按启用状态和来源查询编程语言选项。
     * @param status 启用状态
     * @param oj 已确认的来源编码
     * @return 编程语言选项，无匹配项时为空列表
     */
    @Override
    public List<LanguageVO> getByStatusAndOj(Integer status, String oj) {
        return baseMapper.getByStatusAndOj(status, oj);
    }
}

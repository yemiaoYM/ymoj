package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.mapper.LanguageMapper;
import com.ymcloud.pojo.entity.Language;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: LanguageEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-24  20:14
 * @Description: LanguageEntityServiceImpl
 */
@Service
public class LanguageEntityServiceImpl extends ServiceImpl<LanguageMapper, Language> implements LanguageEntityService {
}

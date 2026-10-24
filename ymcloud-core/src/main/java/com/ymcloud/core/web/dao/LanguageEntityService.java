package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.vo.oj.LanguageVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: LanguageEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-07-24  20:14
 * @Description: LanguageEntityService
 */
public interface LanguageEntityService extends IService<Language> {

    /**
     * 查询指定状态与来源的编程语言选项。
     * @param status 启用状态
     * @param oj 已确认的来源编码
     * @return 编程语言选项
     */
    List<LanguageVO> getByStatusAndOj(Integer status, String oj);
}

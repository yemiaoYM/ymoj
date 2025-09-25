package com.ymcloud.core.web.mapper;
import java.util.List;


import com.ymcloud.pojo.vo.oj.LanguageVO;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Language;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: LanguageMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-24  20:13
 * @Description: LanguageMapper
 */
@Mapper
public interface LanguageMapper extends BaseMapper<Language> {

    /**
     * 根据状态和oj来源查询可用编程语言选项
     * @param status 状态
     * @param oj oj来源
     * @return 前端可用编程语言选项
     */
    @Select("select name,version,content_type,template_code,code_template from language WHERE status = #{status} AND oj = #{oj}")
    List<LanguageVO> getByStatusAndOj(@Param("status") Integer status, @Param("oj") String oj);
}

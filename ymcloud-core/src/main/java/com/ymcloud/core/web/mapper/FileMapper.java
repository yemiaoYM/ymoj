package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.File;
import org.apache.ibatis.annotations.Mapper;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: FileMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:17
 * @Description: FileMapper
 */
@Mapper
public interface FileMapper extends BaseMapper<File> {
}

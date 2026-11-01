package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: FileMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:17
 * @Description: FileMapper
 */
@Mapper
public interface FileMapper extends BaseMapper<File> {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    Page<File> pageAuthorized(@Param("page") Page<File> page,
                              @Param("query") AdminFilePageQuery query,
                              @Param("scope") FileAccessScope scope);
}

package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: FileMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:17
 * @Description: FileMapper
 */
@Mapper
public interface FileMapper extends BaseMapper<FileInfo> {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    Page<FileInfo> pageAuthorized(@Param("page") Page<FileInfo> page,
                                  @Param("query") AdminFilePageQuery query,
                                  @Param("scope") FileAccessScope scope);

    /**
     * 将当前用户预上传的正文图片关联到新建业务记录
     * @param fileIds 本次提交的图片主键
     * @param type 创建入口确定的文件业务类型
     * @param bizId 新建业务的数据库主键
     * @param userId 当前登录用户主键
     * @return 实际绑定的行数
     */
    int bindImages(@Param("fileIds") List<Long> fileIds, @Param("type") FileBizType type,
                   @Param("bizId") Long bizId, @Param("userId") Long userId);
}

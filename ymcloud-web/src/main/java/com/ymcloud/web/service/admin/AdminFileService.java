package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 后台文件列表、上传和下载删除的业务权限入口
 */
public interface AdminFileService {

    /**
     * 按当前用户权限过滤后分页查询文件
     * @param query 已校验的分页和筛选参数
     * @return 可见文件及分页总数
     */
    PageVO<AdminFileVO> queryList(AdminFilePageQuery query);

    /**
     * 将通用文件上传到默认存储并登记当前用户
     * @param file 管理员选择的文件
     * @return 已登记的文件信息
     */
    AdminFileVO upload(MultipartFile file);

    /**
     * 删除当前用户可管理的普通文件
     * @param ids 一个或多个文件主键
     */
    void delete(List<Long> ids);
}

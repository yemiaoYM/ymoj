package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import org.springframework.web.multipart.MultipartFile;

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
     * 校验下载权限并返回固定文件元数据
     * @param id 对外 UUIDv4 文件标识
     * @return 已授权下载的文件
     */
    File requireDownload(String id);

    /**
     * 按当前用户权限上传通用文件
     * @param file 用户上传内容
     * @param isPublic 0 为私有，1 为公开
     * @return 上传后可展示的文件信息
     */
    AdminFileVO upload(MultipartFile file, Integer isPublic);

    /**
     * 检查文件归属与引用后删除普通文件
     * @param id 对外 UUIDv4 文件标识
     */
    void delete(String id);
}

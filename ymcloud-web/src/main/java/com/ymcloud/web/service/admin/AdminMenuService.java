package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.vo.admin.RouterVo;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminMenuService
 * @Author: Yemiao
 * @CreateTime: 2025-09-15  22:38
 * @Description: AdminMenuService
 */
public interface AdminMenuService {

    /**
     * 获取菜单树
     * @return 菜单树
     */
    List<RouterVo> selectMenuTree();
}

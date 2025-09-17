package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.admin.RouterVo;
import com.ymcloud.web.service.admin.AdminMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.common
 * @ClassName: RouterController
 * @Author: Yemiao
 * @CreateTime: 2025-09-15  22:20
 * @Description: 菜单控制
 */
@Slf4j
@RestController
@RequestMapping("/admin/menu")
public class MenuController {

    @Autowired
    private AdminMenuService menuService;

    /**
     * 获取动态路由菜单
     * @return 路由菜单
     */
    @GetMapping
    public Result<List<RouterVo>> getMenus(){
        log.info("获取路由菜单");
        List<RouterVo> routers = menuService.selectMenuTree();
        return Result.ok(routers);
    }

}

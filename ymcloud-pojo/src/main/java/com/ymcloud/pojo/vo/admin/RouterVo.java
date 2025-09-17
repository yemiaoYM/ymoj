package com.ymcloud.pojo.vo.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: RouterVo
 * @Author: Yemiao
 * @CreateTime: 2025-09-17  22:13
 * @Description: 路由配置信息
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouterVo {

    /**
     * 路由名字
     */
    private String name;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件地址
     */
    private String component;

    /**
     * 其他元素
     */
    private MetaVo meta;

    /**
     * 子路由
     */
    private List<RouterVo> children = new ArrayList<>();

}

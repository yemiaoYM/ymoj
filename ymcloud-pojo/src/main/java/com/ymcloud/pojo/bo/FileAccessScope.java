package com.ymcloud.pojo.bo;

import lombok.Data;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 服务端计算的文件功能权限及资源范围，仅用于 Mapper 授权过滤
 */
@Data
public class FileAccessScope {

    /**
     * 当前用户 ID
     */
    private Long userId;

    /**
     * 全站资源管理范围
     */
    private boolean administrator;

    /**
     * 本人原题管理能力
     */
    private boolean problemManager;

    /**
     * 本人比赛管理能力
     */
    private boolean contestManager;

    /**
     * 通用文件列表功能权限
     */
    private boolean generalFiles;

    /**
     * 普通私有文件功能权限
     */
    private boolean privateFiles;

    /**
     * 隐藏测试数据功能权限，仍须原题归属检查
     */
    private boolean testData;
}

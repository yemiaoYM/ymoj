package com.ymcloud.core.web.domain.server;

import lombok.Data;

/**
 * @PackageName: com.ymcloud.web.controller.admin.monitor
 * @ClassName: ServerController
 * @Author: Yemiao
 * @CreateTime: 2025-09-11  23:29
 * @Description: 系统相关信息
 */
@Data
public class Sys {
    /**
     * 服务器名称
     */
    private String computerName;

    /**
     * 服务器Ip
     */
    private String computerIp;

    /**
     * 项目路径
     */
    private String userDir;

    /**
     * 操作系统
     */
    private String osName;

    /**
     * 系统架构
     */
    private String osArch;

}

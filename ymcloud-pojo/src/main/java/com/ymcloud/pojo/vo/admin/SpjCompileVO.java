package com.ymcloud.pojo.vo.admin;

import lombok.Data;
import lombok.ToString;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 管理端 SPJ 编译结果
 */
@Data
public class SpjCompileVO {

    /**
     * 本次编译请求的唯一标识
     */
    private String requestId;

    /**
     * 本次编译所用源码的摘要
     */
    private String digest;

    /**
     * 编译是否通过
     */
    private boolean success;

    /**
     * 供管理员查看的有界编译诊断
     */
    @ToString.Exclude
    private String message;
}

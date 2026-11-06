package com.ymcloud.pojo.contract.judge;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 仅向管理端返回编译状态及有界诊断，不传递沙箱文件 ID
 */
public record SpjCompileResult(String requestId, String digest, boolean success, String message) {}

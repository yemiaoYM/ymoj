package com.ymcloud.pojo.contract.judge;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 有总截止时间的 SPJ 编译检查消息，不创建正式提交
 */
public record SpjCompileRequest(String requestId, long deadlineEpochMs, SpjProgram program) {}

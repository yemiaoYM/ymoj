package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 任务选定的编程语言，由 Worker 映射到固定工具链与执行命令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeLanguage {

    private String name;
}

package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 提交操作的预期执行标识，拒绝重复和过期操作
 */
@Data
public class SubmissionExecutionDTO {

    /** 列表读取时的执行标识，未建立任务箱的历史提交为 null */
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            message = "提交执行标识无效")
    private String executionId;
}

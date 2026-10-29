package com.ymcloud.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 超级管理员手动设置提交最终状态及部分得分
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubmissionStatusDTO extends SubmissionExecutionDTO {

    /** 手动设置的最终状态码 */
    private Integer status;

    /** 仅部分正确时填写，范围 1～99，其他状态由服务端确定分数 */
    private Integer score;

    /**
     * 验证操作、最终状态及部分分数的组合
     * @return 是否为合法的管理命令
     */
    @JsonIgnore
    @AssertTrue(message = "请选择最终状态，部分正确的分数须为 1～99")
    public boolean isValidCommand() {
        if (status == null || !Set.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 8, 10).contains(status)) {
            return false;
        }
        return status == 8 ? score != null && score > 0 && score < 100 : score == null;
    }
}

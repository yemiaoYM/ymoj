package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 正式提交及自测共用的持久任务箱，保存所属用户、固定任务快照和最新结果。
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class JudgeDispatch {

    @TableId(type = IdType.AUTO)
    @ToString.Include
    private Long id;

    @ToString.Include
    private Long submissionId;

    @ToString.Include
    private String executionId;

    private Long userId;

    private Integer strategy;

    private Long problemId;

    private String taskPayload;

    private String resultPayload;

    @ToString.Include
    private Integer dispatchState;

    @ToString.Include
    private JudgeStatus status;

    private Integer lastResultSequence;

    private Integer publishAttempts;

    private Integer recoveryAttempts;

    private LocalDateTime nextAttemptTime;

    private LocalDateTime publishedTime;

    private LocalDateTime deadlineTime;

    private String leaseToken;

    private LocalDateTime leaseUntil;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

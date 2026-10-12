package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测样例记录表。 */
@Data
public class JudgeCase {

    /** 评测样例记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交ID */
    private Long judgeId;

    /** 样例在评测中的顺序编号（从1开始） */
    private Integer caseIndex;

    /** 评测结果 */
    private JudgeStatus status;

    /** 运行耗时(ms) */
    private Long time;

    /** 运行内存(KiB) */
    private Long memory;

    /** 该样例得分（OI模式下有效） */
    private Integer score;

    /** 用户程序输出（部分信息） */
    private String output;

    /** 运行错误信息（CE、RE、WA等时可能会有） */
    private String errorMessage;

    /** 记录创建时间 */
    private LocalDateTime createdTime;

    /** 记录更新时间 */
    private LocalDateTime updatedTime;
}

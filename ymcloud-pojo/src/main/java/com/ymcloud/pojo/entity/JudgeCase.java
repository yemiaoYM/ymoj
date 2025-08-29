package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.JudgeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="评测样例记录表")
public class JudgeCase {
    @TableId(type = IdType.AUTO)
    @Schema(description="评测样例记录ID")
    private Long id;
    @Schema(description="提交ID")
    private Long judgeId;
    @Schema(description="样例在评测中的顺序编号（从1开始）")
    private Integer index;
    @Schema(description="评测结果")
    private JudgeStatus status;
    @Schema(description="运行耗时(ms)")
    private Long time;
    @Schema(description="运行内存(KB)")
    private Long memory;
    @Schema(description="该样例得分（OI模式下有效）")
    private Integer score;
    @Schema(description = "用户程序输出（部分信息）")
    private String output;
    @Schema(description="运行错误信息（CE、RE、WA等时可能会有）")
    private String errorMessage;
    @Schema(description="记录创建时间")
    private LocalDateTime createdTime;
    @Schema(description="记录更新时间")
    private LocalDateTime updatedTime;
}

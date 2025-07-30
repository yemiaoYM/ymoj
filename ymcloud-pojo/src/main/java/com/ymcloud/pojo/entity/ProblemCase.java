package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="测试用例表")
public class ProblemCase {
    @TableId(type = IdType.AUTO)
    @Schema(description="测试用例ID")
    private Long id;
    @Schema(description="题目ID，关联problem表")
    private Long problemId;
    @Schema(description="输入内容")
    private String input;
    @Schema(description="输出内容")
    private String output;
    @Schema(description="样例解释或者提示")
    private String note;
    @Schema(description="是否为示例用例（1=是，0=否，题面展示用）")
    private Integer isSample;
    @Schema(description="该用例对应得分（OI/IOI 模式下可用）")
    private Integer score;
    @Schema(description="测试用例所属分组（用于分组评测）")
    private Integer groupIndex;
    @Schema(description="组内顺序编号（执行顺序）")
    private Integer caseIndex;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

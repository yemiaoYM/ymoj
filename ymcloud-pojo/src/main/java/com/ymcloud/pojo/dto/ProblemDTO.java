package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: ProblemDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-08  17:58
 * @Description: 查询题目实体
 */
@Data
public class ProblemDTO {

    /**
     * 题目唯一标识
     */
    private String problemId;
    /**
     * 是否为比赛题目
     */
    @NotNull(message = "请指定是否为比赛题目")
    private Boolean isContest;
    /**
     * 所属比赛id
     */
    private Long contestId;
    /**
     * 比赛中该题的题号索引
     */
    private String contestProblemIndex;

}

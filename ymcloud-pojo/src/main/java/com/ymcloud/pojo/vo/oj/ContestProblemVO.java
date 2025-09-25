package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestProblemVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  17:50
 * @Description: 比赛题目vo
 */
@Data
public class ContestProblemVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 展示id（A，B，C等等）
     */
    private String displayId;
    /**
     * 题目
     */
    private String displayTitle;
    /**
     * 气球颜色
     */
    private String color;
    /**
     * ac数量
     */
    private int ac;
    /**
     * 总提交数
     */
    private int total;
}

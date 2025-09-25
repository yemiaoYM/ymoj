package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestProblemTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:56
 * @Description: 比赛题目列表前端实体类
 */
@Data
public class ContestProblemTableVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 该题目在比赛中的顺序id
     */
    private String displayId;
    /**
     * 该题目在比赛中的标题，默认为原名字
     */
    private String displayTitle;
    /**
     * 气球颜色
     */
    private String color;
    /**
     * 该题目的ac通过数
     */
    private int ac;
    /**
     * 该题目的总提交数
     */
    private int total;
    /**
     * 用户改题目的通过状态
     */
    private boolean isAccepted;
}

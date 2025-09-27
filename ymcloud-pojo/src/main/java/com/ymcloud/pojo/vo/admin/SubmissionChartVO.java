package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: SubmissionChartVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  21:00
 * @Description: 提交数据折线图VO
 */
@Data
public class SubmissionChartVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 日期列表（格式：MM-dd）
     */
    private List<String> dates;

    /**
     * 总提交数列表
     */
    private List<Long> totalSubmissions;

    /**
     * 通过数列表
     */
    private List<Long> acceptedSubmissions;
}

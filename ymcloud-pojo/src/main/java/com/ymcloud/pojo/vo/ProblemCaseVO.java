package com.ymcloud.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ProblemCaseVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  14:46
 * @Description: 前端返回题面样例
 */
@Data
public class ProblemCaseVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 输入内容
     */
    private String input;
    /**
     * 输出内容
     */
    private String output;
    /**
     * 样例解释或者提示
     */
    private String note;
}

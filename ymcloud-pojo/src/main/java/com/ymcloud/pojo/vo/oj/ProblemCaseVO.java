package com.ymcloud.pojo.vo.oj;

import jakarta.validation.constraints.Size;
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
    @Size(max = 200,message = "测试样例输入内容长度不能超过200")
    private String input;
    /**
     * 输出内容
     */
    @Size(max = 200,message = "测试样例输出内容长度不能超过200")
    private String output;
    /**
     * 样例解释或者提示
     */
    @Size(max = 200,message = "测试样例备注内容长度不能超过200")
    private String note;
}

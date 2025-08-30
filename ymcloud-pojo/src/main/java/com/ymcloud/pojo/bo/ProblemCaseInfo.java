package com.ymcloud.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.bo
 * @ClassName: ProblemCaseInfo
 * @Author: Yemiao
 * @CreateTime: 2025-08-30  23:29
 * @Description: 题目测试用例清单表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemCaseInfo {

    /**
     * 样例编号
     */
    private Integer id;
    /**
     * 输入文件
     */
    private String input;
    /**
     * 输出文件
     */
    private String output;

}

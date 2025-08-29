package com.ymcloud.judge.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: TestCase
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:52
 * @Description: 测试用例
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestCase {
    /**
     * 测试用例编号
     */
    private Integer id;
    /**
     * 输入
     */
    private String input;
    /**
     * 输出
     */
    private String output;
}

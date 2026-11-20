package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.bo.problem.ProblemCaseManifest;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 测试点编辑页面的完整清单及其编辑基线
 */
@Getter
@Setter
public class ProblemCaseListVO extends ProblemCaseManifest {

    /**
     * 页面编辑所基于的清单版本，题目尚未发布清单时为 null
     */
    private Long version;
}

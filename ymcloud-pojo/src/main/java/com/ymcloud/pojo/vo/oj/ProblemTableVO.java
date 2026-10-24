package com.ymcloud.pojo.vo.oj;

import com.ymcloud.pojo.entity.Tag;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:55
 * @Description: 前端题目列表表单
 */
@Data
public class ProblemTableVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 题目唯一标识
     */
    private String problemId;
    /**
     * 题目标题
     */
    private String title;
    /**
     * 题目来源说明，最多255字符，可为空；不参与判题和题号生成
     */
    private String source;
    /**
     * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
     */
    private Integer difficulty;
    /**
     * 标签
     */
    private List<Tag> tagList;
    /**
     * 通过提交数
     */
    private int acceptCount;

    /**
     * 总提交数
     */
    private int submitCount;
}

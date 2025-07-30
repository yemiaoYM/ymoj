package com.ymcloud.pojo.vo;

import com.ymcloud.pojo.entity.Tag;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ProblemTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:55
 * @Description: 前端题目列表表单
 */
@Data
public class ProblemTableVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 表单展示序号：key+id
     */
    private String displayId;
    /**
     * 题目描述内容（题干）
     */
    private String title;
    /**
     * 题目来源
     */
    private String source;
    /**
     * 难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上
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

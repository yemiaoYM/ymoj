package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import lombok.Data;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 各平台统一的未保存题面草稿，不包含隐藏测试数据
 */
@Data
public class RemoteProblemVO {

    /**
     * 导入时已保存的题面图片 ID，随新增题目请求提交
     */
    private List<String> fileIds;

    /**
     * 建议的展示题号，可在新增页修改
     */
    private String problemId;
    /**
     * 原题标题，保留原文
     */
    private String title;
    /**
     * Markdown 题干，包含背景和提示
     */
    private String content;
    /**
     * Markdown 输入说明
     */
    private String input;
    /**
     * Markdown 输出说明
     */
    private String output;
    /**
     * 包含平台、题号及原题链接的出处说明
     */
    private String source;
    /**
     * 原题时间限制，单位毫秒
     * 平台未公开时为 null
     */
    private Long timeLimit;
    /**
     * 原题内存限制，单位 MiB
     * 平台未公开时为 null
     */
    private Long memoryLimit;
    /**
     * 平台与本站题目模式的差异说明，无额外说明时为 null
     */
    private String notice;
    /**
     * 最多前三组公开样例
     */
    private List<ProblemCaseVO> problemCaseList;
}

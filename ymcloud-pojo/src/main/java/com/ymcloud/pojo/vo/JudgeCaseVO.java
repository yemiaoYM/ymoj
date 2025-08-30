package com.ymcloud.pojo.vo;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: JudgeCaseVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-04  13:54
 * @Description: 评测样例结果
 */
@Data
public class JudgeCaseVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 样例在评测中的顺序编号（从1开始）
     */
    private Integer caseIndex;
    /**
     * 评测结果
     */
    private JudgeStatus status;
    /**
     * 运行耗时(ms)
     */
    private Long time;
    /**
     * 运行内存(KB)
     */
    private Long memory;
    /**
     * 该样例得分（OI模式下有效）
     */
    private Integer score;
}

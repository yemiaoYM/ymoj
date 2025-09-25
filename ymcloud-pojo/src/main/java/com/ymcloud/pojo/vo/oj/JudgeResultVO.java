package com.ymcloud.pojo.vo.oj;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: JudgeResultVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-12  18:56
 * @Description: 评测结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JudgeResultVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 评测状态
     */
    private JudgeStatus status;
    /**
     * 运行时间
     */
    private Long time;
    /**
     * 运行内存
     */
    private Long memory;
    /**
     * 用户输入（只在自测模式下生效）
     */
    private String input;
    /**
     * 程序输出（只在自测模式下生效）
     */
    private String output;
    /**
     * 期望输出（只在自测模式下生效）
     */
    private String expectedOutput;
    /**
     * 错误内容
     */
    private String errorMessage;
}

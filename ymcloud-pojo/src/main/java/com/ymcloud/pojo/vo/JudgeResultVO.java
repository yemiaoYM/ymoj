package com.ymcloud.pojo.vo;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: JudgeResultVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-12  18:56
 * @Description: 评测结果
 */
@Data
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
     * 错误内容
     */
    private String errorMessage;
}

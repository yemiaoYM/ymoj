package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: ProblemAddOrUpdateDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  22:10
 * @Description: 添加题目请求体
 */
@Data
public class ProblemDTO {

    /**
     * 题目展示id
     */
    @NotBlank(message = "题目展示id不能为空")
    @Size(max = 20, message = "题目展示id长度不能超过20个字符")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "题目展示id只能包含字母、数字和 -")
    private String problemId;

    /**
     * 题目标题
     */
    @NotBlank(message = "题目标题不能为空")
    @Size(max = 50, message = "题目标题长度不能超过50个字符")
    private String title;

    /**
     * 题目描述内容（题干）
     */
    @NotBlank(message = "题干不能为空")
    private String content;

    /**
     * 输入描述
     */
    @NotBlank(message = "输入描述不能为空")
    @Size(max = 2000, message = "输入描述长度不能超过2000字符")
    private String input;

    /**
     * 输出描述
     */
    @NotBlank(message = "输出描述不能为空")
    @Size(max = 2000, message = "输出描述长度不能超过2000字符")
    private String output;

    /**
     * 题目来源
     */
    @NotBlank(message = "请选择题目来源")
    @Size(max = 255, message = "题目来源长度不能超过255个字符")
    private String source;

    /**
     * 难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上
     */
    @NotNull(message = "请选择题目难度")
    @Min(value = 0, message = "难度参数非法")
    @Max(value = 4, message = "难度参数非法")
    private Integer difficulty;

    /**
     * 评测模式：0=ACM，1=OI
     */
    @NotNull(message = "请选择题目评测模式")
    @Min(value = 0, message = "评测模式参数非法")
    @Max(value = 1, message = "评测模式参数非法")
    private Integer mode;

    /**
     * 题目可见性
     */
    @NotNull(message = "请选择题目可见性")
    @Min(value = 1, message = "可见性参数非法")
    @Max(value = 3, message = "可见性参数非法")
    private Integer visibility;

    /**
     * 是否允许查看测试样例结果
     */
    @NotNull(message = "请设置是否允许查看测试样例结果")
    private Boolean openCaseResult;

    /**
     * 时间限制（ms）
     */
    @NotNull(message = "请设定时间限制")
    @Min(value = 1000, message = "时间限制不能少于1000ms(1秒)")
    @Max(value = 10000, message = "时间限制不能超过10000ms(10秒)")
    private Long timeLimit;

    /**
     * 内存限制（MB）
     */
    @NotNull(message = "请设定内存限制")
    @Min(value = 256, message = "内存限制不能少于256MB")
    @Max(value = 512, message = "内存限制不能超过512MB")
    private Long memoryLimit;

    /**
     * 题面样例
     */
    @Size(max = 5,message = "最多设置5个测试样例")
    private List<ProblemCaseVO> problemCaseList;

    /**
     * 标签列表
     */
    @Size(max = 5,message = "最多设置5个标签")
    private List<Long> tagList;
}
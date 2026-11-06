package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  22:10
 * @Description: 添加题目请求体
 */
@Data
public class ProblemDTO {

    /**
     * 新增题目的校验组，包含通用约束；编辑保留已有展示 ID。
     */
    public interface Create extends Default {}

    /**
     * 新增时自定义展示 ID：1～15 位英文字母、数字或下划线；编辑不修改原 ID。
     */
    @NotBlank(message = "题目展示ID不能为空", groups = Create.class)
    @Size(max = 15, message = "题目展示ID不能超过15个字符", groups = Create.class)
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "题目展示ID只能包含英文字母、数字和下划线", groups = Create.class)
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
     * 题目来源说明，最多255字符，可为空；不决定判题方式或展示 ID
     */
    @Size(max = 255, message = "题目来源长度不能超过255个字符")
    private String source;

    /**
     * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
     */
    @NotNull(message = "请选择题目难度")
    @Min(value = 1, message = "难度参数非法")
    @Max(value = 5, message = "难度参数非法")
    private Integer difficulty;

    /**
     * 判题方式：default=普通、spj=特殊，交互判题暂不开放
     */
    @NotBlank(message = "请选择题目判题方式")
    @Pattern(regexp = "default|spj|interactive", message = "判题方式参数非法")
    private String judgeMode;

    @Pattern(regexp = "C\\+\\+", message = "testlib SPJ 仅支持 C++")
    private String spjLanguage;

    @Size(max = 65535, message = "SPJ 源码不能超过65535个字符")
    @ToString.Exclude
    private String spjCode;

    /**
     * SPJ 模式必须同时提交语言和源码
     * @return 特殊判题配置是否完整
     */
    @JsonIgnore
    @AssertTrue(message = "特殊判题必须填写 SPJ 语言和源码")
    public boolean isSpjConfigured() {
        return !"spj".equals(judgeMode)
                || (spjLanguage != null && spjCode != null && !spjCode.isBlank());
    }

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
    @Max(value = 1024, message = "内存限制不能超过1024MB")
    private Long memoryLimit;

    /**
     * 题面样例
     */
    @Size(max = 3, message = "最多设置3组题面样例")
    private List<ProblemCaseVO> problemCaseList;

    /**
     * 标签列表
     */
    @Size(max = 5,message = "最多设置5个标签")
    private List<Long> tagList;
}

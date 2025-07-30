package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "题目表")
public class Problem {
    @TableId(type = IdType.AUTO)
    @Schema(description="题目ID")
    private Long id;
    @Schema(description="问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等")
    private String problemId;
    @Schema(description="题目标题")
    private String title;
    @Schema(description="题目描述内容（题干）")
    private String content;
    @Schema(description="是否为vj判题")
    private Integer isRemote;
    @Schema(description="输入描述")
    private String inputDescription;
    @Schema(description="输出描述")
    private String outputDescription;
    @Schema(description="题目来源")
    private String source;
    @Schema(description="难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上")
    private Integer difficulty;
    @Schema(description="评测模式：0=ACM，1=OI")
    private Integer mode;
    @Schema(description="可见性：1=公开，2=私有，3=比赛专用")
    private Integer visibility;
    @Schema(description="是否允许查看测试样例结果（0=否，1=是）")
    private Integer openCaseResult;
    @Schema(description="时间限制（ms）")
    private Long timeLimit;
    @Schema(description="内存限制（KB）")
    private Long memoryLimit;
    @Schema(description="出题人ID（user.id）")
    private Long authorId;
    @Schema(description="出题人用户名")
    private String authorUsername;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

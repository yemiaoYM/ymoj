package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.ymcloud.pojo.enums.JudgeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="评测表")
public class Judge {
    @TableId(type = IdType.AUTO)
    @Schema(description="评测ID")
    private Long id;
    @Schema(description="题目ID")
    private Long problemId;
    @Schema(description="题目标题")
    private String problemTitle;
    @Schema(description="题目展示ID，例如 ABC100-A")
    private String displayId;
    @Schema(description="用户ID")
    private Long userId;
    @Schema(description="用户名")
    private String username;
    @Schema(description="编程语言名称")
    private String language;
    @Schema(description="用户提交代码")
    private String code;
    @Schema(description="代码长度，单位：字符")
    private Integer codeLength;
    @Schema(description="评测状态")
    private JudgeStatus status;
    @Schema(description="得分，ACM 模式为 NULL，OI 模式使用")
    private Integer score;
    @Schema(description="执行耗时(ms)")
    private Long time;
    @Schema(description="内存使用(KB)")
    private Long memory;
    @Schema(description="编译错误/运行时错误信息")
    private String errorMessage;
    @Schema(description="是否公开代码：0=私有，1=公开")
    private Boolean share;
    @Schema(description="是否为人工评测记录：0=自动，1=人工")
    private Integer isManual;
    @Schema(description="所属比赛ID，非比赛默认为0")
    private Long contestId;
    @Schema(description="比赛题目ID")
    private Long contestProblemId;
    @Schema(description="执行判题的机器标识")
    private String judgeServer;
    @Schema(description="提交用户IP")
    private String ip;
    @Schema(description="乐观锁版本号")
    @Version
    private Integer version;
    @Schema(description="vjudge判题在其它oj的提交id")
    private Long vjudgeSubmitId;
    @Schema(description="vjudge判题在其它oj的提交用户名")
    private String vjudgeUsername;
    @Schema(description="vjudge判题在其它oj的提交账号密码")
    private String vjudgePassword;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

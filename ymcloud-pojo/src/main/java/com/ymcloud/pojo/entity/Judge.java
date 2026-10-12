package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString(onlyExplicitlyIncluded = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Judge {

    @TableId(type = IdType.AUTO)
    @ToString.Include
    private Long id;

    @ToString.Include
    private Long problemId;

    /** 提交时固定的数据版本，历史记录为空。 */
    private Long testDataVersionId;

    /** 当前执行标识，历史记录为空。 */
    @ToString.Include
    private String executionId;

    private String problemTitle;

    private String displayId;

    private Long userId;

    private String username;

    private String language;

    /** 私有提交代码不参与诊断输出。 */
    private String code;

    private Integer codeLength;

    @ToString.Include
    private JudgeStatus status;

    /** ACM 模式为空，OI 模式为 0～100。 */
    private Integer score;

    /** CPU 时间，单位毫秒。 */
    private Long time;

    /** 峰值内存，单位 KiB。 */
    private Long memory;

    private String errorMessage;

    private Boolean share;

    private Integer isManual;

    private Long contestId;

    private Long contestProblemId;

    private String judgeServer;

    private String ip;

    @Version
    private Integer version;

    private Long vjudgeSubmitId;

    private String vjudgeUsername;

    /** 历史远程评测凭据不参与诊断输出。 */
    private String vjudgePassword;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

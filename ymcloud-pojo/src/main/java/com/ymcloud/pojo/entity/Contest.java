package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "比赛表")
public class Contest {
    @TableId(type = IdType.AUTO)
    @Schema(description="比赛ID")
    private Long id;
    @Schema(description="比赛创建者用户ID")
    private Long creatorId;
    @Schema(description="比赛创建者用户名或主办方")
    private String creatorUsername;
    @Schema(description="比赛Logo图片路径")
    private String image;
    @Schema(description="比赛标题")
    private String title;
    @Schema(description="比赛说明内容")
    private String description;
    @Schema(description="比赛规则说明（评测规则、计分机制）")
    private String rules;
    @Schema(description="来源比赛ID（0表示原创，其它为克隆赛ID）")
    private Long sourceId;
    @Schema(description="是否计入Rating积分（0=否，1=是）")
    private Integer rated;
    @Schema(description="赛制模式：0=ACM，1=OI，2=IOI")
    private Integer mode;
    @Schema(description="比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛")
    private Integer style;
    @Schema(description="访问权限：0=公开，1=私有（进入需密码），2=保护（提交需密码）")
    private Integer visibility;
    @Schema(description="比赛密码（私有/保护模式时使用）")
    private String password;
    @Schema(description="比赛开始时间")
    private LocalDateTime startTime;
    @Schema(description="比赛结束时间")
    private LocalDateTime endTime;
    @Schema(description="比赛时长（单位：分钟）")
    private Long duration;
    @Schema(description="是否开放排行榜（0=否，1=是）")
    private Integer openRank;
    @Schema(description="是否允许比赛结束后继续提交（0=否，1=是）")
    private Integer allowPostSubmit;
    @Schema(description="封榜时间（可选）")
    private LocalDateTime sealRankTime;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

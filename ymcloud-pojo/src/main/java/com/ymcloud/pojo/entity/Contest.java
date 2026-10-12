package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 比赛表。 */
@Data
public class Contest {

    /** 比赛ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 比赛创建者用户ID */
    private Long creatorId;

    /** 比赛创建者用户名或主办方 */
    private String creatorUsername;

    /** 比赛Logo图片路径 */
    private String image;

    /** 比赛标题 */
    private String title;

    /** 比赛说明内容 */
    private String description;

    /** 来源比赛ID（0表示原创，其它为克隆赛ID） */
    private Long sourceId;

    /** 是否计入Rating积分（0=否，1=是） */
    private Integer rated;

    /** 赛制模式：0=ACM，1=OI，2=IOI */
    private Integer mode;

    /** 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛 */
    private Integer style;

    /** 访问权限：0=公开，1=私有（进入需密码） */
    private Integer visibility;

    /** 比赛密码（私有模式时使用） */
    private String password;

    /** 比赛开始时间 */
    private LocalDateTime startTime;

    /** 比赛结束时间 */
    private LocalDateTime endTime;

    /** 比赛时长（单位：分钟） */
    private Long duration;

    /** 是否允许比赛结束后进行提交 */
    private Boolean allowEndSubmit;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}

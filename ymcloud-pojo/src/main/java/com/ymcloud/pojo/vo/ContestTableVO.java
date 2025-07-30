package com.ymcloud.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  00:34
 * @Description: 前端比赛列表表单
 */
@Data
public class ContestTableVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 比赛id
     */
    private Long id;
    /**
     * 比赛创建者用户名或主办方
     */
    private String creatorUsername;
    /**
     * 比赛Logo图片路径
     */
    private String image;
    /**
     * 比赛标题
     */
    private String title;

    /**
     * 比赛状态
     */
    private String status;

    /**
     * 来源比赛ID（0表示原创，其它为克隆赛ID）
     */
    private Long sourceId;
    /**
     * 是否计入Rating积分（0=否，1=是）
     */
    private Integer rated;
    /**
     * 赛制模式：0=ACM，1=OI，2=IOI
     */
    private Integer mode;
    /**
     * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
     */
    private Integer style;
    /**
     * 访问权限：0=公开，1=私有（进入需密码），2=保护（提交需密码）
     */
    private Integer visibility;
    /**
     * 比赛开始时间
     */
    private LocalDateTime startTime;
    /**
     * 比赛结束时间
     */
    private LocalDateTime endTime;
    /**
     * 比赛时长（单位：分钟）
     */
    private Long duration;
    /**
     * 参与人数
     */
    private int participantCount;
}

package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:10
 * @Description: 添加或更新比赛请求体
 */
@Data
public class ContestDTO {

    /**
     * 比赛标题
     */
    @NotBlank(message = "比赛标题不能为空")
    @Size(min = 1, max = 100, message = "比赛标题长度必须在1-100个字符之间")
    private String title;

    /** 公开封面图片地址；null 使用原有默认图。 */
    @Size(max = 512, message = "比赛封面地址不能超过512个字符")
    private String image;
    
    /**
     * 比赛说明内容
     */
    @NotBlank(message = "比赛说明不能为空")
    @Size(max = 10000, message = "比赛说明长度不能超过10000个字符")
    private String description;
    
    /**
     * 是否计入Rating积分（0=否，1=是）
     */
    @NotNull(message = "Rating积分设置不能为空")
    @Min(value = 0, message = "Rating积分设置参数错误")
    @Max(value = 1, message = "Rating积分设置参数错误")
    private Integer rated;
    
    /**
     * 赛制模式：0=ACM，1=OI，2=IOI
     */
    @NotNull(message = "赛制模式不能为空")
    @Min(value = 0, message = "赛制模式参数错误")
    @Max(value = 2, message = "赛制模式参数错误")
    private Integer mode;
    
    /**
     * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
     */
    @NotNull(message = "比赛类型不能为空")
    @Min(value = 0, message = "比赛类型参数错误")
    @Max(value = 4, message = "比赛类型参数错误")
    private Integer style;
    
    /**
     * 访问权限：0=公开，1=密码，2=私有
     */
    @NotNull(message = "访问权限不能为空")
    @Min(value = 0, message = "访问权限参数错误")
    @Max(value = 2, message = "访问权限参数错误")
    private Integer visibility;
    
    /**
     * 比赛密码（密码模式时使用）
     */
    @Size(min = 5, max = 20, message = "比赛密码长度必须在5-20个字符之间")
    private String password;
    
    /**
     * 比赛开始时间
     */
    @NotNull(message = "比赛开始时间不能为空")
    private LocalDateTime startTime;
    
    /**
     * 比赛结束时间
     */
    @NotNull(message = "比赛结束时间不能为空")
    private LocalDateTime endTime;
    
}

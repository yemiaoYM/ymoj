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
 * @CreateTime: 2026-11-01
 * @Description: 克隆比赛时显式指定新比赛的时间和访问凭据
 */
@Data
public class CloneContestDTO {

    /**
     * 新比赛标题
     */
    @NotBlank(message = "比赛标题不能为空")
    @Size(max = 100, message = "比赛标题不能超过100个字符")
    private String title;

    /**
     * 0公开、1密码、2私有
     */
    @NotNull(message = "访问权限不能为空")
    @Min(0)
    @Max(2)
    private Integer visibility;

    /**
     * 新密码，不继承源比赛密码
     */
    @Size(min = 5, max = 20, message = "比赛密码长度必须在5-20个字符之间")
    private String password;

    /**
     * 新比赛开始时间
     */
    @NotNull(message = "比赛开始时间不能为空")
    private LocalDateTime startTime;

    /**
     * 新比赛结束时间
     */
    @NotNull(message = "比赛结束时间不能为空")
    private LocalDateTime endTime;
}

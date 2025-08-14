package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: RegisterContestDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  13:00
 * @Description: 报名比赛请求体
 */
@Data
public class RegisterContestDTO {

    @NotNull(message = "比赛id不能为空")
    private Long contestId;

    @NotBlank(message = "比赛密码不能为空")
    private String password;
}

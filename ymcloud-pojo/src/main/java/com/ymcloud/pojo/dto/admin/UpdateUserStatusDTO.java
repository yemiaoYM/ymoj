package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: UpdateUserStatusDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  13:40
 * @Description: 更新用户状态请求体
 */
@Data
public class UpdateUserStatusDTO {
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long id;
    /**
     * 状态
     */
    @NotNull(message = "状态不能为空")
    private UserStatus status;
}

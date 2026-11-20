package com.ymcloud.pojo.query.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件分页使用固定排序，仅接受业务筛选字段
 */
@Data
public class AdminFilePageQuery {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    @NotNull(message = "每页大小不能为空")
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 50, message = "每页大小不能超过50")
    private Integer pageSize = 10;

    @Size(max = 255, message = "文件名不能超过255个字符")
    private String fileName;

    @Min(value = 0, message = "文件业务类型不合法")
    @Max(value = 5, message = "文件业务类型不合法")
    private Integer bizType;

    @Size(max = 64, message = "存储配置名不能超过64个字符")
    private String storageKey;
}

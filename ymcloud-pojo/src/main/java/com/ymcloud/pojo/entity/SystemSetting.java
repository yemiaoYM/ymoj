package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统配置。 */
@Data
public class SystemSetting {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String settingKey;

    /** 配置值 */
    private String settingValue;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}

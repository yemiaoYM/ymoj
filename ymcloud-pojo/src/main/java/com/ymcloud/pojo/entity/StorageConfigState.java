package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 单行默认配置与并发切换锁，多个 Web 实例共享同一状态。 */
@Getter
@Setter
public class StorageConfigState {

    @TableId(type = IdType.INPUT)
    private Integer id;
    private String activeKey;
    private Long revision;
    private LocalDateTime updatedTime;
}

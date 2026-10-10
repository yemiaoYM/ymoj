package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布后只读的题目测试数据版本，旧版本供已排队任务继续使用。
 */
@Data
public class TestDataVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long problemId;
    private String manifest;
    private String manifestSha256;
    private LocalDateTime createdTime;
}

package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 以题目主键定位的当前已发布测试清单
 */
@Data
public class ProblemCase {

    /**
     * 所属题目的内部主键，对应 problem.id
     */
    @TableId(value = "problem_id", type = IdType.INPUT)
    private Long problemId;

    /**
     * 当前已发布的有序测试点清单，以 JSON 保存
     */
    private String manifest;

    /**
     * 清单的乐观锁版本，首次发布为 1，每次更新递增
     */
    @Version
    private Long version;

    /**
     * 首次发布清单的时间，由数据库写入
     */
    private LocalDateTime createdTime;

    /**
     * 最近一次发布清单的时间，由数据库更新
     */
    private LocalDateTime updatedTime;
}

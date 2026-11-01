package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.entity.Contest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 已通过比赛管理归属校验的编辑视图，附带服务端结构锁定状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminContestVO extends Contest {

    /**
     * 已开始或存在提交时为 true，禁止改赛制、开始时间及题目结构
     */
    private Boolean structureLocked;
}

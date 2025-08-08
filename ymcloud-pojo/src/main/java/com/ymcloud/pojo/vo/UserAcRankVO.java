package com.ymcloud.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: UserAcRankVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-07  18:26
 * @Description: 用户ac题目数排名信息实体
 */
@Data
public class UserAcRankVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 用户名
     */
    private String username;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 用户头像
     */
    private String avatar;
    /**
     * ac题目数量
     */
    private Long count;
}

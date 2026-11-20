package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 对象存储配置分页筛选条件
 */
@Getter
@Setter
public class StorageConfigPageQuery extends PageQuery {

    /**
     * 按完整标识筛选存储配置
     */
    private String configKey;

    /**
     * 按名称模糊匹配存储桶
     */
    private String bucketName;

    /**
     * 默认配置筛选：Y=默认，N=非默认
     */
    private String status;
}

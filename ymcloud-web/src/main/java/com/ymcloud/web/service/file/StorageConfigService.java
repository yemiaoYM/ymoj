package com.ymcloud.web.service.file;

import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.query.admin.StorageConfigPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 管理数据库中的对象存储配置
 */
public interface StorageConfigService {

    /**
     * 分页查询存储配置
     * @param query 分页与配置筛选条件
     * @return 不含凭据的配置分页
     */
    PageVO<StorageConfigVO> queryPageList(StorageConfigPageQuery query);

    /**
     * 读取配置详情
     * @param id 配置主键
     * @return 不含凭据的配置详情
     */
    StorageConfigVO queryById(Long id);

    /**
     * 新增对象存储配置
     * @param dto 连接参数和凭据
     */
    void insert(StorageConfigDTO dto);

    /**
     * 按主键更新存储配置
     * @param dto 新配置，凭据留空时保留原值
     */
    void update(StorageConfigDTO dto);

    /**
     * 删除未被使用的配置
     * @param ids 配置主键集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 切换新上传使用的默认配置
     * @param id 配置主键
     */
    void changeStatus(Long id);
}

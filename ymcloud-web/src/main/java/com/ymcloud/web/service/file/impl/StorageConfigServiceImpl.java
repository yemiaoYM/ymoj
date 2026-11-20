package com.ymcloud.web.service.file.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.factory.StorageFactory;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.query.admin.StorageConfigPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.file.StorageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 对象存储配置的分页、维护及默认配置切换
 */
@Service
public class StorageConfigServiceImpl implements StorageConfigService {

    @Autowired
    private StorageConfigEntityService configs;

    @Autowired
    private FileEntityService files;

    @Autowired
    private StorageFactory storage;

    /**
     * 分页查询存储配置
     * @param query 分页与配置筛选条件
     * @return 不含凭据的配置分页
     */
    @Override
    public PageVO<StorageConfigVO> queryPageList(StorageConfigPageQuery query) {
        Page<StorageConfig> page = configs.lambdaQuery()
                .eq(StringUtils.hasText(query.getConfigKey()), StorageConfig::getConfigKey, query.getConfigKey())
                .like(StringUtils.hasText(query.getBucketName()), StorageConfig::getBucketName, query.getBucketName())
                .eq(StringUtils.hasText(query.getStatus()), StorageConfig::getStatus, query.getStatus())
                .orderByAsc(StorageConfig::getId)
                .page(Page.of(query.getPageNo(), query.getPageSize()));
        Set<String> used = referencedKeys(page.getRecords());
        return PageVO.of(page, config -> view(config, used));
    }

    /**
     * 读取配置详情
     * @param id 配置主键
     * @return 不含凭据的配置详情
     */
    @Override
    public StorageConfigVO queryById(Long id) {
        StorageConfig config = configs.getById(id);
        if (config == null) {
            throw new FileException("存储配置不存在", HttpStatus.NOT_FOUND);
        }
        return view(config, referencedKeys(List.of(config)));
    }

    /**
     * 新增对象存储配置
     * @param dto 连接参数和凭据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(StorageConfigDTO dto) {
        requireSuperAdmin();
        StorageConfig config = record(dto, null);
        config.setStatus("N");
        config.setCreatedBy(SecurityUtils.getUserId());
        if (!configs.save(config)) {
            throw new FileException("新增存储配置失败");
        }
    }

    /**
     * 按主键更新存储配置
     * @param dto 新配置，凭据留空时保留原值
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(StorageConfigDTO dto) {
        requireSuperAdmin();
        StorageConfig old = configs.lambdaQuery()
                .eq(StorageConfig::getId, dto.getId())
                .last("FOR UPDATE")
                .one();
        if (old == null) {
            throw new FileException("存储配置不存在", HttpStatus.NOT_FOUND);
        }
        StorageConfig config = record(dto, old);
        if (!referencedKeys(List.of(old)).isEmpty()
                && !(Objects.equals(old.getConfigKey(), config.getConfigKey())
                && Objects.equals(old.getEndpoint(), config.getEndpoint())
                && Objects.equals(old.getIsHttps(), config.getIsHttps())
                && Objects.equals(old.getBucketName(), config.getBucketName())
                && Objects.equals(old.getAccessPolicy(), config.getAccessPolicy()))) {
            throw new FileException("已有文件使用此配置，不能更改配置标识、存储位置或访问策略", HttpStatus.BAD_REQUEST);
        }
        if (!configs.updateById(config)) {
            throw new FileException("更新存储配置失败");
        }
        if (!old.getConfigKey().equals(config.getConfigKey())) {
            releaseAfterCommit(List.of(old.getConfigKey()));
        }
    }

    /**
     * 删除未被使用的配置
     * @param ids 配置主键集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        requireSuperAdmin();
        if (ids.isEmpty()) {
            throw new FileException("请选择存储配置", HttpStatus.BAD_REQUEST);
        }
        List<StorageConfig> selected = configs.lambdaQuery()
                .in(StorageConfig::getId, ids)
                .orderByAsc(StorageConfig::getId)
                .last("FOR UPDATE")
                .list();
        if (selected.size() != Set.copyOf(ids).size()) {
            throw new FileException("存储配置不存在", HttpStatus.NOT_FOUND);
        }
        if (selected.stream().anyMatch(c -> "Y".equals(c.getStatus()))
                || !referencedKeys(selected).isEmpty()) {
            throw new FileException("默认或已有文件引用的配置不能删除", HttpStatus.BAD_REQUEST);
        }
        if (!configs.removeByIds(ids)) {
            throw new FileException("删除存储配置失败");
        }
        releaseAfterCommit(selected.stream().map(StorageConfig::getConfigKey).toList());
    }

    /**
     * 删除或改名事务提交后释放本机工厂内的旧客户端
     * @param keys 已停用的配置标识
     */
    private void releaseAfterCommit(List<String> keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 配置变更生效后释放连接资源
             */
            @Override
            public void afterCommit() {
                keys.forEach(storage::remove);
            }
        });
    }

    /**
     * 切换新上传使用的默认配置
     * @param id 配置主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id) {
        requireSuperAdmin();
        List<StorageConfig> all = configs.lambdaQuery()
                .orderByAsc(StorageConfig::getId)
                .last("FOR UPDATE")
                .list();
        StorageConfig target = all.stream().filter(c -> c.getId().equals(id)).findFirst()
                .orElseThrow(() -> new FileException("存储配置不存在", HttpStatus.NOT_FOUND));
        if (!StringUtils.hasText(target.getAccessKey()) || !StringUtils.hasText(target.getSecretKey())) {
            throw new FileException("请先填写存储访问凭据", HttpStatus.BAD_REQUEST);
        }
        Long operator = SecurityUtils.getUserId();
        configs.lambdaUpdate()
                .eq(StorageConfig::getStatus, "Y")
                .set(StorageConfig::getStatus, "N")
                .set(StorageConfig::getUpdatedBy, operator)
                .update();
        if (!configs.lambdaUpdate()
                .eq(StorageConfig::getId, id)
                .set(StorageConfig::getStatus, "Y")
                .set(StorageConfig::getUpdatedBy, operator)
                .update()) {
            throw new FileException("切换默认存储失败");
        }
    }

    /**
     * 校验配置标识唯一性并准备本次保存的凭据
     * @param dto 管理员提交的配置
     * @param old 现有记录，新增时为空
     * @return 待持久化配置
     */
    private StorageConfig record(StorageConfigDTO dto, StorageConfig old) {
        String key = dto.getConfigKey().trim();
        if (configs.lambdaQuery()
                .eq(StorageConfig::getConfigKey, key)
                .ne(old != null, StorageConfig::getId, dto.getId())
                .exists()) {
            throw new FileException("存储配置标识已存在", HttpStatus.CONFLICT);
        }
        StorageConfig config = BeanUtil.copyProperties(dto, StorageConfig.class);
        config.setConfigKey(key);
        config.setId(old == null ? null : old.getId());
        config.setEndpoint(dto.getEndpoint().trim());
        config.setPrefix(Objects.toString(dto.getPrefix(), ""));
        config.setDomain(Objects.toString(dto.getDomain(), ""));
        config.setRemark(Objects.toString(dto.getRemark(), ""));
        config.setRegion(Objects.toString(dto.getRegion(), ""));
        if (old != null && !StringUtils.hasText(dto.getAccessKey()) && !StringUtils.hasText(dto.getSecretKey())) {
            config.setAccessKey(old.getAccessKey());
            config.setSecretKey(old.getSecretKey());
        }
        if (!StringUtils.hasText(config.getAccessKey()) || !StringUtils.hasText(config.getSecretKey())) {
            throw new FileException("请同时填写 Access Key 和 Secret Key", HttpStatus.BAD_REQUEST);
        }
        config.setUpdatedBy(SecurityUtils.getUserId());
        return config;
    }

    /**
     * 批量读取仍有文件引用的配置标识
     * @param selected 当前页或待修改配置
     * @return 已引用的配置标识
     */
    private Set<String> referencedKeys(Collection<StorageConfig> selected) {
        if (selected.isEmpty()) {
            return Set.of();
        }
        return files.lambdaQuery()
                .select(FileInfo::getService)
                .in(FileInfo::getService, selected.stream().map(StorageConfig::getConfigKey).toList())
                .groupBy(FileInfo::getService)
                .list().stream().map(FileInfo::getService).collect(Collectors.toSet());
    }

    /**
     * 返回管理视图和可操作状态
     * @param config 配置记录
     * @param used 已引用的配置标识
     * @return 脱敏配置视图
     */
    private StorageConfigVO view(StorageConfig config, Set<String> used) {
        StorageConfigVO view = BeanUtil.copyProperties(config, StorageConfigVO.class);
        view.setId(config.getId().toString());
        view.setReferenced(used.contains(config.getConfigKey()));
        view.setCanDelete(!"Y".equals(view.getStatus()) && !view.getReferenced());
        view.setCredentialConfigured(StringUtils.hasText(config.getAccessKey()) && StringUtils.hasText(config.getSecretKey()));
        return view;
    }

    /**
     * 存储凭据维护和默认配置切换仅允许超级管理员执行
     */
    private void requireSuperAdmin() {
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getAuthentication())) {
            throw new AccessDeniedException("仅超级管理员可以维护存储配置");
        }
    }
}

package com.ymcloud.web.service.admin.impl;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.config.StorageConfigValidation;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.core.storage.security.StorageCredentialCipher;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import com.ymcloud.pojo.enums.StorageDriverType;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import com.ymcloud.web.service.file.StorageConfigCatalog;
import com.ymcloud.web.service.file.StorageConnectionTester;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 命名配置管理，写入与探测仅向 ID 为 1 的可用超级管理员开放
 */
@Service
public class AdminStorageConfigServiceImpl implements AdminStorageConfigService {

    @Autowired
    private StorageProperties properties;

    @Autowired
    private StorageConfigCatalog catalog;

    @Autowired
    private StorageCredentialCipher cipher;

    @Autowired
    private StorageConnectionTester tester;

    /**
     * 合并静态与动态配置并返回脱敏管理视图，不解密凭据
     * @return 按配置标识排序的管理视图
     */
    @Override
    public List<StorageConfigVO> list() {
        String active = catalog.active();
        Map<String, StorageConfigVO> result = new LinkedHashMap<>();
        StorageProperties.Profile local = new StorageProperties.Profile();
        local.setDriverType(StorageDriverType.LOCAL);
        local.setPublicBucket("public");
        local.setPrivateBucket("private");
        result.put("local", view("local", "本地文件", "local", local, null, "config", active, true));
        properties.getProfiles().forEach((key, profile) -> result.put(key,
                view(key, key, profile.getProvider(), profile, null, "config", active,
                        profile.getDriverType() == StorageDriverType.LOCAL || credentialsPresent(profile))));
        for (StorageConfig config : catalog.list()) {
            StorageProperties.Profile profile = new StorageProperties.Profile();
            profile.setDriverType(config.getDriverType());
            profile.setEndpoint(config.getEndpoint());
            profile.setRegion(config.getRegion());
            profile.setPublicBucket(config.getPublicBucket());
            profile.setPrivateBucket(config.getPrivateBucket());
            profile.setPathStyle(Boolean.TRUE.equals(config.getPathStyle()));
            result.put(config.getId(), view(config.getId(), config.getName(), config.getProvider(), profile,
                    config.getRemark(), "database", active,
                    present(config.getEncryptedAccessKey()) && present(config.getEncryptedSecretKey())));
        }
        List<StorageConfigVO> list = new ArrayList<>(result.values());
        list.sort(Comparator.comparing(StorageConfigVO::getKey));
        return list;
    }

    /**
     * 由超级管理员创建配置，存在历史引用时先只读核验全部对象
     * @param dto 配置参数与新凭据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(StorageConfigDTO dto) {
        requireSuperAdmin();
        catalog.lockForWrite();
        validateDto(dto, dto == null ? null : dto.getKey());
        if (catalog.find(dto.getKey()) != null || properties.getProfiles().keySet().stream()
                .anyMatch(key -> key.equalsIgnoreCase(dto.getKey()))) {
            throw new FileException("存储配置名已存在，请编辑已有配置");
        }
        List<File> references = catalog.referencedFiles(dto.getKey());
        if (!references.isEmpty()) {
            // 配置名须与历史记录完全一致，不能借数据库不区分大小写的比较恢复别名。
            if (references.stream().anyMatch(file -> file == null || !dto.getKey().equals(file.getStorageProfile()))) {
                throw new FileException("已有文件的存储配置名不一致，请使用原配置名恢复", HttpStatus.BAD_REQUEST);
            }
            tester.verifyReferencedFiles(requestProfile(dto, null), references);
        }
        persist(dto, null, null, false);
    }

    /**
     * 由超级管理员更新配置，禁止改变已被文件引用的物理位置
     * @param key 原配置标识
     * @param dto 待更新参数，空凭据表示保留原凭据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String key, StorageConfigDTO dto) {
        requireSuperAdmin();
        catalog.lockForWrite();
        validateDto(dto, key);
        StorageConfig existing = catalog.find(key);
        StorageProperties.Profile old = existing == null ? yamlProfile(key) : catalog.profile(existing);
        if (old.getDriverType() == StorageDriverType.LOCAL) {
            throw new FileException("本地存储配置只读");
        }
        persist(dto, existing, old, Objects.equals(catalog.active(), key));
    }

    /**
     * 由超级管理员删除非默认且无文件引用的动态配置
     * @param key 待删除配置标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String key) {
        requireSuperAdmin();
        catalog.lockForWrite();
        StorageConfigValidation.validateKey(key);
        if ("local".equals(key) || Objects.equals(catalog.active(), key) || catalog.referenced(key)) {
            throw new FileException("本地、正在使用或已被文件引用的配置不能删除");
        }
        if (catalog.find(key) == null || properties.getProfiles().containsKey(key)) {
            throw new FileException("配置文件中的配置不能在后台删除");
        }
        catalog.remove(key);
    }

    /**
     * 验证双桶读写删除能力，并登记当前配置版本的成功测试状态
     * @param key 待测试配置标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void test(String key) {
        requireSuperAdmin();
        StorageConfigValidation.validateKey(key);
        if ("local".equals(key)) {
            tester.test(yamlProfile(key));
            return;
        }
        catalog.lockForWrite();
        // 首次读取前取独占锁，避免两个连接测试从共享锁升级时互相等待。
        StorageConfig config = catalog.find(key);
        StorageProperties.Profile profile = config == null ? yamlProfile(key) : catalog.profile(config);
        tester.test(profile);
        if (profile.getDriverType() == StorageDriverType.LOCAL) {
            return;
        }
        if (config == null) {
            StorageConfigDTO dto = fromYaml(key, profile);
            config = record(dto, profile, 1L);
            config.setTestedRevision(config.getRevision());
            config.setTestedAt(LocalDateTime.now());
            catalog.save(config, false);
        } else {
            config.setTestedRevision(config.getRevision());
            config.setTestedAt(LocalDateTime.now());
            config.setUpdatedTime(LocalDateTime.now());
            catalog.save(config, true);
        }
    }

    /**
     * 由超级管理员启用已通过测试的配置，本地配置即时验证
     * @param key 将用于新上传的默认配置标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(String key) {
        requireSuperAdmin();
        StorageConfigValidation.validateKey(key);
        StorageConfigState state = catalog.lockForWrite();
        StorageConfig config = catalog.find(key);
        if ("local".equals(key) && config == null) {
            tester.test(yamlProfile(key));
        } else if (config == null || !Objects.equals(config.getRevision(), config.getTestedRevision())) {
            throw new FileException("请先成功测试当前版本的存储配置");
        } else {
            // 默认选择只更新元数据，仍需确认动态配置的凭据能够解密。
            catalog.profile(config);
        }
        catalog.activate(state, key);
    }

    /**
     * 组装并保存配置，默认配置的修改须先重新通过连接测试
     * @param dto 已校验的管理请求
     * @param existing 现有动态记录，新建或静态转动态时为空
     * @param old 原客户端参数，新建时为空
     * @param active 本次保存的是否为当前默认配置
     */
    private void persist(StorageConfigDTO dto, StorageConfig existing, StorageProperties.Profile old, boolean active) {
        StorageProperties.Profile profile = requestProfile(dto, old);
        StorageConfigValidation.validate(profile, false);
        if (old != null && catalog.referenced(dto.getKey()) && !sameLocation(old, profile)) {
            throw new FileException("已引用配置的驱动与存储位置不可修改", HttpStatus.BAD_REQUEST);
        }
        if (active) {
            tester.test(profile);
        }
        StorageConfig config = record(dto, profile, existing == null ? 1L : existing.getRevision() + 1);
        if (existing != null) {
            config.setCreatedTime(existing.getCreatedTime());
        }
        if (active) {
            config.setTestedRevision(config.getRevision());
            config.setTestedAt(LocalDateTime.now());
        }
        catalog.save(config, existing != null);
    }

    /**
     * 加密凭据并构建指定版本的动态配置记录
     * @param dto 已校验的管理请求
     * @param profile 已组装的客户端参数
     * @param revision 新配置版本号
     * @return 含密文和时间信息的待保存记录
     */
    private StorageConfig record(StorageConfigDTO dto, StorageProperties.Profile profile, long revision) {
        StorageConfig config = new StorageConfig();
        config.setId(dto.getKey());
        config.setName(dto.getName().trim());
        config.setProvider(dto.getProvider());
        config.setDriverType(profile.getDriverType());
        config.setEndpoint(profile.getEndpoint());
        config.setRegion(profile.getRegion());
        config.setPublicBucket(profile.getPublicBucket());
        config.setPrivateBucket(profile.getPrivateBucket());
        config.setPathStyle(profile.isPathStyle());
        config.setEncryptedAccessKey(cipher.encrypt(dto.getKey(), "accessKey", profile.getAccessKey()));
        config.setEncryptedSecretKey(cipher.encrypt(dto.getKey(), "secretKey", profile.getSecretKey()));
        config.setRemark(dto.getRemark());
        config.setRevision(revision);
        config.setCreatedTime(LocalDateTime.now());
        config.setUpdatedTime(config.getCreatedTime());
        return config;
    }

    /**
     * 组装请求中的连接参数，编辑时复用未重新填写的原凭据
     * @param dto 已校验的管理请求
     * @param old 原客户端参数，新建时为空
     * @return 仅供内部校验和客户端创建的参数
     */
    private StorageProperties.Profile requestProfile(StorageConfigDTO dto, StorageProperties.Profile old) {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setDriverType(dto.getDriverType());
        profile.setProvider(dto.getProvider());
        profile.setEndpoint(dto.getEndpoint().trim());
        profile.setRegion(dto.getRegion().trim());
        profile.setPublicBucket(dto.getPublicBucket().trim());
        profile.setPrivateBucket(dto.getPrivateBucket().trim());
        profile.setPathStyle(dto.getPathStyle());
        profile.setAccessKey(present(dto.getAccessKey()) ? dto.getAccessKey() : old == null ? null : old.getAccessKey());
        profile.setSecretKey(present(dto.getSecretKey()) ? dto.getSecretKey() : old == null ? null : old.getSecretKey());
        return profile;
    }

    /**
     * 校验存储配置入口，驱动必须显式指定且本地配置只读
     * @param dto 待保存的配置及凭据
     * @param key 新建或编辑的配置标识
     */
    private void validateDto(StorageConfigDTO dto, String key) {
        StorageConfigValidation.validateKey(key);
        if (dto == null || (dto.getKey() != null && !key.equals(dto.getKey())) || "local".equals(key)
                || dto.getDriverType() == null || dto.getDriverType() == StorageDriverType.LOCAL
                || (dto.getProvider() != null && !bounded(dto.getProvider(), 100))
                || !bounded(dto.getName(), 100) || !bounded(dto.getEndpoint(), 512)
                || !bounded(dto.getRegion(), 64) || !bounded(dto.getPublicBucket(), 128)
                || !bounded(dto.getPrivateBucket(), 128) || dto.getPathStyle() == null
                || (dto.getRemark() != null && dto.getRemark().length() > 500)) {
            throw new FileException("存储配置参数不完整或不合法");
        }
        dto.setKey(key);
    }

    /**
     * 读取静态配置，未显式配置的 local 使用内置本地参数
     * @param key 配置标识
     * @return 静态客户端参数
     * @throws FileException 指定的非默认配置不存在
     */
    private StorageProperties.Profile yamlProfile(String key) {
        if ("local".equals(key) && !properties.getProfiles().containsKey(key)) {
            StorageProperties.Profile profile = new StorageProperties.Profile();
            profile.setDriverType(StorageDriverType.LOCAL);
            return profile;
        }
        StorageProperties.Profile profile = properties.getProfiles().get(key);
        if (profile == null) {
            throw new FileException("存储配置不存在", HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    /**
     * 为静态配置首次登记测试状态构建名称与服务商信息
     * @param key 静态配置标识
     * @param profile 静态客户端参数
     * @return 用于构建动态记录的基础管理信息
     */
    private StorageConfigDTO fromYaml(String key, StorageProperties.Profile profile) {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey(key);
        dto.setName(key);
        dto.setProvider(profile.getProvider());
        dto.setDriverType(profile.getDriverType());
        return dto;
    }

    /**
     * 构建不含凭据的管理视图，并标记引用及可删除状态
     * @param key 配置标识
     * @param name 展示名称
     * @param provider 服务商标识
     * @param profile 客户端参数，仅提取非敏感字段
     * @param remark 备注，可为空
     * @param source 配置来源标识
     * @param active 当前默认配置标识
     * @param credentialConfigured 是否已有完整凭据
     * @return 可返回管理端的脱敏视图
     */
    private StorageConfigVO view(String key, String name, String provider, StorageProperties.Profile profile,
                                 String remark, String source, String active, boolean credentialConfigured) {
        StorageConfigVO view = new StorageConfigVO();
        view.setKey(key);
        view.setName(name);
        view.setProvider(provider);
        view.setDriverType(profile.getDriverType());
        view.setEndpoint(profile.getEndpoint());
        view.setRegion(profile.getRegion());
        view.setPublicBucket(profile.getDriverType() == StorageDriverType.LOCAL ? "public" : profile.getPublicBucket());
        view.setPrivateBucket(profile.getDriverType() == StorageDriverType.LOCAL ? "private" : profile.getPrivateBucket());
        view.setPathStyle(profile.isPathStyle());
        view.setRemark(remark);
        view.setSource(source);
        view.setActive(key.equals(active));
        view.setReferenced(catalog.referenced(key));
        view.setCredentialConfigured(credentialConfigured);
        view.setCanDelete(profile.getDriverType() != StorageDriverType.LOCAL && "database".equals(source) && !key.equals(active)
                && !Boolean.TRUE.equals(view.getReferenced()) && !properties.getProfiles().containsKey(key));
        return view;
    }

    /**
     * 比较影响对象定位的参数，凭据轮换不视为位置变化
     * @param first 原客户端参数
     * @param second 新客户端参数
     * @return 驱动、端点、区域、双桶与寻址方式是否相同
     */
    private boolean sameLocation(StorageProperties.Profile first, StorageProperties.Profile second) {
        return Objects.equals(first.getDriverType(), second.getDriverType()) && Objects.equals(first.getEndpoint(), second.getEndpoint())
                && Objects.equals(first.getRegion(), second.getRegion())
                && Objects.equals(first.getPublicBucket(), second.getPublicBucket())
                && Objects.equals(first.getPrivateBucket(), second.getPrivateBucket())
                && first.isPathStyle() == second.isPathStyle();
    }

    /**
     * 检查配置文本非空、无控制字符且未超过长度限制
     * @param value 待检查文本，可为空
     * @param max 最大字符数
     * @return 是否满足文本约束
     */
    private boolean bounded(String value, int max) {
        return present(value) && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    /**
     * 判断配置文本是否包含非空白内容
     * @param value 待检查文本，可为空
     * @return 是否存在有效文本
     */
    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 判断静态配置是否同时提供访问标识和密钥
     * @param profile 静态客户端参数
     * @return 两个凭据字段是否均非空白
     */
    private boolean credentialsPresent(StorageProperties.Profile profile) {
        return present(profile.getAccessKey()) && present(profile.getSecretKey());
    }

    /**
     * 限制配置写入和探测仅由可用的超级管理员执行
     * @throws AccessDeniedException 当前用户不是超级管理员
     */
    private void requireSuperAdmin() {
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getAuthentication())) {
            throw new AccessDeniedException("仅超级管理员可以修改或测试存储配置");
        }
    }
}

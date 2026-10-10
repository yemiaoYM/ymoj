package com.ymcloud.web.service.admin.impl;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StorageConfigCatalog;
import com.ymcloud.core.storage.StorageConfigValidation;
import com.ymcloud.core.storage.StorageConnectionTester;
import com.ymcloud.core.storage.StorageCredentialCipher;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;
import com.ymcloud.pojo.entity.StorageConfigState;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
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
import java.util.Set;

/** 命名配置管理。写入与探测仅向 ID 为 1 的可用超级管理员开放。 */
@Service
public class AdminStorageConfigServiceImpl implements AdminStorageConfigService {

    private final StorageProperties properties;
    private final StorageConfigCatalog catalog;
    private final StorageCredentialCipher cipher;
    private final StorageConnectionTester tester;

    public AdminStorageConfigServiceImpl(StorageProperties properties,
                                         StorageConfigCatalog catalog, StorageCredentialCipher cipher,
                                         StorageConnectionTester tester) {
        this.properties = properties;
        this.catalog = catalog;
        this.cipher = cipher;
        this.tester = tester;
    }

    @Override
    public List<StorageConfigVO> list() {
        String active = catalog.active();
        Map<String, StorageConfigVO> result = new LinkedHashMap<>();
        StorageProperties.Profile local = new StorageProperties.Profile();
        local.setPublicBucket("public");
        local.setPrivateBucket("private");
        result.put("local", view("local", "本地文件", "local", local, null, "config", active, true));
        properties.getProfiles().forEach((key, profile) -> result.put(key,
                view(key, key, yamlProvider(key, profile), profile, null, "config", active,
                        "local".equals(profile.getType()) || credentialsPresent(profile))));
        for (StorageConfig config : catalog.list()) {
            StorageProperties.Profile profile = new StorageProperties.Profile();
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
                throw new FileException("已有文件的存储配置名不一致，请使用原配置名恢复", 400);
            }
            tester.verifyReferencedFiles(requestProfile(dto, null), references);
        }
        persist(dto, null, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String key, StorageConfigDTO dto) {
        requireSuperAdmin();
        catalog.lockForWrite();
        validateDto(dto, key);
        StorageConfig existing = catalog.find(key);
        StorageProperties.Profile old = existing == null ? yamlProfile(key) : catalog.profile(existing);
        if ("local".equals(old.getType())) {
            throw new FileException("本地存储配置只读");
        }
        persist(dto, existing, old, Objects.equals(catalog.active(), key));
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void test(String key) {
        requireSuperAdmin();
        StorageConfigValidation.validateKey(key);
        // 缺少主密钥时仍可探测 YAML，不能将配置或测试结果写入数据库。
        if (!present(properties.getConfigEncryptionKey())) {
            StorageConfig config = catalog.find(key);
            if (config != null) {
                throw new FileException("测试动态存储配置需要主密钥");
            }
            tester.test(yamlProfile(key));
            return;
        }
        catalog.lockForWrite();
        // 首次读取前取独占锁，避免两个连接测试从共享锁升级时互相等待。
        StorageConfig config = catalog.find(key);
        StorageProperties.Profile profile = config == null ? yamlProfile(key) : catalog.profile(config);
        tester.test(profile);
        if ("local".equals(profile.getType())) {
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
        }
        catalog.activate(state, key);
    }

    private void persist(StorageConfigDTO dto, StorageConfig existing, StorageProperties.Profile old, boolean active) {
        StorageProperties.Profile profile = requestProfile(dto, old);
        StorageConfigValidation.validate(profile, false);
        String oldProvider = existing == null && old != null ? yamlProvider(dto.getKey(), old)
                : existing == null ? null : existing.getProvider();
        if (old != null && catalog.referenced(dto.getKey())
                && (!sameLocation(old, profile) || !Objects.equals(oldProvider, dto.getProvider()))) {
            throw new FileException("配置已被文件引用，驱动、地址、区域、存储桶及路径风格不能修改；可轮换凭据");
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

    private StorageConfig record(StorageConfigDTO dto, StorageProperties.Profile profile, long revision) {
        StorageConfig config = new StorageConfig();
        config.setId(dto.getKey());
        config.setName(dto.getName().trim());
        config.setProvider(dto.getProvider());
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

    private StorageProperties.Profile requestProfile(StorageConfigDTO dto, StorageProperties.Profile old) {
        StorageProperties.Profile profile = new StorageProperties.Profile();
        profile.setType("aliyun".equals(dto.getProvider()) ? "oss" : "s3");
        profile.setEndpoint(dto.getEndpoint().trim());
        profile.setRegion(dto.getRegion().trim());
        profile.setPublicBucket(dto.getPublicBucket().trim());
        profile.setPrivateBucket(dto.getPrivateBucket().trim());
        profile.setPathStyle(dto.getPathStyle());
        profile.setAccessKey(present(dto.getAccessKey()) ? dto.getAccessKey() : old == null ? null : old.getAccessKey());
        profile.setSecretKey(present(dto.getSecretKey()) ? dto.getSecretKey() : old == null ? null : old.getSecretKey());
        return profile;
    }

    private void validateDto(StorageConfigDTO dto, String key) {
        StorageConfigValidation.validateKey(key);
        if (dto == null || (dto.getKey() != null && !key.equals(dto.getKey())) || "local".equals(key)
                || !Set.of("seaweedfs", "minio", "aliyun", "s3").contains(String.valueOf(dto.getProvider()))
                || !bounded(dto.getName(), 100) || !bounded(dto.getEndpoint(), 512)
                || !bounded(dto.getRegion(), 64) || !bounded(dto.getPublicBucket(), 128)
                || !bounded(dto.getPrivateBucket(), 128) || dto.getPathStyle() == null
                || (dto.getRemark() != null && dto.getRemark().length() > 500)) {
            throw new FileException("存储配置参数不完整或不合法，本地配置只读且配置名不可修改");
        }
        dto.setKey(key);
    }

    private StorageProperties.Profile yamlProfile(String key) {
        if ("local".equals(key) && !properties.getProfiles().containsKey(key)) {
            return new StorageProperties.Profile();
        }
        StorageProperties.Profile profile = properties.getProfiles().get(key);
        if (profile == null) {
            throw new FileException("存储配置不存在", 404);
        }
        return profile;
    }

    private StorageConfigDTO fromYaml(String key, StorageProperties.Profile profile) {
        StorageConfigDTO dto = new StorageConfigDTO();
        dto.setKey(key);
        dto.setName(key);
        dto.setProvider(yamlProvider(key, profile));
        return dto;
    }

    private String yamlProvider(String key, StorageProperties.Profile profile) {
        if ("local".equals(profile.getType())) {
            return "local";
        }
        if ("oss".equals(profile.getType())) {
            return "aliyun";
        }
        return Set.of("seaweedfs", "minio").contains(key) ? key : "s3";
    }

    private StorageConfigVO view(String key, String name, String provider, StorageProperties.Profile profile,
                                 String remark, String source, String active, boolean credentialConfigured) {
        StorageConfigVO view = new StorageConfigVO();
        view.setKey(key);
        view.setName(name);
        view.setProvider(provider);
        view.setEndpoint(profile.getEndpoint());
        view.setRegion(profile.getRegion());
        view.setPublicBucket("local".equals(provider) ? "public" : profile.getPublicBucket());
        view.setPrivateBucket("local".equals(provider) ? "private" : profile.getPrivateBucket());
        view.setPathStyle(profile.isPathStyle());
        view.setRemark(remark);
        view.setSource(source);
        view.setActive(key.equals(active));
        view.setReferenced(catalog.referenced(key));
        view.setCredentialConfigured(credentialConfigured);
        view.setCanDelete(!"local".equals(provider) && "database".equals(source) && !key.equals(active)
                && !Boolean.TRUE.equals(view.getReferenced()) && !properties.getProfiles().containsKey(key));
        return view;
    }

    private boolean sameLocation(StorageProperties.Profile first, StorageProperties.Profile second) {
        return Objects.equals(first.getType(), second.getType()) && Objects.equals(first.getEndpoint(), second.getEndpoint())
                && Objects.equals(first.getRegion(), second.getRegion())
                && Objects.equals(first.getPublicBucket(), second.getPublicBucket())
                && Objects.equals(first.getPrivateBucket(), second.getPrivateBucket())
                && first.isPathStyle() == second.isPathStyle();
    }

    private boolean bounded(String value, int max) {
        return present(value) && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private boolean credentialsPresent(StorageProperties.Profile profile) {
        return present(profile.getAccessKey()) && present(profile.getSecretKey());
    }

    private void requireSuperAdmin() {
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getAuthentication())) {
            throw new AccessDeniedException("仅超级管理员可以修改或测试存储配置");
        }
    }
}

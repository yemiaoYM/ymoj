package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.constant.SystemSettingConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.core.web.mapper.SystemSettingMapper;
import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.web.service.common.impl.SystemSettingServiceImpl;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-10
 * @Description: 系统配置读写和备案输入边界测试，不连接外部数据库
 */
@ExtendWith(MockitoExtension.class)
public class SystemSettingServiceTest {

    @Mock
    private SystemSettingEntityService systemSettingEntityService;

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private SystemSettingServiceImpl systemSettingService;

    /** 初始化 MyBatis 映射，使测试使用真实条件构造器。 */
    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), SystemSetting.class);
        lenient().when(systemSettingEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(systemSettingMapper, SystemSetting.class));
        lenient().when(systemSettingEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(systemSettingMapper));
    }

    /** 公开配置每次读取最新注册开关，未配置备案时默认关闭。 */
    @Test
    void readsLatestRegistrationSettingWithoutCache() {
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue("true");
        when(systemSettingMapper.selectOne(any(Wrapper.class))).thenReturn(setting);
        assertTrue(systemSettingService.getSystemSetting().getRegisterEnabled());
        setting.setSettingValue("false");
        assertFalse(systemSettingService.getSystemSetting().getRegisterEnabled());
        verify(systemSettingMapper, times(2)).selectOne(any(Wrapper.class));
    }

    /** 旧数据库无需迁移即可读取，且不会显示硬编码备案信息。 */
    @Test
    void missingFilingSettingsDefaultToDisabledAndEmpty() {
        when(systemSettingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("register_enabled", "true"));
        var result = systemSettingService.getSystemSetting();
        assertFalse(result.getFilingEnabled());
        assertEquals("", result.getIcpFilingNumber());
        assertEquals("", result.getPoliceFilingNumber());
    }

    /** 读取已保存的备案号，关闭后仍保留值，避免切换时丢失配置。 */
    @Test
    void readsFilingValuesAndLatestSwitch() {
        when(systemSettingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("register_enabled", "true"));
        SystemSetting enabled = setting(SystemSettingConstants.FILING_ENABLED, "true");
        when(systemSettingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(enabled,
                setting(SystemSettingConstants.ICP_FILING_NUMBER, "测试ICP备123号"),
                setting(SystemSettingConstants.POLICE_FILING_NUMBER, "测试公安备123456号")));
        var result = systemSettingService.getSystemSetting();
        assertTrue(result.getFilingEnabled());
        assertEquals("测试ICP备123号", result.getIcpFilingNumber());
        assertEquals("测试公安备123456号", result.getPoliceFilingNumber());
        enabled.setSettingValue("false");
        result = systemSettingService.getSystemSetting();
        assertFalse(result.getFilingEnabled());
        assertEquals("测试公安备123456号", result.getPoliceFilingNumber());
    }

    /** 注册配置缺失或损坏不能意外开放注册。 */
    @Test
    void missingOrInvalidSettingDoesNotEnableRegistration() {
        assertThrows(ServiceException.class, () -> systemSettingService.isRegisterEnabled());
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue("invalid");
        when(systemSettingMapper.selectOne(any(Wrapper.class))).thenReturn(setting);
        assertThrows(ServiceException.class, () -> systemSettingService.isRegisterEnabled());
    }

    /** 缺失必需开关时不发生写入。 */
    @Test
    void missingSwitchIsRejectedBeforeWriting() {
        assertThrows(ServiceException.class, () -> systemSettingService.updateSystemSetting(new SystemSettingDTO()));
        verifyNoInteractions(systemSettingEntityService);
    }

    /** 注册开关的两种取值均可保存，注册配置更新失败不能伪造成功。 */
    @Test
    void savesBothSwitchStatesAndReportsWriteFailure() {
        SystemSettingDTO settingDTO = validRequest();
        when(systemSettingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1, 1, 0);
        when(systemSettingEntityService.save(any(SystemSetting.class))).thenReturn(true);
        settingDTO.setRegisterEnabled(false);
        assertDoesNotThrow(() -> systemSettingService.updateSystemSetting(settingDTO));
        settingDTO.setRegisterEnabled(true);
        assertDoesNotThrow(() -> systemSettingService.updateSystemSetting(settingDTO));
        assertThrows(ServiceException.class, () -> systemSettingService.updateSystemSetting(settingDTO));
    }

    /** 首次保存旧库时只补齐备案键，去除空白，关闭时不丢弃原值。 */
    @Test
    void createsMissingFilingSettingsOnFirstSave() {
        when(systemSettingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(systemSettingEntityService.save(any(SystemSetting.class))).thenReturn(true);
        SystemSettingDTO request = validRequest();
        request.setIcpFilingNumber("  测试ICP备123号  ");
        request.setPoliceFilingNumber("  测试公安备123456号  ");
        systemSettingService.updateSystemSetting(request);
        ArgumentCaptor<SystemSetting> inserted = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingEntityService, times(3)).save(inserted.capture());
        assertEquals(List.of(SystemSettingConstants.FILING_ENABLED, SystemSettingConstants.ICP_FILING_NUMBER,
                SystemSettingConstants.POLICE_FILING_NUMBER), inserted.getAllValues().stream()
                .map(SystemSetting::getSettingKey).toList());
        assertEquals(List.of("false", "测试ICP备123号", "测试公安备123456号"), inserted.getAllValues().stream()
                .map(SystemSetting::getSettingValue).toList());
    }

    /** 已存在的备案配置使用更新，不重复插入唯一键。 */
    @Test
    void updatesExistingFilingSettings() {
        when(systemSettingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(systemSettingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("existing", "old"));
        systemSettingService.updateSystemSetting(validRequest());
        verify(systemSettingMapper, times(4)).update(isNull(), any(Wrapper.class));
        verify(systemSettingEntityService, never()).save(any(SystemSetting.class));
    }

    /** 任一备案键保存失败必须抛出异常，供事务边界回滚，不能返回成功。 */
    @Test
    void filingWriteFailureIsNotIgnored() {
        when(systemSettingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertThrows(ServiceException.class, () -> systemSettingService.updateSystemSetting(validRequest()));
    }

    /** Bean Validation 拒绝空白开启、超长文本和不含有效数字段的公安备案号。 */
    @Test
    void validatesFilingInputAtHttpBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            SystemSettingDTO request = validRequest();
            assertTrue(validator.validate(request).isEmpty());
            request.setFilingEnabled(true);
            request.setIcpFilingNumber("  ");
            assertFalse(validator.validate(request).isEmpty());
            request.setIcpFilingNumber("测试ICP备123号");
            assertTrue(validator.validate(request).isEmpty());
            request.setIcpFilingNumber("x".repeat(101));
            assertFalse(validator.validate(request).isEmpty());
            request.setIcpFilingNumber("");
            request.setPoliceFilingNumber("测试公安备123456号");
            assertTrue(validator.validate(request).isEmpty());
            request.setPoliceFilingNumber("没有数字编号");
            assertFalse(validator.validate(request).isEmpty());
            request.setPoliceFilingNumber("公安备123和456号");
            assertFalse(validator.validate(request).isEmpty());
            request.setFilingEnabled(null);
            assertFalse(validator.validate(request).isEmpty());
        }
    }

    /**
     * 构造关闭备案栏的完整合法请求。
     * @return 所有必填字段均存在的请求
     */
    private SystemSettingDTO validRequest() {
        SystemSettingDTO request = new SystemSettingDTO();
        request.setRegisterEnabled(true);
        request.setFilingEnabled(false);
        request.setIcpFilingNumber("");
        request.setPoliceFilingNumber("");
        return request;
    }

    /**
     * 构造数据库配置记录。
     * @param key 配置键
     * @param value 配置值
     * @return 不含数据库生成字段的配置记录
     */
    private SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}

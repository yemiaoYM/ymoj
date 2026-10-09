package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.web.service.common.impl.SystemSettingServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 系统配置测试，不连接外部数据库
 */
@ExtendWith(MockitoExtension.class)
public class SystemSettingServiceTest {

    @Mock
    private SystemSettingEntityService systemSettingEntityService;

    @InjectMocks
    private SystemSettingServiceImpl systemSettingService;

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), SystemSetting.class);
    }

    @Test
    void readsLatestRegistrationSettingWithoutCache() {
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue("true");
        when(systemSettingEntityService.getOne(any(Wrapper.class))).thenReturn(setting);
        assertTrue(systemSettingService.getSystemSetting().getRegisterEnabled());
        setting.setSettingValue("false");
        assertFalse(systemSettingService.getSystemSetting().getRegisterEnabled());
        verify(systemSettingEntityService, times(2)).getOne(any(Wrapper.class));
    }

    @Test
    void missingOrInvalidSettingDoesNotEnableRegistration() {
        assertThrows(ServiceException.class, () -> systemSettingService.isRegisterEnabled());
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue("invalid");
        when(systemSettingEntityService.getOne(any(Wrapper.class))).thenReturn(setting);
        assertThrows(ServiceException.class, () -> systemSettingService.isRegisterEnabled());
    }

    @Test
    void missingSwitchIsRejectedBeforeWriting() {
        assertThrows(ServiceException.class, () -> systemSettingService.updateSystemSetting(new SystemSettingDTO()));
        verifyNoInteractions(systemSettingEntityService);
    }

    @Test
    void savesBothSwitchStatesAndReportsWriteFailure() {
        SystemSettingDTO settingDTO = new SystemSettingDTO();
        when(systemSettingEntityService.update(any(Wrapper.class))).thenReturn(true, true, false);
        settingDTO.setRegisterEnabled(false);
        assertDoesNotThrow(() -> systemSettingService.updateSystemSetting(settingDTO));
        settingDTO.setRegisterEnabled(true);
        assertDoesNotThrow(() -> systemSettingService.updateSystemSetting(settingDTO));
        assertThrows(ServiceException.class, () -> systemSettingService.updateSystemSetting(settingDTO));
    }
}

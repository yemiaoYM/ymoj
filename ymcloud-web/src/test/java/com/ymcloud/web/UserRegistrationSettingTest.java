package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.user.UserException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.service.AuthorizationService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.dto.RegisterDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.web.service.common.SystemSettingService;
import com.ymcloud.web.service.oj.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 注册开关测试，不消耗真实验证码或创建数据库用户
 */
@ExtendWith(MockitoExtension.class)
public class UserRegistrationSettingTest {

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private UserEntityService userEntityService;

    @Mock
    private RedisCache redisCache;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void closedRegistrationRejectsDirectRequestBeforeCaptchaOrUserWrites() {
        when(systemSettingService.isRegisterEnabled()).thenReturn(false);
        UserException exception = assertThrows(UserException.class, () -> userService.register(new RegisterDTO()));
        assertEquals(HttpStatus.FORBIDDEN, exception.getCode());
        assertEquals("系统暂未开放新用户注册", exception.getMessage());
        verifyNoInteractions(redisCache, userEntityService);
    }

    @Test
    void reopeningRegistrationRestoresExistingRegistrationFlow() {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("setting-test");
        registerDTO.setEmail("setting-test@example.com");
        registerDTO.setPassword("test12345");
        registerDTO.setRePassword("test12345");
        registerDTO.setCode("1234");
        registerDTO.setUuid("test-uuid");
        when(systemSettingService.isRegisterEnabled()).thenReturn(false, true);
        assertThrows(UserException.class, () -> userService.register(registerDTO));
        when(redisCache.getCacheObject(anyString())).thenReturn("1234");
        LambdaQueryChainWrapper<User> query = mock(LambdaQueryChainWrapper.class);
        when(userEntityService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(userEntityService.save(any(User.class))).thenReturn(true);
        assertDoesNotThrow(() -> userService.register(registerDTO));
        verify(userEntityService).save(any(User.class));
    }
}

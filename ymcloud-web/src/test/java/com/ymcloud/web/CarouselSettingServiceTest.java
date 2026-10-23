package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.core.web.mapper.SystemSettingMapper;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.common.CarouselSettingService;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 验证轮播图片内容、配置边界和失败路径，不访问真实数据库或存储
 */
class CarouselSettingServiceTest {

    private SystemSettingEntityService settings;
    private SystemSettingMapper settingMapper;
    private FileMapper fileMapper;
    private StoredFileService storage;
    private PlatformTransactionManager transactions;
    private CarouselSettingService service;

    /** 配置真实条件构造器，替换数据库和对象存储边界。 */
    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), SystemSetting.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), File.class);
        settings = mock(SystemSettingEntityService.class);
        settingMapper = mock(SystemSettingMapper.class);
        fileMapper = mock(FileMapper.class);
        FileEntityService files = mock(FileEntityService.class);
        storage = mock(StoredFileService.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(settings.lambdaQuery()).thenAnswer(call -> new LambdaQueryChainWrapper<>(settingMapper, SystemSetting.class));
        when(settings.lambdaUpdate()).thenAnswer(call -> new LambdaUpdateChainWrapper<>(settingMapper));
        when(files.lambdaQuery()).thenAnswer(call -> new LambdaQueryChainWrapper<>(fileMapper, File.class));
        service = new CarouselSettingService(settings, files, storage, new ObjectMapper(), transactions);
    }

    /** 未配置与明确清空必须保持不同含义。 */
    @Test
    void distinguishesMissingAndExplicitlyEmptyConfig() {
        assertFalse(service.get().configured());
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("[]"));
        assertTrue(service.get().configured());
        assertTrue(service.get().images().isEmpty());
        verifyNoInteractions(fileMapper);
    }

    /** 读取顺序遵循配置，缺失图片跳过，ID 按字符串公开。 */
    @Test
    void preservesOrderAndSkipsMissingImages() {
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("[3,1,2]"));
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(image(1), image(3)));
        assertEquals(List.of("3", "1"), service.get().images().stream().map(item -> item.id()).toList());
    }

    /** 损坏配置不能伪装为未配置并恢复内置图片。 */
    @Test
    void rejectsDamagedConfiguration() {
        for (String value : List.of("broken", "null", "[null]", "[-1]")) {
            when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting(value));
            assertThrows(ServiceException.class, () -> service.get());
        }
    }

    /** 结构校验允许空列表，拒绝缺失、空元素、非法 ID 和超过十张图片。 */
    @Test
    void validatesRequestShape() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(List.of())).isEmpty());
            assertFalse(validator.validate(new CarouselSettingDTO()).isEmpty());
            assertFalse(validator.validate(request(Arrays.asList("1", null))).isEmpty());
            assertFalse(validator.validate(request(List.of("0", "x"))).isEmpty());
            assertFalse(validator.validate(request(java.util.Collections.nCopies(11, "1"))).isEmpty());
        }
    }

    /** 重复 ID 及 Long 溢出在访问文件和事务前失败。 */
    @Test
    void rejectsDuplicateAndOverflowIds() {
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1", "1"))));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("9999999999999999999"))));
        verifyNoInteractions(storage, fileMapper);
        verify(transactions, never()).getTransaction(any());
    }

    /** 首次保存补齐键，空列表用于隐藏；写入失败要求事务回滚。 */
    @Test
    void insertsEmptyConfigAndRollsBackWriteFailure() {
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("true")).thenReturn(null);
        when(settings.save(any())).thenReturn(true);
        service.save(request(List.of()));
        verify(settings).save(argThat(item -> item.getSettingKey().equals("home_carousel") && item.getSettingValue().equals("[]")));
        verify(transactions).commit(any());
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("true")).thenReturn(null);
        when(settings.save(any())).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.save(request(List.of())));
        verify(transactions).rollback(any());
    }

    /**
     * 图片内容在事务外校验，事务内复核访问状态并保存顺序。
     * @throws IOException 测试图片编码失败
     */
    @Test
    void validatesThenSavesOrderedImages() throws IOException {
        when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(image(2), image(1), image(1), image(2));
        when(storage.open(any())).thenAnswer(call -> new ByteArrayInputStream(png(160, 90)));
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("true"), setting("[]"));
        when(settingMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.save(request(List.of("2", "1")));
        var ordered = inOrder(storage, transactions);
        ordered.verify(storage, times(2)).open(any());
        ordered.verify(transactions).getTransaction(any());
        verify(settingMapper).update(isNull(), argThat(wrapper -> {
            wrapper.getSqlSegment(); // MyBatis 实际执行时会渲染 SQL 并填充参数。
            return ((LambdaUpdateWrapper<SystemSetting>) wrapper).getParamNameValuePairs().containsValue("[2,1]");
        }));
    }

    /**
     * 私有文件、测试数据及内容比例不符的图片不允许配置。
     * @throws IOException 测试图片编码失败
     */
    @Test
    void rejectsUnsafeReferencesAndWrongAspectRatio() throws IOException {
        File privateImage = image(1);
        privateImage.setIsPublic(0);
        File testData = image(1);
        testData.setBizType(FileBizType.TEST_DATA);
        when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(null, privateImage, testData, image(1));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1"))));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1"))));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1"))));
        when(storage.open(any())).thenReturn(new ByteArrayInputStream(png(100, 100)));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1"))));
        verify(transactions, never()).getTransaction(any());
    }

    /**
     * 校验后被删除的文件不能因竞态被写入配置。
     * @throws IOException 测试图片编码失败
     */
    @Test
    void rechecksFileInsideTransaction() throws IOException {
        when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(image(1)).thenReturn(null);
        when(storage.open(any())).thenReturn(new ByteArrayInputStream(png(160, 90)));
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("true"));
        assertThrows(ServiceException.class, () -> service.save(request(List.of("1"))));
        verify(settings, never()).save(any());
        verify(transactions).rollback(any());
    }

    /**
     * 上传拒绝伪造 MIME、空文件、错误比例及超限内容。
     * @throws IOException 测试图片编码失败
     */
    @Test
    void rejectsInvalidUploadsBeforeStorage() throws IOException {
        for (byte[] bytes : List.of(new byte[0], "not png".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                png(160, 100), new byte[5 * 1024 * 1024 + 1])) {
            var upload = new MockMultipartFile("file", "fake.png", "image/png", bytes);
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ServiceException.class, () -> service.upload(upload)).getCode());
        }
        verifyNoInteractions(storage);
    }

    /**
     * 合法上传重新编码，存储元数据标记为公开图片，并返回字符串 ID。
     * @throws IOException 测试图片编码失败
     */
    @Test
    void uploadsVerifiedImage() throws IOException {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            when(storage.save(any(), anyLong(), any(File.class))).thenAnswer(call -> {
                File file = call.getArgument(2);
                assertEquals(FileBizType.IMAGE, file.getBizType());
                assertEquals(1, file.getIsPublic());
                assertEquals("image/png", file.getFileType());
                file.setId(17L);
                file.setUrl("/files/public/test/image.png");
                return file;
            });
            assertEquals("17", service.upload(new MockMultipartFile("file", "photo.png", "image/png", png(160, 90))).id());
        }
    }

    /**
     * 构造测试图片，不使用外部图片文件。
     * @param width 宽度，像素
     * @param height 高度，像素
     * @return PNG 字节
     * @throws IOException 编码失败
     */
    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    /**
     * 构造轮播输入。
     * @param ids 有序 ID 字符串
     * @return 请求对象
     */
    private static CarouselSettingDTO request(List<String> ids) {
        CarouselSettingDTO request = new CarouselSettingDTO();
        request.setFileIds(ids);
        return request;
    }

    /**
     * 构造配置记录。
     * @param value 配置值
     * @return 配置记录
     */
    private static SystemSetting setting(String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingValue(value);
        return setting;
    }

    /**
     * 构造安全图片元数据。
     * @param id 文件 ID
     * @return 公开图片
     */
    private static File image(long id) {
        File file = new File();
        file.setId(id);
        file.setBizType(FileBizType.IMAGE);
        file.setIsPublic(1);
        file.setUrl("/files/public/test/" + id + ".png");
        return file;
    }
}

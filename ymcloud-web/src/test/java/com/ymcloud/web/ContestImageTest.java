package com.ymcloud.web;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.controller.admin.contest.AdminContestController;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.admin.impl.AdminContestServiceImpl;
import com.ymcloud.web.service.common.PublicImageService;
import com.ymcloud.web.service.common.impl.PublicImageServiceImpl;
import com.ymcloud.web.service.file.StoredFileService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 比赛方形封面的内容、存储、默认图恢复及权限回归。
 */
class ContestImageTest {

    /** 测试真实方法权限拦截。 */
    @EnableMethodSecurity
    @org.springframework.context.annotation.Configuration
    static class SecurityConfig {}

    /**
     * 方形封面重编码后保存，非方形与伪造图片均不能写入存储。
     * @throws Exception 测试图片构造或读取失败
     */
    @Test
    void uploadsSquareCoverThroughSharedStorage() throws Exception {
        StoredFileService storage = mock(StoredFileService.class);
        PublicImageServiceImpl images = new PublicImageServiceImpl();
        ReflectionTestUtils.setField(images, "storedFiles", storage);
        AdminContestController controller = new AdminContestController();
        ReflectionTestUtils.setField(controller, "publicImages", images);
        doAnswer(call -> {
            BufferedImage image = ImageIO.read((InputStream) call.getArgument(0));
            assertEquals(1024, image.getWidth());
            assertEquals(1024, image.getHeight());
            File file = call.getArgument(2);
            assertEquals("contest.png", file.getFileName());
            assertEquals(FileBizType.IMAGE, file.getBizType());
            assertEquals(1, file.getIsPublic());
            file.setUrl("/files/public/cover.png");
            return file;
        }).when(storage).save(any(InputStream.class), anyLong(), any(File.class));
        User user = new User();
        user.setId(2L);
        user.setUsername("editor");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            assertEquals("/files/public/cover.png", controller.uploadContest(picture(1024, 1024)).getData());
        }
        assertThrows(FileException.class, () -> controller.uploadContest(picture(1600, 900)));
        assertThrows(FileException.class, () -> controller.uploadContest(
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8))));
        verify(storage, times(1)).save(any(InputStream.class), anyLong(), any(File.class));
    }

    /**
     * 文件拒绝仍返回参数错误，读取失败保留 cause，不能退化为通用业务异常。
     * @throws IOException 配置模拟读取失败
     */
    @Test
    void preservesFileErrorCodeAndCause() throws IOException {
        PublicImageServiceImpl images = new PublicImageServiceImpl();
        FileException empty = assertThrows(FileException.class, () -> images.upload(
                new MockMultipartFile("file", new byte[0]), "contest", 1, 1));
        assertEquals(HttpStatus.BAD_REQUEST, empty.getCode());
        FileException large = assertThrows(FileException.class, () -> images.upload(
                new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1]), "contest", 1, 1));
        assertEquals(HttpStatus.BAD_REQUEST, large.getCode());
        IOException cause = new IOException("test read failure");
        var unreadable = mock(org.springframework.web.multipart.MultipartFile.class);
        when(unreadable.getInputStream()).thenThrow(cause);
        FileException read = assertThrows(FileException.class,
                () -> images.upload(unreadable, "contest", 1, 1));
        assertEquals(HttpStatus.BAD_REQUEST, read.getCode());
        assertSame(cause, read.getCause());
    }

    /**
     * 新增保存封面，编辑可以清空封面回到默认图
     * 未知或非图片引用不能写入比赛
     */
    @Test
    @SuppressWarnings("unchecked")
    void persistsCoverAndCanRestoreDefault() {
        ContestEntityService contests = mock(ContestEntityService.class);
        StoredFileService storage = mock(StoredFileService.class);
        AdminContestServiceImpl service = new AdminContestServiceImpl();
        ReflectionTestUtils.setField(service, "resourceAccess", mock(ResourceAccessService.class));
        ReflectionTestUtils.setField(service, "contestEntityService", contests);
        ReflectionTestUtils.setField(service, "storedFiles", storage);
        Contest existing = new Contest();
        existing.setImage("/files/public/cover.png");
        LambdaQueryChainWrapper<Contest> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.last("FOR UPDATE")).thenReturn(query);
        when(query.one()).thenReturn(existing);
        when(contests.lambdaQuery()).thenReturn(query);
        when(contests.save(any())).thenReturn(true);
        when(contests.updateById(any(Contest.class))).thenReturn(true);
        File file = new File();
        file.setBizType(FileBizType.IMAGE);
        when(storage.resolvePublicUrl("/files/public/cover.png")).thenReturn(file);
        ContestDTO request = new ContestDTO();
        request.setTitle("比赛");
        request.setVisibility(0);
        request.setStartTime(LocalDateTime.of(2030, 1, 1, 10, 0));
        request.setEndTime(request.getStartTime().plusHours(2));
        request.setImage("/files/public/cover.png");
        User user = new User();
        user.setId(2L);
        user.setUsername("editor");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            service.addContest(request);
        }
        ArgumentCaptor<Contest> saved = ArgumentCaptor.forClass(Contest.class);
        verify(contests).save(saved.capture());
        assertEquals(request.getImage(), saved.getValue().getImage());
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setMode(request.getMode());
        existing.setSourceId(0L);
        request.setImage(null);
        service.updateContest(9L, request);
        verify(contests).updateById(saved.capture());
        assertNull(saved.getValue().getImage());
        var table = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), Contest.class);
        String update = table.getFieldList().stream().filter(field -> field.getProperty().equals("image"))
                .findFirst().orElseThrow().getSqlSet("et.");
        assertFalse(update.contains("<if"));
        request.setImage("/files/private/data.txt");
        assertThrows(FileException.class, () -> service.updateContest(9L, request));
        request.setImage("/files/public/cover.png");
        file.setBizType(FileBizType.GENERAL);
        assertThrows(FileException.class, () -> service.addContest(request));
        verify(contests, times(1)).save(any(Contest.class));
        verify(contests, times(1)).updateById(any(Contest.class));
    }

    /**
     * 比赛封面入口保留比赛写权限，只读账号不能调用
     */
    @Test
    void requiresContestWritePermission() {
        PublicImageService images = mock(PublicImageService.class);
        File file = new File();
        file.setUrl("/files/public/cover.png");
        when(images.upload(null, "contest", 1, 1)).thenReturn(file);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SecurityConfig.class, AdminContestController.class, PermissionService.class);
            context.getBeanFactory().registerSingleton("publicImageService", images);
            context.getBeanFactory().registerSingleton("adminContestService", mock(AdminContestService.class));
            context.refresh();
            var controller = context.getBean(AdminContestController.class);
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("viewer", "", "contest:contest:list"));
            assertThrows(AccessDeniedException.class, () -> controller.uploadContest(null));
            verifyNoInteractions(images);
            for (String permission : new String[]{"contest:contest:add", "contest:contest:edit"}) {
                SecurityContextHolder.getContext().setAuthentication(
                        new TestingAuthenticationToken("editor", "", permission));
                assertDoesNotThrow(() -> controller.uploadContest(null));
            }
            verify(images, times(2)).upload(null, "contest", 1, 1);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 生成无外部依赖的 PNG 测试图。
     * @param width 图宽
     * @param height 图高
     * @return multipart 图片
     * @throws Exception 编码失败
     */
    private MockMultipartFile picture(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "cover.png", "image/png", output.toByteArray());
    }
}

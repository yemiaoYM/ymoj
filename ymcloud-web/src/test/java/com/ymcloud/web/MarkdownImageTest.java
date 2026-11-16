package com.ymcloud.web;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.common.impl.PublicImageServiceImpl;
import com.ymcloud.web.service.file.StoredFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-31
 * @Description: Markdown 图片压缩、真实格式、解码容量与存储失败边界回归
 */
class MarkdownImageTest {

    private final PublicImageServiceImpl images = new PublicImageServiceImpl();
    private final StoredFileService storage = mock(StoredFileService.class);
    private byte[] savedBytes;
    private File savedFile;

    /**
     * 捕获实际输出字节，执行真实解码及压缩，仅模拟存储副作用
     * @throws IOException 设置流读取模拟失败
     */
    @BeforeEach
    void setup() throws IOException {
        ReflectionTestUtils.setField(images, "storedFiles", storage);
        doAnswer(call -> {
            savedBytes = ((InputStream) call.getArgument(0)).readAllBytes();
            assertEquals(savedBytes.length, (long) call.getArgument(1));
            savedFile = call.getArgument(2);
            savedFile.setUrl("/files/public/local/image.png");
            return savedFile;
        }).when(storage).save(any(InputStream.class), anyLong(), any(File.class));
    }

    /**
     * 大图等比缩小、重新编码去除附加内容，并绑定登录用户
     * @throws Exception 图片编码或解码失败
     */
    @Test
    void resizesAndReencodesWithoutTrustingNameOrMime() throws Exception {
        byte[] original = picture(3000, 1500, false, false);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(original);
        payload.write("<script>unsafe()</script>".getBytes(StandardCharsets.UTF_8));
        upload(new MockMultipartFile("file", "../../x.html", "text/html", payload.toByteArray()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertEquals(1920, image.getWidth());
        assertEquals(960, image.getHeight());
        assertTrue(savedBytes.length <= 2 * 1024 * 1024);
        assertFalse(new String(savedBytes, StandardCharsets.ISO_8859_1).contains("<script>"));
        assertEquals("image/jpeg", savedFile.getFileType());
        assertEquals("markdown.jpeg", savedFile.getFileName());
        assertEquals(9L, savedFile.getUploaderId());
        assertEquals("tester", savedFile.getUploaderUsername());
        assertEquals(FileBizType.IMAGE, savedFile.getBizType());
        assertEquals(1, savedFile.getIsPublic());
    }

    /**
     * 透明图片不铺黑底、不放大，超过保存体积的随机图继续等比缩小
     * @throws Exception 图片编码或解码失败
     */
    @Test
    void preservesTransparencyAndBoundsEncodedSize() throws Exception {
        upload(new MockMultipartFile("file", picture(20, 10, true, false)));
        BufferedImage small = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertEquals(20, small.getWidth());
        assertEquals(0, small.getRGB(0, 0) >>> 24);
        assertEquals("image/png", savedFile.getFileType());

        byte[] noisy = picture(1000, 1000, true, true);
        assertTrue(noisy.length > 2 * 1024 * 1024);
        upload(new MockMultipartFile("file", noisy));
        assertTrue(savedBytes.length <= 2 * 1024 * 1024);
        BufferedImage resized = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertTrue(resized.getWidth() < 1000);
        assertEquals(resized.getWidth(), resized.getHeight());
        assertTrue(resized.getColorModel().hasAlpha());
    }

    /**
     * 已安装的 WebP 解码器可以处理静态图片，输出仍使用通用的安全图片格式
     * @throws IOException 输出图片解码失败
     */
    @Test
    void decodesStaticWebp() throws IOException {
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        upload(new MockMultipartFile("file", "image.webp", "image/webp", webp));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(savedBytes));
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
        assertEquals("image/jpeg", savedFile.getFileType());
    }

    /**
     * 空文件、超限体积、伪图片和 SVG 不进入存储
     */
    @Test
    void rejectsUntrustedFilesBeforeStorage() {
        for (byte[] payload : new byte[][] {
                new byte[0], new byte[5 * 1024 * 1024 + 1],
                "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8),
                "not an image".getBytes(StandardCharsets.UTF_8)
        }) {
            FileException error = assertThrows(FileException.class,
                    () -> images.upload(new MockMultipartFile("file", "fake.png", "image/png", payload), "markdown", 0, 0));
            assertEquals(HttpStatus.BAD_REQUEST, error.getCode());
        }
        verifyNoInteractions(storage);
    }

    /**
     * 在像素解码之前拒绝伪造的大尺寸 PNG 头，避免分配超大位图
     * @throws Exception 构造测试 PNG 失败
     */
    @Test
    void rejectsPixelBombBeforeDecoding() throws Exception {
        byte[] png = picture(1, 1, false, false);
        ByteBuffer.wrap(png).putInt(16, 100_000).putInt(20, 100_000);
        FileException error = assertThrows(FileException.class,
                () -> images.upload(new MockMultipartFile("file", png), "markdown", 0, 0));
        assertEquals("图片不能超过2400万像素", error.getMessage());
        verifyNoInteractions(storage);
    }

    /**
     * 存储失败原样传播，不返回成功地址，也不重复包装领域错误
     * @throws Exception 构造测试 PNG 失败
     */
    @Test
    void propagatesStorageFailure() throws Exception {
        FileException failure = new FileException("存储不可用", HttpStatus.SERVICE_UNAVAILABLE);
        when(storage.save(any(InputStream.class), anyLong(), any(File.class))).thenThrow(failure);
        byte[] bytes = picture(10, 10, false, false);
        assertSame(failure, assertThrows(FileException.class, () -> upload(new MockMultipartFile("file", bytes))));
    }

    /**
     * 在模拟的真实登录用户上下文执行上传
     * @param file 上传文件，不含可信用户信息
     */
    private void upload(MockMultipartFile file) {
        User user = new User();
        user.setId(9L);
        user.setUsername("tester");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            assertEquals("/files/public/local/image.png", images.upload(file, "markdown", 0, 0).getUrl());
        }
    }

    /**
     * 创建可重现的静态 PNG，随机图用于验证真实压缩容量
     * @param width 像素宽度
     * @param height 像素高度
     * @param alpha 是否保留透明通道
     * @param noise 是否填充随机像素
     * @return PNG 字节
     * @throws IOException PNG 编码失败
     */
    private byte[] picture(int width, int height, boolean alpha, boolean noise) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (noise) {
            Random random = new Random(31);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, random.nextInt());
                }
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

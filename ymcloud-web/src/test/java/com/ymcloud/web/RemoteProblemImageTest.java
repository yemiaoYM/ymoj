package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.storage.factory.StorageRegistry;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.remote.LuoguProblemProvider;
import com.ymcloud.web.remote.ProblemHtmlMarkdown;
import com.ymcloud.web.remote.ProblemImageEncoder;
import com.ymcloud.web.remote.ProblemImageReferences;
import com.ymcloud.web.remote.RemoteProblemHttpClient;
import com.ymcloud.web.remote.RemoteProblemImages;
import com.ymcloud.web.service.file.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 远程插图定位、压缩、存储事务边界与公式不落图片回归
 */
class RemoteProblemImageTest {

    private static final String IMAGE = "https://cdn.luogu.com.cn/upload/image_hosting/test.png";
    private RemoteProblemImages importer;
    private RemoteProblemHttpClient client;
    private FileService files;
    private PlatformTransactionManager transactions;
    private final List<byte[]> saved = new ArrayList<>();
    private final List<File> metadata = new ArrayList<>();

    /**
     * 装配真实导入处理器及隔离的文件存储边界
     * @throws IOException 读取保存的图片流失败
     */
    @BeforeEach
    void setUp() throws IOException {
        importer = new RemoteProblemImages();
        client = mock(RemoteProblemHttpClient.class);
        files = mock(FileService.class);
        transactions = mock(PlatformTransactionManager.class);
        var registry = mock(StorageRegistry.class);
        when(registry.maxFileSize()).thenReturn(50L * 1024 * 1024);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(importer, "httpClient", client);
        ReflectionTestUtils.setField(importer, "storedFiles", files);
        ReflectionTestUtils.setField(importer, "storage", registry);
        ReflectionTestUtils.setField(importer, "transactionManager", transactions);
        when(files.create(any(CreateStoredFileCommand.class), any(InputStream.class), anyLong())).thenAnswer(invocation -> {
            byte[] bytes = ((InputStream) invocation.getArgument(1)).readAllBytes();
            saved.add(bytes);
            File file = StoredFileFixture.saved(invocation.getArgument(0));
            assertEquals((long) bytes.length, (long) invocation.getArgument(2));
            file.setPublicId(String.format("%012x40008000000000000001", saved.size()));
            metadata.add(file);
            return file;
        });
        User user = new User();
        user.setId(1L);
        user.setUsername("image-test");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(new LoginUser(user), ""));
    }

    /**
     * 清除测试身份，避免影响其他权限用例
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 定位内联、引用和 HTML 图片，不匹配代码示例或修改数学语法
     */
    @Test
    void replacesOnlyActualImages() {
        String source = """
                公式 $S_{1,1} \\le n$ 与 ![图](https://cdn.luogu.com.cn/a.png "示意图")
                ![第二张][figure]

                [figure]: https://img.atcoder.jp/b.png

                `![代码](https://bad.invalid/a.png)`
                ```md
                ![代码](https://bad.invalid/b.png)
                ```
                <div><img src='https://uploadfiles.nowcoder.com/c.png' alt='HTML图'> 保留文本</div>

                <pre>&lt;img src='https://bad.invalid/c.png'&gt;</pre>
                """;
        var images = ProblemImageReferences.find(source);
        assertEquals(3, images.size());
        var urls = Map.of("https://cdn.luogu.com.cn/a.png", "/files/public/a.png",
                "https://img.atcoder.jp/b.png", "/files/public/b.png",
                "https://uploadfiles.nowcoder.com/c.png", "/files/public/c.png");
        String replaced = ProblemImageReferences.replace(source, images, urls);
        assertTrue(replaced.contains("$S_{1,1} \\le n$"));
        assertTrue(replaced.contains("![图](/files/public/a.png \"示意图\")"));
        assertTrue(replaced.contains("![第二张](/files/public/b.png)"));
        assertTrue(replaced.contains("<img src=\"/files/public/c.png\" alt=\"HTML图\"> 保留文本"));
        assertTrue(replaced.contains("`![代码](https://bad.invalid/a.png)`"));
        assertTrue(replaced.contains("![代码](https://bad.invalid/b.png)"));
        assertTrue(replaced.contains("<pre>&lt;img src='https://bad.invalid/c.png'&gt;</pre>"));
    }

    /**
     * 同图跨字段只保存一次，公式恢复为 LaTeX 且不产生下载或存储请求
     * @throws IOException 生成或读取测试插图失败
     */
    @Test
    void storesIllustrationsOnceAndPreservesFormula() throws IOException {
        byte[] png = png(64, 32, false);
        when(client.getImage(IMAGE)).thenReturn(png);
        var draft = draft("![示意图](" + IMAGE + ")\n$S_{1,1}$\n"
                + "![](https://www.nowcoder.com/equation?tex=x_%7B1%7D)");
        draft.setInput("<img src='" + IMAGE + "' alt='输入图'>");
        draft.setOutput("![输出图](" + IMAGE + ")");
        importer.importImages(draft);
        verify(client).getImage(IMAGE);
        verify(files).create(any(CreateStoredFileCommand.class), any(InputStream.class), anyLong());
        verify(transactions).commit(any());
        assertEquals(1, saved.size());
        assertEquals(64, ImageIO.read(new ByteArrayInputStream(saved.get(0))).getWidth());
        assertTrue(draft.getContent().contains("$x_{1}$"));
        assertTrue(draft.getContent().contains("$S_{1,1}$"));
        assertFalse(draft.getContent().contains("https://"));
        assertTrue(draft.getInput().contains("/files/public/00000000000140008000000000000001"));
        assertTrue(draft.getOutput().contains("/files/public/00000000000140008000000000000001"));
        assertEquals("原始来源", draft.getSource());
        File file = metadata.get(0);
        assertEquals(FileConstants.ACCESS_PUBLIC, file.getIsPublic());
        assertEquals(FileBizType.IMAGE, file.getBizType());
        assertEquals("image/png", file.getFileType());
        assertEquals(1L, file.getUploaderId());
        assertEquals("image-test", file.getUploaderUsername());
    }

    /**
     * 只有公式时完全不访问文件存储，缺少 alt 时从 tex 参数恢复
     */
    @Test
    void formulaImagesDoNotUseStorage() {
        var draft = draft("$x$ ![](https://hr.nowcoder.com/equation?tex=a%2Bb)");
        importer.importImages(draft);
        assertEquals("$x$ $a+b$", draft.getContent());
        verifyNoInteractions(client, files, transactions);
        assertEquals("n_1", ProblemHtmlMarkdown.equationImage("https://www.nowcoder.com/equation?tex=n", "n_1"));
        assertNull(ProblemHtmlMarkdown.equationImage(IMAGE, "x+y"));
        assertEquals("n", ProblemHtmlMarkdown.equationImage("//www.nowcoder.com/equation?tex=n", ""));
        var html = org.jsoup.Jsoup.parseBodyFragment("<pre><img src='" + IMAGE + "'></pre>").body();
        String statement = ProblemHtmlMarkdown.convert(html);
        assertFalse(statement.contains("```"));
        assertEquals(1, ProblemImageReferences.find(statement).size());
    }

    /**
     * 下载失败时不进入文件事务，存储失败时请求回滚且不修改草稿
     * @throws IOException 生成测试插图失败
     */
    @Test
    void failuresNeverReturnPartlyRewrittenDrafts() throws IOException {
        String second = "https://img.atcoder.jp/second.png";
        var draft = draft("![](" + IMAGE + ") ![](" + second + ")");
        String original = draft.getContent();
        when(client.getImage(IMAGE)).thenReturn(png(2, 2, false));
        when(client.getImage(second)).thenThrow(new FileException("download"));
        assertThrows(FileException.class, () -> importer.importImages(draft));
        verifyNoInteractions(files, transactions);
        assertEquals(original, draft.getContent());
        doReturn(png(3, 3, false)).when(client).getImage(second);
        when(files.create(any(CreateStoredFileCommand.class), any(InputStream.class), anyLong()))
                .thenAnswer(invocation -> {
                    File file = StoredFileFixture.saved(invocation.getArgument(0));
                    file.setPublicId("a0c3440ccd014a6ea014bf4c2e818a96");
                    return file;
                }).thenThrow(new FileException("storage"));
        assertThrows(FileException.class, () -> importer.importImages(draft));
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        assertEquals(original, draft.getContent());
    }

    /**
     * 大图压缩后可解码且保留透明度及比例，小图保持尺寸
     * @throws IOException 编解码测试图片失败
     */
    @Test
    void compressesOversizedIllustrations() throws IOException {
        byte[] bytes = png(512, 256, true);
        var small = ProblemImageEncoder.encode(bytes, 5 * 1024 * 1024);
        assertEquals(512, ImageIO.read(new ByteArrayInputStream(small.bytes())).getWidth());
        var compressed = ProblemImageEncoder.encode(bytes, 20 * 1024);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(compressed.bytes()));
        assertTrue(compressed.bytes().length <= 20 * 1024);
        assertTrue(result.getWidth() < 512);
        assertTrue(Math.abs(result.getWidth() - 2 * result.getHeight()) <= 1);
        assertTrue(result.getColorModel().hasAlpha());
        assertThrows(FileException.class, () -> ProblemImageEncoder.encode("<html>403</html>".getBytes(), 1024));
    }

    /**
     * JPEG 和 WebP 可统一解码，GIF 动画不被静默转换为第一帧
     * @throws IOException 编解码测试图片失败
     */
    @Test
    void handlesJpegWebpAndAnimatedGif() throws IOException {
        var bitmap = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "jpg", jpeg);
        var encodedJpeg = ProblemImageEncoder.encode(jpeg.toByteArray(), 1024 * 1024);
        assertEquals("image/jpeg", encodedJpeg.contentType());
        assertEquals(16, ImageIO.read(new ByteArrayInputStream(encodedJpeg.bytes())).getWidth());
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var encodedWebp = ProblemImageEncoder.encode(webp, 1024 * 1024);
        assertEquals("image/png", encodedWebp.contentType());
        assertEquals(1, ImageIO.read(new ByteArrayInputStream(encodedWebp.bytes())).getWidth());
        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var stream = new MemoryCacheImageOutputStream(gif)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(bitmap, null, null), null);
            bitmap.setRGB(0, 0, 0xffffffff);
            writer.writeToSequence(new IIOImage(bitmap, null, null), null);
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        var animated = ProblemImageEncoder.encode(gif.toByteArray(), 1024 * 1024);
        assertEquals("image/gif", animated.contentType());
        assertArrayEquals(gif.toByteArray(), animated.bytes());
        assertThrows(FileException.class, () -> ProblemImageEncoder.encode(gif.toByteArray(), 10));
    }

    /**
     * 图片地址不允许访问内网、伪造域名、非标准端口或重定向目标
     * @throws IOException 生成图片失败
     */
    @Test
    void restrictsDownloadDestinationsAndResponses() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var realClient = new RemoteProblemHttpClient();
        ReflectionTestUtils.setField(realClient, "client", builder.build());
        for (String url : List.of("http://127.0.0.1/a.png", "http://192.168.1.1/a.png", "file:///a.png",
                "https://cdn.luogu.com.cn.evil.test/a.png", "https://cdn.luogu.com.cn:1234/a.png",
                "https://user:pass@cdn.luogu.com.cn/a.png", "https://[")) {
            assertThrows(FileException.class, () -> realClient.getImage(url));
        }
        server.verify();
        byte[] bytes = png(2, 2, false);
        server.expect(requestTo(IMAGE)).andRespond(withSuccess(bytes, MediaType.IMAGE_PNG));
        assertArrayEquals(bytes, realClient.getImage(IMAGE));
        server.verify();
        for (HttpStatus status : List.of(HttpStatus.FOUND, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            server.reset();
            server.expect(requestTo(IMAGE)).andRespond(withStatus(status));
            assertThrows(FileException.class, () -> realClient.getImage(IMAGE));
            server.verify();
        }
        server.reset();
        server.expect(requestTo(IMAGE)).andRespond(withSuccess(new byte[20 * 1024 * 1024 + 1], MediaType.IMAGE_PNG));
        assertThrows(FileException.class, () -> realClient.getImage(IMAGE));
        server.verify();
        server.reset();
        server.expect(requestTo(IMAGE)).andRespond(withException(new IOException("timeout")));
        assertThrows(FileException.class, () -> realClient.getImage(IMAGE));
        server.verify();
    }

    /**
     * 显式启用时真实下载洛谷 P1002 插图，存储边界隔离以免修改业务库
     * @throws IOException 保存本地验证产物失败
     */
    @Test
    @EnabledIfSystemProperty(named = "remoteImages.live", matches = "true")
    void downloadsRealIllustrationAndRewritesDraft() throws IOException {
        var http = new RemoteProblemHttpClient();
        http.initialize();
        var provider = new LuoguProblemProvider();
        ReflectionTestUtils.setField(provider, "httpClient", http);
        ReflectionTestUtils.setField(provider, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(importer, "httpClient", http);
        var draft = provider.fetch("P1002");
        importer.importImages(draft);
        assertFalse(saved.isEmpty());
        assertTrue(draft.getContent().contains("/files/public/00000000000140008000000000000001"));
        assertTrue(draft.getContent().contains("$A$"));
        for (byte[] image : saved) {
            assertTrue(ImageIO.read(new ByteArrayInputStream(image)).getWidth() > 0);
        }
        Files.write(Path.of("target/remote-illustration.png"), saved.get(0));
        Files.writeString(Path.of("target/remote-image-draft.json"), new ObjectMapper().writeValueAsString(draft));
    }

    /**
     * 创建只有题面字段的草稿，用于验证图片处理不改动来源
     * @param content 题干
     * @return 待处理草稿
     */
    private RemoteProblemVO draft(String content) {
        var draft = new RemoteProblemVO();
        draft.setContent(content);
        draft.setInput("");
        draft.setOutput("");
        draft.setSource("原始来源");
        return draft;
    }

    /**
     * 生成无压缩捷径的透明插图，以验证真实体积压缩
     * @param width 宽度
     * @param height 高度
     * @param noise 是否填充随机像素
     * @return PNG 字节
     * @throws IOException PNG 编码失败
     */
    private byte[] png(int width, int height, boolean noise) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (noise) {
            Random random = new Random(1);
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

package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.mapper.ProblemMapper;
import com.ymcloud.core.web.mapper.TestDataVersionMapper;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.oj.impl.TestDataServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 验证测试数据版本发布、历史目录拒绝及私有文件读取边界
 */
@ExtendWith(MockitoExtension.class)
class TestDataVersionServiceTest {

    @TempDir
    Path directory;

    @Mock
    ProblemEntityService problems;
    @Mock
    TestDataVersionEntityService versions;
    @Mock
    ProblemMapper problemMapper;
    @Mock
    TestDataVersionMapper versionMapper;
    @Mock
    FileService storage;

    TestDataServiceImpl service;
    Problem problem;
    final Map<Long, TestDataVersion> versionRows = new HashMap<>();
    final Map<Long, File> files = new HashMap<>();
    final Map<Long, byte[]> content = new HashMap<>();
    final AtomicLong nextFileId = new AtomicLong(100);
    TestDataVersion lastSaved;

    /**
     * 装配内存版本记录与文件服务替身，保留真实版本发布逻辑
     * @throws Exception 测试依赖装配失败
     */
    @BeforeEach
    void prepare() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), Problem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), TestDataVersion.class);
        problem = new Problem();
        problem.setId(7L);
        service = new TestDataServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "problemEntityService", problems);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "versionEntityService", versions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "storedFileService", storage);
        lenient().when(problems.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(problemMapper, Problem.class));
        lenient().when(problems.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(problemMapper));
        lenient().when(versions.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(versionMapper, TestDataVersion.class));
        lenient().when(problemMapper.selectOne(any(Wrapper.class))).thenReturn(problem);
        lenient().when(versionMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            AbstractWrapper<?, ?, ?> wrapper = invocation.getArgument(0);
            wrapper.getSqlSegment();
            Map<String, Object> parameters = wrapper.getParamNameValuePairs();
            TestDataVersion version = versionRows.get(parameters.get("MPGENVAL1"));
            if (version == null || !version.getProblemId().equals(parameters.get("MPGENVAL2"))) {
                return null;
            }
            return version;
        });
        lenient().when(versions.save(any(TestDataVersion.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            versionRows.put(lastSaved.getId(), lastSaved);
            return true;
        });
        lenient().when(problemMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            problem.setTestDataVersionId(lastSaved.getId());
            return 1;
        });
        lenient().when(storage.create(any(CreateStoredFileCommand.class), any(InputStream.class), anyLong())).thenAnswer(invocation -> {
            InputStream input = invocation.getArgument(1);
            byte[] bytes = input.readAllBytes();
            assertEquals((long) invocation.getArgument(2), bytes.length);
            File file = file(bytes, invocation.<CreateStoredFileCommand>getArgument(0).fileName());
            files.put(file.getId(), file);
            content.put(file.getId(), bytes);
            return file;
        });
        lenient().when(storage.get(anyLong())).thenAnswer(invocation -> files.get(invocation.getArgument(0)));
        lenient().when(storage.open(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            return new ByteArrayInputStream(content.get(file.getId()));
        });
    }

    @Test
    void zeroBytePairsAreAcceptedAndIdsFollowSortedNames() {
        service.upload(7L, new MultipartFile[]{upload("z.in", ""), upload("z.out", ""),
                upload("a.out", ""), upload("a.in", "")});
        TestDataManifest manifest = service.getManifest(7L, problem.getTestDataVersionId());
        assertEquals(List.of("/in/a.in", "/in/z.in"), manifest.getEntries().stream()
                .map(TestDataManifest.Entry::getInput).toList());
        assertEquals(List.of(1, 2), manifest.getEntries().stream().map(TestDataManifest.Entry::getId).toList());
        assertEquals(0L, manifest.getEntries().get(0).getInputSize());
    }

    @Test
    void replacementKeepsPinnedOldVersionAndPreservesInputWhitespace() throws Exception {
        service.upload(7L, pair("a", "  old\n\n", "answer"));
        Long oldVersion = problem.getTestDataVersionId();
        service.upload(7L, pair("a", "new", "different"));
        assertNotEquals(oldVersion, problem.getTestDataVersionId());
        try (InputStream input = service.openFile(7L, oldVersion, "/in/a.in")) {
            assertEquals("  old\n\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream input = service.openFile(7L, problem.getTestDataVersionId(), "/in/a.in")) {
            assertEquals("new", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(2, versionRows.size());
        verify(storage, never()).delete(anyLong());
    }

    @Test
    void deletingPairCreatesEmptyVersionWithoutDeletingHistory() throws Exception {
        service.upload(7L, pair("a", "input", "output"));
        Long oldVersion = problem.getTestDataVersionId();
        service.delete(7L, List.of("/in/a.in", "/out/a.out"));
        assertTrue(service.listCurrent(7L).isEmpty());
        assertThrows(ProblemException.class, () -> service.getCurrentVersion(7L));
        try (InputStream input = service.openFile(7L, oldVersion, "/in/a.in")) {
            assertEquals("input", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        verify(storage, never()).delete(anyLong());
    }

    /**
     * 对象上传失败不发布不完整测试版本
     */
    @Test
    void failedObjectUploadDoesNotPublishPartialVersion() {
        service.upload(7L, pair("a", "input", "output"));
        Long oldVersion = problem.getTestDataVersionId();
        when(storage.create(argThat(command -> command.fileName().equals("a.out")), any(InputStream.class), anyLong())).thenThrow(new ProblemException("storage down"));
        assertThrows(ProblemException.class, () -> service.upload(7L, pair("a", "new", "new")));
        assertEquals(oldVersion, problem.getTestDataVersionId());
        assertEquals(1, versionRows.size());
    }

    @Test
    void failedManifestInsertKeepsExistingPointer() {
        service.upload(7L, pair("a", "input", "output"));
        Long oldVersion = problem.getTestDataVersionId();
        when(versions.save(any(TestDataVersion.class))).thenReturn(false);
        assertThrows(ProblemException.class, () -> service.upload(7L, pair("b", "new", "new")));
        assertEquals(oldVersion, problem.getTestDataVersionId());
        assertEquals(1, versionRows.size());
    }

    @Test
    void unpairedDuplicateOversizedAndTraversalUploadsAreRejectedBeforeStorage() {
        assertThrows(ProblemException.class, () -> service.upload(7L, new MultipartFile[]{upload("a.in", "a")}));
        assertThrows(ProblemException.class, () -> service.upload(7L,
                new MultipartFile[]{upload("a.in", "a"), upload("a.in", "b"), upload("a.out", "a")}));
        for (String unsafe : List.of("../a.in", "C:\\a.in", "a\n.in", ".in", "a/b.in")) {
            assertThrows(ProblemException.class, () -> service.upload(7L,
                    new MultipartFile[]{upload(unsafe, "a"), upload("a.out", "a")}));
        }
        MultipartFile tooBig = mock(MultipartFile.class);
        when(tooBig.getOriginalFilename()).thenReturn("a.in");
        when(tooBig.getSize()).thenReturn(50L * 1024 * 1024 + 1);
        assertThrows(ProblemException.class, () -> service.upload(7L,
                new MultipartFile[]{tooBig, upload("a.out", "a")}));
        verifyNoInteractions(storage, versions, problems);
    }

    @Test
    void crossProblemWrongDirectoryAndUnlistedPathsAreRejected() {
        service.upload(7L, pair("a", "input", "output"));
        Long versionId = problem.getTestDataVersionId();
        assertThrows(ProblemException.class, () -> service.getManifest(8L, versionId));
        for (String path : List.of("/in/../a.in", "/out/a.in", "../../etc/passwd", "/in/missing.in")) {
            assertThrows(ProblemException.class, () -> service.openFile(7L, versionId, path));
        }
    }

    @Test
    void publicOrCrossProblemFileMetadataIsRejected() {
        service.upload(7L, pair("a", "input", "output"));
        Long versionId = problem.getTestDataVersionId();
        Long fileId = service.getManifest(7L, versionId).getEntries().get(0).getInputFileId();
        files.get(fileId).setIsPublic(1);
        assertThrows(ProblemException.class, () -> service.openFile(7L, versionId, "/in/a.in"));
        files.get(fileId).setIsPublic(0);
        files.get(fileId).setBizId(8L);
        assertThrows(ProblemException.class, () -> service.openFile(7L, versionId, "/in/a.in"));
    }

    @Test
    void corruptedManifestAndSameSizeObjectAreRejected() throws Exception {
        service.upload(7L, pair("a", "input", "output"));
        Long versionId = problem.getTestDataVersionId();
        TestDataVersion version = versionRows.get(versionId);
        String originalManifest = version.getManifest();
        version.setManifest(originalManifest + " ");
        assertThrows(ProblemException.class, () -> service.getManifest(7L, versionId));
        version.setManifest(originalManifest);
        Long inputId = service.getManifest(7L, versionId).getEntries().get(0).getInputFileId();
        content.put(inputId, "wrong".getBytes(StandardCharsets.UTF_8));
        try (InputStream input = service.openFile(7L, versionId, "/in/a.in")) {
            assertThrows(IOException.class, input::readAllBytes);
        }
    }

    /**
     * 拒绝不成对或重复删除，保持当前版本不变
     */
    @Test
    void partialDeleteAndDuplicateDeleteAreRejectedWithoutPublishing() {
        service.upload(7L, pair("a", "input", "output"));
        Long versionId = problem.getTestDataVersionId();
        assertThrows(ProblemException.class, () -> service.delete(7L, List.of("/in/a.in")));
        assertThrows(ProblemException.class, () -> service.delete(7L,
                List.of("/in/a.in", "/out/a.out", "/out/a.out")));
        assertEquals(versionId, problem.getTestDataVersionId());
        assertEquals(1, versionRows.size());
    }

    /**
     * 未登记历史目录不能再通过运行时读取或隐式创建测试数据版本
     * @throws Exception 创建历史文件夹具失败
     */
    @Test
    void unregisteredHistoricalFilesAreNotReadOrImported() throws Exception {
        legacyPair("a", "input", "output");
        assertTrue(service.getManifest(7L, null).getEntries().isEmpty());
        assertThrows(ProblemException.class, () -> service.getCurrentVersion(7L));
        assertThrows(ProblemException.class, () -> service.openFile(7L, null, "/in/a.in"));
        assertNull(problem.getTestDataVersionId());
        verifyNoInteractions(storage);
        verify(versions, never()).save(any());
    }

    private MultipartFile[] pair(String name, String input, String output) {
        return new MultipartFile[]{upload(name + ".in", input), upload(name + ".out", output)};
    }

    private MockMultipartFile upload(String name, String value) {
        return new MockMultipartFile("file", name, "text/plain", value.getBytes(StandardCharsets.UTF_8));
    }

    private File file(byte[] value, String name) throws Exception {
        File file = new File();
        file.setId(nextFileId.incrementAndGet());
        file.setFileName(name);
        file.setFileSize((long) value.length);
        file.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)));
        file.setBizType(FileBizType.TEST_DATA);
        file.setBizId(7L);
        file.setIsPublic(0);
        return file;
    }

    private void legacyPair(String name, String input, String output) throws Exception {
        Path root = directory.resolve("private/problem/7");
        Files.createDirectories(root.resolve("in"));
        Files.createDirectories(root.resolve("out"));
        Files.writeString(root.resolve("in/" + name + ".in"), input, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("out/" + name + ".out"), output, StandardCharsets.UTF_8);
    }
}

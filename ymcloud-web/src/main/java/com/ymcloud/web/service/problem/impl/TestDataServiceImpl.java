package com.ymcloud.web.service.problem.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.storage.util.FileDigests;
import com.ymcloud.core.storage.util.StorageDirectoryResolver;
import com.ymcloud.core.storage.util.StoragePaths;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.file.StoredFileService;
import com.ymcloud.web.service.problem.TestDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 在题目行锁内发布测试数据版本，对象与清单就绪后切换当前版本并保留历史引用
 */
@Service
public class TestDataServiceImpl implements TestDataService {

    private final ProblemEntityService problemEntityService;
    private final TestDataVersionEntityService versionEntityService;
    private final StoredFileService storedFileService;
    private final Path baseDirectory;

    /**
     * 绑定测试数据版本服务并固定历史目录基准
     * @param problemEntityService 题目实体服务
     * @param versionEntityService 测试数据版本实体服务
     * @param storedFileService 文件登记与对象生命周期服务
     * @param baseDirectory 本地文件根目录配置
     */
    public TestDataServiceImpl(ProblemEntityService problemEntityService,
                              TestDataVersionEntityService versionEntityService,
                              StoredFileService storedFileService,
                              @Value("${ymcloud.file.base-dir}") String baseDirectory) {
        this.problemEntityService = problemEntityService;
        this.versionEntityService = versionEntityService;
        this.storedFileService = storedFileService;
        this.baseDirectory = StorageDirectoryResolver.resolve(baseDirectory);
    }

    /**
     * 新任务固定版本，尚未迁移的完整历史数据在同一事务中生成初始快照
     * @param problemId 已授权的题目编号
     * @return 含有效测试点的固定版本编号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getCurrentVersion(Long problemId) {
        Problem problem = requireProblem(problemId, true);
        if (problem.getTestDataVersionId() != null) {
            TestDataManifest manifest = getManifest(problemId, problem.getTestDataVersionId());
            requireCases(manifest);
            return manifest.getVersionId();
        }
        TestDataMigrationReport report = inspectLegacy(problemId);
        requireValidLegacy(report);
        requireCases(report.getManifest());
        return publish(problemId, importLegacy(report.getManifest()));
    }

    /**
     * 读取并校验指定版本清单
     * @param problemId 已授权的题目编号
     * @param versionId 固定版本编号，为空时只读盘点历史数据
     * @return 通过身份与完整性校验的清单
     */
    @Override
    public TestDataManifest getManifest(Long problemId, Long versionId) {
        requireProblem(problemId, false);
        if (versionId == null) {
            TestDataMigrationReport report = inspectLegacy(problemId);
            requireValidLegacy(report);
            return report.getManifest();
        }
        TestDataVersion version = versionEntityService.lambdaQuery()
                .eq(TestDataVersion::getId, versionId)
                .eq(TestDataVersion::getProblemId, problemId)
                .one();
        if (version == null) {
            throw new ProblemException("测试数据版本不存在或不属于该题目");
        }
        if (!Objects.equals(version.getManifestSha256(), sha256(version.getManifest()))) {
            throw new ProblemException("测试数据版本清单校验失败");
        }
        TestDataManifest manifest;
        try {
            manifest = JSON.parseObject(version.getManifest(), TestDataManifest.class);
        } catch (RuntimeException e) {
            throw new ProblemException("测试数据版本清单无效", e);
        }
        validateManifest(manifest, problemId, versionId);
        return manifest;
    }

    /**
     * 读取指定清单中的文件并在读取过程中核对长度和哈希
     * @param problemId 已授权的题目编号
     * @param versionId 固定版本编号，历史未迁移数据可为空
     * @param logicalPath 清单中的输入或输出逻辑路径
     * @return 调用方负责关闭的校验流
     */
    @Override
    public InputStream openFile(Long problemId, Long versionId, String logicalPath) {
        validateLogicalPath(logicalPath);
        TestDataManifest manifest = getManifest(problemId, versionId);
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            boolean input = logicalPath.equals(entry.getInput());
            if (input || logicalPath.equals(entry.getOutput())) {
                Long size = input ? entry.getInputSize() : entry.getOutputSize();
                String hash = input ? entry.getInputSha256() : entry.getOutputSha256();
                InputStream stream;
                if (versionId == null) {
                    try {
                        stream = Files.newInputStream(safeLegacyFile(legacyRoot(problemId), logicalPath.substring(1)));
                    } catch (IOException e) {
                        throw new ProblemException("读取历史测试数据失败", e);
                    }
                } else {
                    File file = storedFileService.get(input ? entry.getInputFileId() : entry.getOutputFileId());
                    if (!FileBizType.TEST_DATA.equals(file.getBizType())
                            || !Objects.equals(problemId, file.getBizId())
                            || !Integer.valueOf(FileConstants.ACCESS_PRIVATE).equals(file.getIsPublic())
                            || !Objects.equals(size, file.getFileSize())
                            || !Objects.equals(hash, file.getSha256())) {
                        throw new ProblemException("测试数据文件元信息校验失败");
                    }
                    stream = storedFileService.open(file);
                }
                return new VerifiedInputStream(stream, size, hash);
            }
        }
        throw new ProblemException("文件不在测试数据版本清单中");
    }

    /**
     * 列出题目当前版本的测试点名称，不返回文件内容
     * @param problemId 已授权的题目编号
     * @return 当前测试点列表，无测试点时为空列表
     */
    @Override
    public List<ProblemCaseInfo> listCurrent(Long problemId) {
        Problem problem = requireProblem(problemId, false);
        return getManifest(problemId, problem.getTestDataVersionId()).getEntries()
                .stream()
                .map(entry -> new ProblemCaseInfo(entry.getId(), entry.getInput(), entry.getOutput()))
                .toList();
    }

    /**
     * 上传成对测试文件并在全部对象准备完成后发布新版本
     * @param problemId 已授权管理的题目编号
     * @param files 成对的输入和输出文件，单文件不超过测试数据限制
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upload(Long problemId, MultipartFile[] files) {
        Map<String, MultipartFile> uploads = validateUploads(files);
        Problem problem = requireProblem(problemId, true);
        Map<String, TestDataManifest.Entry> entries = currentEntries(problem);
        try {
            for (String inputName : uploads.keySet().stream().filter(name -> name.endsWith(".in")).sorted().toList()) {
                String outputName = baseName(inputName) + ".out";
                TestDataManifest.Entry entry = new TestDataManifest.Entry();
                entry.setInput("/in/" + inputName);
                entry.setOutput("/out/" + outputName);
                File inputFile = saveUpload(problemId, uploads.get(inputName));
                File outputFile = saveUpload(problemId, uploads.get(outputName));
                fillStoredFiles(entry, inputFile, outputFile);
                entries.put(baseName(inputName), entry);
            }
            publish(problemId, new ArrayList<>(entries.values()));
        } catch (IOException e) {
            throw new ProblemException("上传测试数据失败", e);
        }
    }

    /**
     * 从新版本中移除成对测试文件，保留旧版本引用的实际对象
     * @param problemId 已授权管理的题目编号
     * @param logicalPaths 非空、无重复且成对的输入与输出逻辑路径
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long problemId, List<String> logicalPaths) {
        if (logicalPaths == null || logicalPaths.isEmpty()) {
            throw new ProblemException("文件名不能为空");
        }
        Set<String> paths = new HashSet<>();
        for (String path : logicalPaths) {
            validateLogicalPath(path);
            if (!paths.add(path)) {
                throw new ProblemException("存在重复的删除文件");
            }
        }
        Problem problem = requireProblem(problemId, true);
        Map<String, TestDataManifest.Entry> entries = currentEntries(problem);
        Set<String> names = new HashSet<>();
        for (String path : paths) {
            String name = baseName(path.substring(path.lastIndexOf('/') + 1));
            TestDataManifest.Entry entry = entries.get(name);
            if (entry == null || (!path.equals(entry.getInput()) && !path.equals(entry.getOutput()))) {
                throw new ProblemException("文件不存在");
            }
            if (!paths.contains(entry.getInput()) || !paths.contains(entry.getOutput())) {
                throw new ProblemException("文件不配对，无法删除");
            }
            names.add(name);
        }
        names.forEach(entries::remove);
        // 删除只改变新版本清单，不删除仍可能被排队任务引用的对象
        publish(problemId, new ArrayList<>(entries.values()));
    }

    /**
     * 盘点历史目录，获准执行且数据完整时发布首个版本
     * @param problemId 已授权管理的题目编号
     * @param apply 是否实际导入文件并发布版本
     * @return 盘点错误、文件数量与字节数，以及实际迁移状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestDataMigrationReport migrateLegacy(Long problemId, boolean apply) {
        Problem problem = requireProblem(problemId, apply);
        if (problem.getTestDataVersionId() != null) {
            TestDataMigrationReport report = describe(getManifest(problemId, problem.getTestDataVersionId()));
            report.setApplied(apply);
            return report;
        }
        TestDataMigrationReport report = inspectLegacy(problemId);
        if (apply && report.getErrors().isEmpty() && !report.getManifest().getEntries().isEmpty()) {
            Long versionId = publish(problemId, importLegacy(report.getManifest()));
            report.setVersionId(versionId);
            report.setApplied(true);
            report.setManifest(getManifest(problemId, versionId));
        }
        return report;
    }

    /**
     * 读取已存在的题目，版本变更时通过行锁串行化发布
     * @param problemId 题目编号，必须为正数
     * @param lock 是否持有题目行写锁
     * @return 题目记录
     * @throws ProblemException 题目编号非法或题目不存在
     */
    private Problem requireProblem(Long problemId, boolean lock) {
        if (problemId == null || problemId <= 0) {
            throw new ProblemException("题目ID无效");
        }
        Problem problem;
        if (lock) {
            problem = problemEntityService.lambdaQuery()
                    .eq(Problem::getId, problemId)
                    .last("FOR UPDATE")
                    .one();
        } else {
            problem = problemEntityService.lambdaQuery()
                    .eq(Problem::getId, problemId)
                    .one();
        }
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        return problem;
    }

    /**
     * 获取可修改的当前条目索引，首次编辑时先导入完整历史数据
     * @param problem 已读取且持有写锁的题目
     * @return 按不含扩展名的文件名索引的条目
     */
    private Map<String, TestDataManifest.Entry> currentEntries(Problem problem) {
        List<TestDataManifest.Entry> entries;
        if (problem.getTestDataVersionId() == null) {
            TestDataMigrationReport report = inspectLegacy(problem.getId());
            requireValidLegacy(report);
            entries = importLegacy(report.getManifest());
        } else {
            entries = getManifest(problem.getId(), problem.getTestDataVersionId()).getEntries();
        }
        Map<String, TestDataManifest.Entry> result = new LinkedHashMap<>();
        for (TestDataManifest.Entry entry : entries) {
            result.put(baseName(entry.getInput().substring(4)), entry);
        }
        return result;
    }

    /**
     * 对条目排序编号并保存不可变版本，最后更新题目的当前版本引用
     * @param problemId 已持有行锁的题目编号
     * @param entries 对象已保存的测试点条目，本方法会排序并重编号
     * @return 新发布的版本编号
     */
    private Long publish(Long problemId, List<TestDataManifest.Entry> entries) {
        entries.sort(Comparator.comparing(TestDataManifest.Entry::getInput));
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).setId(index + 1);
        }
        TestDataManifest manifest = new TestDataManifest();
        manifest.setProblemId(problemId);
        manifest.setVersionId(IdWorker.getId());
        manifest.setEntries(entries);
        validateManifest(manifest, problemId, manifest.getVersionId());
        String json = JSON.toJSONString(manifest);
        TestDataVersion version = new TestDataVersion();
        version.setId(manifest.getVersionId());
        version.setProblemId(problemId);
        version.setManifest(json);
        version.setManifestSha256(sha256(json));
        version.setCreatedTime(LocalDateTime.now());
        if (!versionEntityService.save(version)) {
            throw new ProblemException("保存测试数据版本失败");
        }
        boolean updated = problemEntityService.lambdaUpdate()
                .eq(Problem::getId, problemId)
                .set(Problem::getTestDataVersionId, version.getId())
                .update();
        if (!updated) {
            throw new ProblemException("发布测试数据版本失败");
        }
        return version.getId();
    }

    /**
     * 校验上传文件名称、大小、唯一性及输入输出配对
     * @param files 待上传的文件数组
     * @return 按完整文件名索引的合法上传文件
     * @throws ProblemException 文件为空、超限、重名或不配对
     */
    private Map<String, MultipartFile> validateUploads(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ProblemException("请选择要上传的文件");
        }
        Map<String, MultipartFile> uploads = new HashMap<>();
        for (MultipartFile file : files) {
            if (file == null) {
                throw new ProblemException("上传文件无效");
            }
            String filename = file.getOriginalFilename();
            validateFilename(filename);
            if (file.getSize() < 0 || file.getSize() > ProblemConstants.MAX_TEST_CASE_FILE_SIZE) {
                throw new ProblemException("单个文件大小不能超过50MB");
            }
            if (uploads.putIfAbsent(filename, file) != null) {
                throw new ProblemException("存在重名文件：" + filename);
            }
        }
        for (String filename : uploads.keySet()) {
            String pair = baseName(filename) + (filename.endsWith(".in") ? ".out" : ".in");
            if (!uploads.containsKey(pair)) {
                throw new ProblemException("文件不配对，无法上传");
            }
        }
        return uploads;
    }

    /**
     * 保存单个私有测试文件，结束后关闭上传流
     * @param problemId 文件所属题目编号
     * @param file 已校验的上传文件
     * @return 已登记的私有测试文件
     * @throws IOException 打开或关闭上传流失败
     */
    private File saveUpload(Long problemId, MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return storedFileService.save(input, file.getSize(), file.getOriginalFilename(),
                    "text/plain", FileBizType.TEST_DATA, problemId, false);
        }
    }

    /**
     * 将已保存的输入输出对象身份、大小和摘要写入清单条目
     * @param entry 待填充的测试点
     * @param input 已保存的输入文件
     * @param output 已保存的输出文件
     */
    private void fillStoredFiles(TestDataManifest.Entry entry, File input, File output) {
        entry.setInputFileId(input.getId());
        entry.setOutputFileId(output.getId());
        entry.setInputSize(input.getFileSize());
        entry.setOutputSize(output.getFileSize());
        entry.setInputSha256(input.getSha256());
        entry.setOutputSha256(output.getSha256());
    }

    /**
     * 复制历史清单条目并导入对应私有对象，不修改原始清单
     * @param manifest 已通过盘点校验的历史清单
     * @return 带文件登记编号的新条目列表
     */
    private List<TestDataManifest.Entry> importLegacy(TestDataManifest manifest) {
        List<TestDataManifest.Entry> entries = new ArrayList<>();
        for (TestDataManifest.Entry original : manifest.getEntries()) {
            TestDataManifest.Entry entry = new TestDataManifest.Entry();
            entry.setId(original.getId());
            entry.setInput(original.getInput());
            entry.setOutput(original.getOutput());
            File input = saveLegacy(manifest.getProblemId(), original.getInput(), original.getInputSize(), original.getInputSha256());
            File output = saveLegacy(manifest.getProblemId(), original.getOutput(), original.getOutputSize(), original.getOutputSha256());
            fillStoredFiles(entry, input, output);
            entries.add(entry);
        }
        return entries;
    }

    /**
     * 按盘点大小与摘要读取历史文件，保存后再次核对迁移结果
     * @param problemId 所属题目编号
     * @param path 历史清单中的逻辑路径
     * @param size 盘点确认的文件字节数
     * @param hash 盘点确认的 SHA-256 摘要
     * @return 已登记的私有测试文件
     */
    private File saveLegacy(Long problemId, String path, long size, String hash) {
        try (InputStream input = new VerifiedInputStream(
                Files.newInputStream(safeLegacyFile(legacyRoot(problemId), path.substring(1))), size, hash)) {
            File file = storedFileService.save(input, size, path.substring(path.lastIndexOf('/') + 1),
                    "text/plain", FileBizType.TEST_DATA, problemId, false);
            if (!Objects.equals(hash, file.getSha256()) || !Objects.equals(size, file.getFileSize())) {
                throw new ProblemException("历史测试数据在迁移期间发生变化");
            }
            return file;
        } catch (IOException e) {
            throw new ProblemException("迁移历史测试数据失败", e);
        }
    }

    /**
     * 只读盘点历史目录并累计配对、摘要和旧清单校验问题
     * @param problemId 题目编号
     * @return 历史清单与盘点结果，目录不存在时返回空清单
     */
    private TestDataMigrationReport inspectLegacy(Long problemId) {
        TestDataManifest manifest = new TestDataManifest();
        manifest.setProblemId(problemId);
        TestDataMigrationReport report = describe(manifest);
        try {
            Path root = legacyRoot(problemId);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return report;
            }
            if (Files.isSymbolicLink(root) || !root.toRealPath().startsWith(baseDirectory.toRealPath())) {
                throw new IOException("测试数据目录越界");
            }
            Map<String, Path> inputs = scanLegacyDirectory(root, "in", ".in", report.getErrors());
            Map<String, Path> outputs = scanLegacyDirectory(root, "out", ".out", report.getErrors());
            Set<String> names = new TreeSet<>(inputs.keySet());
            names.addAll(outputs.keySet());
            for (String name : names) {
                if (!inputs.containsKey(name) || !outputs.containsKey(name)) {
                    report.getErrors().add("文件不配对：" + name);
                    continue;
                }
                TestDataManifest.Entry entry = new TestDataManifest.Entry();
                entry.setId(manifest.getEntries().size() + 1);
                entry.setInput("/in/" + name + ".in");
                entry.setOutput("/out/" + name + ".out");
                entry.setInputSize(Files.size(inputs.get(name)));
                entry.setOutputSize(Files.size(outputs.get(name)));
                entry.setInputSha256(hashFile(inputs.get(name)));
                entry.setOutputSha256(hashFile(outputs.get(name)));
                manifest.getEntries().add(entry);
            }
            validateLegacyInfo(root, manifest, report.getErrors());
            TestDataMigrationReport counts = describe(manifest);
            report.setFileCount(counts.getFileCount());
            report.setTotalBytes(counts.getTotalBytes());
        } catch (IOException e) {
            throw new ProblemException("历史测试数据读取失败", e);
        }
        return report;
    }

    /**
     * 扫描历史输入或输出目录，将单文件问题收集到盘点报告
     * @param root 题目历史根目录
     * @param directory 输入或输出子目录名称
     * @param extension 该目录允许的文件扩展名
     * @param errors 接收盘点错误的列表
     * @return 按不含扩展名的文件名索引的安全路径
     * @throws IOException 目录读取失败或目录路径越界
     */
    private Map<String, Path> scanLegacyDirectory(Path root, String directory, String extension,
                                                 List<String> errors) throws IOException {
        Map<String, Path> result = new HashMap<>();
        Path folder = root.resolve(directory);
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        if (Files.isSymbolicLink(folder) || !folder.toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("历史测试数据目录不能是符号链接");
        }
        try (Stream<Path> stream = Files.list(folder)) {
            for (Path path : stream.sorted().toList()) {
                String filename = path.getFileName().toString();
                if (!filename.endsWith(extension)) {
                    errors.add("目录内存在非测试数据文件：" + directory + "/" + filename);
                    continue;
                }
                try {
                    validateFilename(filename);
                } catch (ProblemException e) {
                    errors.add("无效历史文件：" + directory + "/" + filename);
                    continue;
                }
                Path safe = safeLegacyFile(root, directory + "/" + filename);
                if (Files.size(safe) > ProblemConstants.MAX_TEST_CASE_FILE_SIZE) {
                    errors.add("历史测试数据超过50MB：" + filename);
                    continue;
                }
                result.put(baseName(filename), safe);
            }
        }
        return result;
    }

    /**
     * 将已有 info.json 与实际文件配对、路径和摘要逐项比对
     * @param root 题目历史根目录
     * @param manifest 从实际文件生成的清单
     * @param errors 接收不一致问题的列表
     * @throws IOException 读取历史清单失败
     */
    private void validateLegacyInfo(Path root, TestDataManifest manifest, List<String> errors) throws IOException {
        Path infoPath = root.resolve("info.json");
        if (!Files.exists(infoPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        String content = Files.readString(safeLegacyFile(root, "info.json"), StandardCharsets.UTF_8);
        List<TestDataManifest.Entry> listed;
        try {
            listed = JSON.parseArray(content, TestDataManifest.Entry.class);
        } catch (JSONException e) {
            errors.add("历史info.json无效");
            return;
        }
        if (listed == null) {
            errors.add("历史info.json为空");
            return;
        }
        Map<String, TestDataManifest.Entry> actual = new HashMap<>();
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            actual.put(entry.getInput(), entry);
        }
        Set<String> seen = new HashSet<>();
        for (TestDataManifest.Entry listedEntry : listed) {
            if (listedEntry == null || !seen.add(listedEntry.getInput())) {
                errors.add("历史info.json含重复或无效测试点");
                continue;
            }
            TestDataManifest.Entry entry = actual.get(listedEntry.getInput());
            if (entry == null || !Objects.equals(entry.getOutput(), listedEntry.getOutput())) {
                errors.add("历史info.json与文件不一致");
            } else if ((listedEntry.getInputSha256() != null
                    && !listedEntry.getInputSha256().equals(entry.getInputSha256()))
                    || (listedEntry.getOutputSha256() != null
                    && !listedEntry.getOutputSha256().equals(entry.getOutputSha256()))) {
                errors.add("历史info.json哈希与文件不一致");
            }
        }
        if (!seen.equals(actual.keySet())) {
            errors.add("历史info.json存在缺失或多余测试点");
        }
    }

    /**
     * 在本地私有目录内定位题目历史数据
     * @param problemId 题目编号
     * @return 经过路径边界校验的题目目录
     */
    private Path legacyRoot(Long problemId) {
        return StoragePaths.resolve(baseDirectory, "private/problem/" + problemId);
    }

    /**
     * 校验历史文件为普通文件并限制真实路径位于题目及本地根目录内
     * @param root 题目历史目录
     * @param relativePath 题目目录内相对文件路径
     * @return 已校验的真实文件路径
     * @throws IOException 文件不存在、不是普通文件或真实路径越界
     */
    private Path safeLegacyFile(Path root, String relativePath) throws IOException {
        Path path = StoragePaths.resolve(root, relativePath);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("历史文件无效或越界");
        }
        Path realRoot = root.toRealPath();
        Path realPath = path.toRealPath();
        if (!realRoot.startsWith(baseDirectory.toRealPath()) || !realPath.startsWith(realRoot)) {
            throw new IOException("历史文件路径越界");
        }
        return realPath;
    }

    /**
     * 根据清单汇总迁移报告的身份、文件数与总字节数
     * @param manifest 待汇总的测试数据清单
     * @return 引用原清单的盘点报告
     */
    private TestDataMigrationReport describe(TestDataManifest manifest) {
        TestDataMigrationReport report = new TestDataMigrationReport();
        report.setProblemId(manifest.getProblemId());
        report.setVersionId(manifest.getVersionId());
        report.setManifest(manifest);
        report.setFileCount(manifest.getEntries().size() * 2);
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            report.setTotalBytes(report.getTotalBytes() + entry.getInputSize() + entry.getOutputSize());
        }
        return report;
    }

    /**
     * 拒绝存在盘点问题的历史数据，避免发布不完整版本
     * @param report 历史数据盘点报告
     * @throws ProblemException 报告包含至少一个校验错误
     */
    private void requireValidLegacy(TestDataMigrationReport report) {
        if (!report.getErrors().isEmpty()) {
            throw new ProblemException("历史测试数据校验失败");
        }
    }

    /**
     * 确保新判题任务使用的清单包含测试点
     * @param manifest 已读取的清单
     * @throws ProblemException 清单中没有测试点
     */
    private void requireCases(TestDataManifest manifest) {
        if (manifest.getEntries().isEmpty()) {
            throw new ProblemException("题目尚未配置测试数据");
        }
    }

    /**
     * 校验持久化清单的身份、排序、配对、文件引用、长度和摘要格式
     * @param manifest 反序列化或即将发布的清单
     * @param problemId 预期题目编号
     * @param versionId 预期版本编号
     * @throws ProblemException 清单不满足版本契约
     */
    private void validateManifest(TestDataManifest manifest, Long problemId, Long versionId) {
        if (manifest == null || !Objects.equals(problemId, manifest.getProblemId())
                || !Objects.equals(versionId, manifest.getVersionId()) || manifest.getEntries() == null) {
            throw new ProblemException("测试数据清单版本无效");
        }
        Set<String> inputs = new HashSet<>();
        String previous = null;
        for (int index = 0; index < manifest.getEntries().size(); index++) {
            TestDataManifest.Entry entry = manifest.getEntries().get(index);
            if (entry == null) {
                throw new ProblemException("测试数据清单含无效测试点");
            }
            validateLogicalPath(entry.getInput());
            validateLogicalPath(entry.getOutput());
            if (!entry.getInput().startsWith("/in/") || !entry.getOutput().startsWith("/out/")
                    || !baseName(entry.getInput().substring(4)).equals(baseName(entry.getOutput().substring(5)))
                    || !Integer.valueOf(index + 1).equals(entry.getId()) || !inputs.add(entry.getInput())
                    || (previous != null && previous.compareTo(entry.getInput()) >= 0)
                    || entry.getInputFileId() == null || entry.getOutputFileId() == null
                    || !validSize(entry.getInputSize()) || !validSize(entry.getOutputSize())
                    || !validHash(entry.getInputSha256()) || !validHash(entry.getOutputSha256())) {
                throw new ProblemException("测试数据清单内容无效");
            }
            previous = entry.getInput();
        }
    }

    /**
     * 检查清单中的文件大小是否处于测试数据上限内
     * @param size 文件字节数，可为空
     * @return 是否为 0 到单文件上限之间的有效大小
     */
    private boolean validSize(Long size) {
        return size != null && size >= 0 && size <= ProblemConstants.MAX_TEST_CASE_FILE_SIZE;
    }

    /**
     * 检查清单摘要是否为小写十六进制 SHA-256
     * @param hash 待检查摘要，可为空
     * @return 是否为 64 位小写十六进制文本
     */
    private boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    /**
     * 校验清单逻辑路径及目录与扩展名的对应关系
     * @param path 输入或输出逻辑路径
     * @throws ProblemException 目录、文件名或扩展名不合法
     */
    private void validateLogicalPath(String path) {
        if (path == null || (!path.startsWith("/in/") && !path.startsWith("/out/"))) {
            throw new ProblemException("测试数据文件路径无效");
        }
        String filename = path.substring(path.startsWith("/in/") ? 4 : 5);
        validateFilename(filename);
        if ((path.startsWith("/in/") && !filename.endsWith(".in"))
                || (path.startsWith("/out/") && !filename.endsWith(".out"))) {
            throw new ProblemException("测试数据文件类型与目录不匹配");
        }
    }

    /**
     * 校验测试文件名称，禁止路径分隔、控制字符和非法扩展名
     * @param filename 原始文件名
     * @throws ProblemException 文件名不符合测试数据规则
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 200
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw new ProblemException("测试数据文件名无效");
        }
        for (int index = 0; index < filename.length(); index++) {
            if (Character.isISOControl(filename.charAt(index))) {
                throw new ProblemException("测试数据文件名包含控制字符");
            }
        }
        if ((!filename.endsWith(".in") && !filename.endsWith(".out"))
                || baseName(filename).isBlank() || baseName(filename).equals(".") || baseName(filename).equals("..")) {
            throw new ProblemException("只支持成对的.in和.out测试数据文件");
        }
    }

    /**
     * 取已校验测试文件名的扩展名前部分
     * @param filename 含合法扩展名的测试文件名
     * @return 用于输入输出配对的基础文件名
     */
    private String baseName(String filename) {
        return filename.substring(0, filename.lastIndexOf('.'));
    }

    /**
     * 复用存储摘要工具流式计算历史文件哈希
     * @param path 已校验的本地文件路径
     * @return 小写十六进制 SHA-256
     * @throws IOException 文件读取或关闭失败
     */
    private String hashFile(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return FileDigests.sha256(input);
        }
    }

    /**
     * 按原始 UTF-8 文本计算清单摘要，不重新序列化
     * @param value 清单原文
     * @return 小写十六进制 SHA-256 摘要
     * @throws ProblemException 清单原文缺失
     */
    private String sha256(String value) {
        if (value == null) {
            throw new ProblemException("测试数据版本清单缺失");
        }
        return HexFormat.of().formatHex(FileDigests.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 读取到EOF后校验大小和哈希，不把完整测试数据加载进内存
     */
    private static class VerifiedInputStream extends FilterInputStream {

        private final MessageDigest digest = FileDigests.digest();
        private final long expectedSize;
        private final String expectedHash;
        private long count;
        private boolean verified;

        /**
         * 绑定底层流及清单期望值，读取时累计大小与摘要
         * @param input 底层内容流
         * @param expectedSize 清单记录的文件字节数
         * @param expectedHash 清单记录的 SHA-256 摘要
         */
        VerifiedInputStream(InputStream input, long expectedSize, String expectedHash) {
            super(input);
            this.expectedSize = expectedSize;
            this.expectedHash = expectedHash;
        }

        /**
         * 读取单个字节并累计校验信息，到流末尾时验证摘要
         * @return 无符号字节值，流结束时为 -1
         * @throws IOException 读取失败或大小、摘要不匹配
         */
        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value == -1) {
                verify();
            } else {
                digest.update((byte) value);
                count++;
                checkSize();
            }
            return value;
        }

        /**
         * 读取一段内容并累计校验信息，到流末尾时验证摘要
         * @param buffer 接收内容的缓冲区
         * @param offset 缓冲区写入起点
         * @param length 最大读取字节数
         * @return 实际读取字节数，流结束时为 -1
         * @throws IOException 读取失败或大小、摘要不匹配
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int size = in.read(buffer, offset, length);
            if (size == -1) {
                verify();
            } else if (size > 0) {
                digest.update(buffer, offset, size);
                count += size;
                checkSize();
            }
            return size;
        }

        /**
         * 通过实际读取跳过内容，使跳过的字节同样参与完整性校验
         * @param size 希望跳过的字节数
         * @return 实际跳过字节数，非正数时为 0
         * @throws IOException 读取失败或完整性校验失败
         */
        @Override
        public long skip(long size) throws IOException {
            byte[] buffer = new byte[8192];
            long skipped = 0;
            while (skipped < size) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, size - skipped));
                if (read == -1) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        /**
         * 声明校验流不支持标记和回退，避免摘要重复累计
         * @return 固定为 false
         */
        @Override
        public boolean markSupported() {
            return false;
        }

        /**
         * 拒绝回退已经参与摘要计算的内容
         * @throws IOException 校验流不支持 reset
         */
        @Override
        public void reset() throws IOException {
            throw new IOException("校验流不支持reset");
        }

        /**
         * 拒绝实际读取量超过清单声明的内容
         * @throws IOException 读取字节数超过预期大小
         */
        private void checkSize() throws IOException {
            if (count > expectedSize) {
                throw new IOException("测试数据文件大小校验失败");
            }
        }

        /**
         * 首次到达末尾时核对总长度与 SHA-256 摘要
         * @throws IOException 总长度或摘要与清单不符
         */
        private void verify() throws IOException {
            if (!verified) {
                if (count != expectedSize || !Objects.equals(expectedHash, HexFormat.of().formatHex(digest.digest()))) {
                    throw new IOException("测试数据文件完整性校验失败");
                }
                verified = true;
            }
        }
    }
}

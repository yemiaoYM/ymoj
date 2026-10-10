package com.ymcloud.core.storage;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.enums.FileBizType;
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
import java.security.NoSuchAlgorithmException;
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
 * 在题目行锁内发布版本。对象和清单全部准备完成后才切换当前版本，历史引用始终保留。
 */
@Service
public class TestDataServiceImpl implements TestDataService {

    private final ProblemEntityService problemEntityService;
    private final TestDataVersionEntityService versionEntityService;
    private final StoredFileService storedFileService;
    private final Path baseDirectory;

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
     * 新任务固定版本；尚未迁移的完整历史数据在同一事务中生成初始快照。
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
        try {
            TestDataManifest manifest = JSON.parseObject(version.getManifest(), TestDataManifest.class);
            validateManifest(manifest, problemId, versionId);
            return manifest;
        } catch (ProblemException e) {
            throw e;
        } catch (Exception e) {
            throw new ProblemException("测试数据版本清单无效", e);
        }
    }

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
                    if (file == null || !FileBizType.TEST_DATA.equals(file.getBizType())
                            || !Objects.equals(problemId, file.getBizId())
                            || !Integer.valueOf(0).equals(file.getIsPublic())
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

    @Override
    public List<ProblemCaseInfo> listCurrent(Long problemId) {
        Problem problem = requireProblem(problemId, false);
        return getManifest(problemId, problem.getTestDataVersionId()).getEntries()
                .stream()
                .map(entry -> new ProblemCaseInfo(entry.getId(), entry.getInput(), entry.getOutput()))
                .toList();
    }

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
        } catch (ProblemException e) {
            throw e;
        } catch (IOException e) {
            throw new ProblemException("上传测试数据失败", e);
        }
    }

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
        // 删除只改变新版本清单，不删除仍可能被排队任务引用的对象。
        publish(problemId, new ArrayList<>(entries.values()));
    }

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

    private File saveUpload(Long problemId, MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return storedFileService.save(input, file.getSize(), file.getOriginalFilename(),
                    "text/plain", FileBizType.TEST_DATA, problemId, false);
        }
    }

    private void fillStoredFiles(TestDataManifest.Entry entry, File input, File output) {
        entry.setInputFileId(input.getId());
        entry.setOutputFileId(output.getId());
        entry.setInputSize(input.getFileSize());
        entry.setOutputSize(output.getFileSize());
        entry.setInputSha256(input.getSha256());
        entry.setOutputSha256(output.getSha256());
    }

    private List<TestDataManifest.Entry> importLegacy(TestDataManifest manifest) {
        List<TestDataManifest.Entry> entries = new ArrayList<>();
        for (TestDataManifest.Entry original : manifest.getEntries()) {
            TestDataManifest.Entry entry = JSON.parseObject(JSON.toJSONString(original), TestDataManifest.Entry.class);
            File input = saveLegacy(manifest.getProblemId(), entry.getInput(), entry.getInputSize(), entry.getInputSha256());
            File output = saveLegacy(manifest.getProblemId(), entry.getOutput(), entry.getOutputSize(), entry.getOutputSha256());
            fillStoredFiles(entry, input, output);
            entries.add(entry);
        }
        return entries;
    }

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
        } catch (Exception e) {
            report.getErrors().add("历史数据读取失败：" + e.getMessage());
        }
        return report;
    }

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
                    Path safe = safeLegacyFile(root, directory + "/" + filename);
                    if (Files.size(safe) > ProblemConstants.MAX_TEST_CASE_FILE_SIZE) {
                        errors.add("历史测试数据超过50MB：" + filename);
                        continue;
                    }
                    result.put(baseName(filename), safe);
                } catch (Exception e) {
                    errors.add("无效历史文件：" + directory + "/" + filename);
                }
            }
        }
        return result;
    }

    private void validateLegacyInfo(Path root, TestDataManifest manifest, List<String> errors) throws IOException {
        Path infoPath = root.resolve("info.json");
        if (!Files.exists(infoPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<TestDataManifest.Entry> listed;
        try {
            listed = JSON.parseArray(Files.readString(safeLegacyFile(root, "info.json"), StandardCharsets.UTF_8),
                    TestDataManifest.Entry.class);
            if (listed == null) {
                throw new IllegalArgumentException("清单为空");
            }
        } catch (Exception e) {
            errors.add("历史info.json无效");
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

    private Path legacyRoot(Long problemId) {
        return StoragePaths.resolve(baseDirectory, "private/problem/" + problemId);
    }

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

    private void requireValidLegacy(TestDataMigrationReport report) {
        if (!report.getErrors().isEmpty()) {
            throw new ProblemException("历史测试数据校验失败：" + String.join("；", report.getErrors()));
        }
    }

    private void requireCases(TestDataManifest manifest) {
        if (manifest.getEntries().isEmpty()) {
            throw new ProblemException("题目尚未配置测试数据");
        }
    }

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

    private boolean validSize(Long size) {
        return size != null && size >= 0 && size <= ProblemConstants.MAX_TEST_CASE_FILE_SIZE;
    }

    private boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

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

    private String baseName(String filename) {
        return filename.substring(0, filename.lastIndexOf('.'));
    }

    private String hashFile(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(String value) {
        if (value == null) {
            throw new ProblemException("测试数据版本清单缺失");
        }
        return HexFormat.of().formatHex(newDigest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    /**
     * 读取到EOF后校验大小和哈希，不把完整测试数据加载进内存。
     */
    private static class VerifiedInputStream extends FilterInputStream {

        private final MessageDigest digest = newDigest();
        private final long expectedSize;
        private final String expectedHash;
        private long count;
        private boolean verified;

        VerifiedInputStream(InputStream input, long expectedSize, String expectedHash) {
            super(input);
            this.expectedSize = expectedSize;
            this.expectedHash = expectedHash;
        }

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

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void reset() throws IOException {
            throw new IOException("校验流不支持reset");
        }

        private void checkSize() throws IOException {
            if (count > expectedSize) {
                throw new IOException("测试数据文件大小校验失败");
            }
        }

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

package com.ymcloud.web.service.file.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.client.StorageClient;
import com.ymcloud.core.storage.factory.StorageFactory;
import com.ymcloud.core.storage.model.Options;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemCaseEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.bo.problem.ProblemCaseManifest.Entry;
import com.ymcloud.pojo.bo.problem.ProblemCaseManifest.FileRef;
import com.ymcloud.pojo.bo.problem.ProblemCaseManifest;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemCase;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.admin.ProblemCaseListVO;
import com.ymcloud.web.service.file.ProblemCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 上传不可变测试文件，通过乐观锁发布题目的当前清单
 */
@Service
public class ProblemCaseServiceImpl implements ProblemCaseService {

    private static final String EDIT_CONFLICT = "测试数据已更新，请刷新后重新提交";

    @Autowired
    private ProblemCaseEntityService problemCaseEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private FileEntityService fileEntityService;

    @Autowired
    private StorageFactory storageFactory;

    @Autowired
    private ResourceAccessService access;

    @Autowired
    private TransactionTemplate transaction;

    /**
     * 查询最新发布记录
     * @param problemId 题目主键
     * @return 当前清单行，未发布时为空
     */
    @Override
    public ProblemCase current(Long problemId) {
        return problemCaseEntityService.getById(problemId);
    }

    /**
     * 验证管理归属，从同一行构造列表和编辑基线
     * @param problemId 题目主键
     * @return 测试点与版本
     */
    @Override
    public ProblemCaseListVO list(Long problemId) {
        requireManager(problemId);
        ProblemCase row = current(problemId);
        ProblemCaseListVO view = new ProblemCaseListVO();
        if (row == null) {
            view.setCases(List.of());
        } else {
            view.setCases(JSON.parseObject(row.getManifest(), ProblemCaseManifest.class).getCases());
            view.setVersion(row.getVersion());
        }
        return view;
    }

    /**
     * 验证当前用户对题目的管理权限
     * @param problemId 题目主键
     */
    private void requireManager(Long problemId) {
        Problem problem = problemEntityService.getById(problemId);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        access.requireManageProblem(problem);
    }

    /**
     * 准备指定编辑基线上的条目索引
     * @param problemId 题目主键
     * @param version 客户端打开清单时的版本
     * @return 以测试点名称索引的可编辑条目
     */
    private Map<String, Entry> editable(Long problemId, Long version) {
        ProblemCaseListVO view = list(problemId);
        if (!Objects.equals(version, view.getVersion())) {
            throw new ProblemException(EDIT_CONFLICT, HttpStatus.CONFLICT);
        }
        Map<String, Entry> entries = new HashMap<>();
        for (Entry entry : view.getCases()) {
            entries.put(entry.getName(), entry);
        }
        return entries;
    }

    /**
     * 在数据库事务之外上传变化的文件，全部完成后发布完整清单
     * @param problemId 题目主键
     * @param version 客户端编辑基线
     * @param uploads 成对的测试文件
     * @return 已提交的版本
     */
    @Override
    public Long upload(Long problemId, Long version, MultipartFile[] uploads) {
        Map<String, Entry> entries = editable(problemId, version);
        Map<String, MultipartFile> pairs = validateUploads(uploads);
        List<FileInfo> added = new ArrayList<>();
        StorageClient client = storageFactory.instance();
        for (String filename : pairs.keySet()) {
            if (!filename.endsWith(".in")) {
                continue;
            }
            String name = baseName(filename);
            Entry entry = entries.computeIfAbsent(name, ignored -> new Entry());
            entry.setName(name);
            entry.setInput(storeFile(client, problemId, pairs.get(filename), entry.getInput(), added));
            entry.setOutput(storeFile(client, problemId, pairs.get(name + ".out"), entry.getOutput(), added));
        }
        return publish(problemId, version, entries, added);
    }

    /**
     * 流式核对长度和摘要，复用相同文件或上传新对象
     * @param client 本次发布使用的存储客户端
     * @param problemId 所属题目
     * @param part 上传内容
     * @param previous 原文件引用，可为空
     * @param added 待提交的文件记录
     * @return 本次发布引用的文件
     */
    private FileRef storeFile(StorageClient client, Long problemId, MultipartFile part, FileRef previous, List<FileInfo> added) {
        MessageDigest digest = newDigest();
        long size;
        try (DigestInputStream input = new DigestInputStream(part.getInputStream(), digest)) {
            size = input.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new ProblemException("读取测试文件失败", e);
        }
        if (size != part.getSize()) {
            throw new ProblemException("测试文件长度与声明不一致", HttpStatus.BAD_REQUEST);
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        if (previous != null && previous.getSize() == size && hash.equals(previous.getSha256())) {
            return previous;
        }
        String filename = part.getOriginalFilename();
        FileInfo file = new FileInfo();
        file.setId(IdWorker.getId());
        file.setFileName(client.buildPathKey(filename));
        file.setService(client.clientId());
        file.setOriginalName(filename);
        file.setFileSize(size);
        file.setFileSuffix(filename.substring(filename.lastIndexOf('.')));
        file.setFileType("application/octet-stream");
        file.setBizType(FileBizType.TEST_DATA);
        file.setBizId(problemId);
        file.setCreatedBy(SecurityUtils.getUserId());
        file.setUpdatedBy(file.getCreatedBy());
        try (DigestInputStream input = new DigestInputStream(part.getInputStream(), digest)) {
            var result = client.upload(file.getFileName(), input, size, Options.builder().setContentType(file.getFileType()));
            if (result.getSize() != size || input.read() != -1 || !hash.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new ProblemException("存储的测试文件与上传内容不一致");
            }
            file.setUrl(result.getUrl());
        } catch (IOException e) {
            throw new ProblemException("上传测试文件失败", e);
        }
        added.add(file);
        FileRef reference = new FileRef();
        reference.setFileId(file.getId());
        reference.setSize(Math.toIntExact(size));
        reference.setSha256(hash);
        return reference;
    }

    /**
     * 发布删除后的清单，空清单保留主键和递增版本
     * @param problemId 题目主键
     * @param version 客户端编辑基线
     * @param names 删除的测试点名称
     * @return 已提交的新版本
     */
    @Override
    public Long delete(Long problemId, Long version, List<String> names) {
        Map<String, Entry> entries = editable(problemId, version);
        if (names == null || names.isEmpty()) {
            throw new ProblemException("请选择待删除的测试点", HttpStatus.BAD_REQUEST);
        }
        for (String name : names) {
            if (entries.remove(name) == null) {
                throw new ProblemException("待删除的测试点不存在或重复", HttpStatus.BAD_REQUEST);
            }
        }
        return publish(problemId, version, entries, List.of());
    }

    /**
     * 以客户端旧版本提交清单和新增元数据
     * 并发失败由同一事务回滚
     * @param problemId 题目主键
     * @param version 客户端编辑基线，首次发布为空
     * @param entries 全部待发布的测试点
     * @param added 已完成上传的新文件记录
     * @return 提交成功的清单版本
     */
    private Long publish(Long problemId, Long version, Map<String, Entry> entries, List<FileInfo> added) {
        ProblemCaseManifest manifest = new ProblemCaseManifest();
        manifest.setCases(entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.getName() + ".in"))
                .toList());
        ProblemCase row = new ProblemCase();
        row.setProblemId(problemId);
        row.setVersion(version);
        row.setManifest(JSON.toJSONString(manifest));
        return transaction.execute(status -> {
            if (!added.isEmpty() && !fileEntityService.saveBatch(added)) {
                throw new ProblemException("保存测试文件记录失败");
            }
            if (version == null) {
                row.setVersion(1L);
                try {
                    if (!problemCaseEntityService.save(row)) {
                        throw new ProblemException("保存测试清单失败");
                    }
                } catch (DuplicateKeyException e) {
                    throw new ProblemException(EDIT_CONFLICT, HttpStatus.CONFLICT, e);
                }
            } else if (!problemCaseEntityService.updateById(row)) {
                throw new ProblemException(EDIT_CONFLICT, HttpStatus.CONFLICT);
            }
            return row.getVersion();
        });
    }

    /**
     * 按不可变文件身份读取，旧清单仍可继续读取原文件
     * @param problemId 所属题目
     * @param fileId 文件主键
     * @return 调用方负责关闭的数据流
     */
    @Override
    public InputStream openFile(Long problemId, Long fileId) {
        FileInfo file = fileEntityService.getById(fileId);
        if (file == null || file.getBizType() != FileBizType.TEST_DATA || !problemId.equals(file.getBizId())) {
            throw new ProblemException("测试文件不存在", HttpStatus.NOT_FOUND);
        }
        return storageFactory.instance(file.getService()).open(file.getFileName());
    }

    /**
     * 校验题目归属后读取管理员选定的文件
     * @param problemId 所属题目
     * @param fileId 文件主键
     * @return 调用方负责关闭的文件流
     */
    @Override
    public InputStream openManagedFile(Long problemId, Long fileId) {
        requireManager(problemId);
        return openFile(problemId, fileId);
    }

    /**
     * 为每次清单或数据校验创建独立的 SHA-256 实例
     * @return 新的摘要计算器
     */
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 校验上传文件名称、大小、唯一性及输入输出配对
     * @param files 待上传的文件数组
     * @return 按完整文件名索引的合法上传文件
     * @throws ProblemException 文件为空、超限、重名或不配对
     */
    private Map<String, MultipartFile> validateUploads(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ProblemException("请选择要上传的文件", HttpStatus.BAD_REQUEST);
        }
        Map<String, MultipartFile> uploads = new HashMap<>();
        for (MultipartFile file : files) {
            if (file == null) {
                throw new ProblemException("上传文件无效", HttpStatus.BAD_REQUEST);
            }
            String filename = file.getOriginalFilename();
            validateFilename(filename);
            if (file.getSize() < 0 || file.getSize() > ProblemConstants.MAX_TEST_CASE_FILE_SIZE) {
                throw new ProblemException("单个文件大小不能超过50MB", HttpStatus.PAYLOAD_TOO_LARGE);
            }
            if (uploads.putIfAbsent(filename, file) != null) {
                throw new ProblemException("存在重名文件：" + filename, HttpStatus.BAD_REQUEST);
            }
        }
        for (String filename : uploads.keySet()) {
            String pair = baseName(filename) + (filename.endsWith(".in") ? ".out" : ".in");
            if (!uploads.containsKey(pair)) {
                throw new ProblemException("文件不配对，无法上传", HttpStatus.BAD_REQUEST);
            }
        }
        return uploads;
    }

    /**
     * 校验测试文件名称，禁止路径分隔、控制字符和非法扩展名
     * @param filename 原始文件名
     * @throws ProblemException 文件名不符合测试数据规则
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 200
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw new ProblemException("测试数据文件名无效", HttpStatus.BAD_REQUEST);
        }
        for (int index = 0; index < filename.length(); index++) {
            if (Character.isISOControl(filename.charAt(index))) {
                throw new ProblemException("测试数据文件名包含控制字符", HttpStatus.BAD_REQUEST);
            }
        }
        if ((!filename.endsWith(".in") && !filename.endsWith(".out"))
                || baseName(filename).isBlank() || baseName(filename).equals(".") || baseName(filename).equals("..")) {
            throw new ProblemException("只支持成对的.in和.out测试数据文件", HttpStatus.BAD_REQUEST);
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

}

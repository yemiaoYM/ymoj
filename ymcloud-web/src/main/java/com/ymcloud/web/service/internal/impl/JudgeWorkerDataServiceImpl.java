package com.ymcloud.web.service.internal.impl;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.web.service.internal.JudgeWorkerDataService;
import com.ymcloud.web.service.problem.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Objects;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 校验 Worker 文件的题目、版本和清单归属，隔离数据访问
 */
@Service
public class JudgeWorkerDataServiceImpl implements JudgeWorkerDataService {

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private TestDataVersionEntityService versionEntityService;

    /**
     * 校验题目、版本归属和清单哈希，再读取原始版本记录。
     * @param problemId 正数题目编号
     * @param versionId 属于该题目的版本编号
     * @return 仅供服务端组装清单响应的版本记录
     * @throws ProblemException 编号非法、版本不存在或清单校验失败
     */
    @Override
    public TestDataVersion getVerifiedVersion(Long problemId, Long versionId) {
        requireIds(problemId, versionId);
        testDataService.getManifest(problemId, versionId);
        TestDataVersion version = versionEntityService.lambdaQuery()
                .eq(TestDataVersion::getId, versionId)
                .eq(TestDataVersion::getProblemId, problemId)
                .one();
        if (version == null) {
            throw new ProblemException("测试版本不存在", HttpStatus.NOT_FOUND);
        }
        return version;
    }

    /**
     * 从固定版本清单中解析文件，拒绝读取清单外的文件。
     * @param problemId 正数题目编号
     * @param versionId 属于该题目的版本编号
     * @param fileId 清单中的输入或输出文件编号
     * @return 已校验的文件路径、字节数和哈希
     * @throws ProblemException 版本或文件不存在、清单校验失败或引用不完整
     */
    @Override
    public FileReference resolveFile(Long problemId, Long versionId, Long fileId) {
        requireIds(problemId, versionId);
        if (fileId == null || fileId <= 0) {
            throw new ProblemException("测试文件不存在", HttpStatus.NOT_FOUND);
        }
        TestDataManifest manifest = testDataService.getManifest(problemId, versionId);
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            if (Objects.equals(fileId, entry.getInputFileId())) {
                return requireFile(entry.getInput(), entry.getInputSize(), entry.getInputSha256());
            }
            if (Objects.equals(fileId, entry.getOutputFileId())) {
                return requireFile(entry.getOutput(), entry.getOutputSize(), entry.getOutputSha256());
            }
        }
        throw new ProblemException("测试文件不存在", HttpStatus.NOT_FOUND);
    }

    /**
     * 按服务端已校验的引用打开测试文件。
     * @param problemId 题目编号
     * @param versionId 固定版本编号
     * @param file resolveFile 返回的文件引用，不能来自请求体
     * @return 文件流，由调用方关闭并校验传输长度
     */
    @Override
    public InputStream openFile(Long problemId, Long versionId, FileReference file) {
        return testDataService.openFile(problemId, versionId, file.path());
    }

    /**
     * 拒绝不完整的文件引用。
     * @param path 清单逻辑路径
     * @param size 字节数，须非空且非负
     * @param hash 清单中的 SHA-256
     * @return 内部文件引用
     */
    private FileReference requireFile(String path, Long size, String hash) {
        if (path == null || size == null || size < 0) {
            throw new ProblemException("测试文件不存在", HttpStatus.NOT_FOUND);
        }
        return new FileReference(path, size, hash);
    }

    /**
     * 拒绝非法题目或版本编号。
     * @param problemId 题目编号
     * @param versionId 版本编号
     */
    private void requireIds(Long problemId, Long versionId) {
        if (problemId == null || problemId <= 0 || versionId == null || versionId <= 0) {
            throw new ProblemException("测试版本不存在", HttpStatus.NOT_FOUND);
        }
    }
}

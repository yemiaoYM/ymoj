package com.ymcloud.web.service.problem;

import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 测试数据版本发布及内部读取入口，调用方负责业务鉴权
 */
public interface TestDataService {

    /**
     * 获取可用于新判题任务的固定版本，必要时在事务内迁移完整历史数据
     * @param problemId 已授权的题目编号
     * @return 包含有效测试点的版本编号
     */
    Long getCurrentVersion(Long problemId);

    /**
     * 读取并校验指定版本清单
     * @param problemId 已授权的题目编号
     * @param versionId 固定版本编号，为空时只读盘点历史数据
     * @return 通过身份与完整性校验的清单
     */
    TestDataManifest getManifest(Long problemId, Long versionId);

    /**
     * 打开指定版本内的测试文件并流式核对长度与摘要
     * @param problemId 已授权的题目编号
     * @param versionId 固定版本编号，历史未迁移数据可为空
     * @param logicalPath 清单内的输入或输出逻辑路径
     * @return 由调用方关闭的内容流，完整读取到末尾时完成摘要校验
     */
    InputStream openFile(Long problemId, Long versionId, String logicalPath);

    /**
     * 列出题目当前版本的测试点名称，不返回文件内容
     * @param problemId 已授权的题目编号
     * @return 当前测试点列表，无测试点时为空列表
     */
    List<ProblemCaseInfo> listCurrent(Long problemId);

    /**
     * 保存成对测试文件并发布新版本，同名测试点替换当前条目
     * @param problemId 已授权管理的题目编号
     * @param files 非空且成对的输入、输出文件，单文件最多 50 MiB
     */
    void upload(Long problemId, MultipartFile[] files);

    /**
     * 从新版本中移除成对测试文件，保留旧版本引用的实际对象
     * @param problemId 已授权管理的题目编号
     * @param logicalPaths 非空、无重复且成对的输入与输出逻辑路径
     */
    void delete(Long problemId, List<String> logicalPaths);

    /**
     * 盘点历史目录，获准执行且数据完整时发布首个版本
     * @param problemId 已授权管理的题目编号
     * @param apply 是否实际导入文件并发布版本
     * @return 盘点错误、文件数量与字节数，以及实际迁移状态
     */
    TestDataMigrationReport migrateLegacy(Long problemId, boolean apply);
}

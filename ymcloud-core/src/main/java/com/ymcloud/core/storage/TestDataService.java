package com.ymcloud.core.storage;

import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 测试数据版本发布及内部读取入口。调用方负责业务鉴权。
 */
public interface TestDataService {

    Long getCurrentVersion(Long problemId);

    TestDataManifest getManifest(Long problemId, Long versionId);

    InputStream openFile(Long problemId, Long versionId, String logicalPath);

    List<ProblemCaseInfo> listCurrent(Long problemId);

    void upload(Long problemId, MultipartFile[] files);

    void delete(Long problemId, List<String> logicalPaths);

    TestDataMigrationReport migrateLegacy(Long problemId, boolean apply);
}

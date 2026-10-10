package com.ymcloud.pojo.bo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史测试数据的盘点结果；发现异常时禁止自动发布。
 */
@Data
public class TestDataMigrationReport {

    private Long problemId;
    private Long versionId;
    private boolean applied;
    private int fileCount;
    private long totalBytes;
    private List<String> errors = new ArrayList<>();
    private TestDataManifest manifest;
}

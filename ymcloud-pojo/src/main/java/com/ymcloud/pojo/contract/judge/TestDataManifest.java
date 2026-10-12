package com.ymcloud.pojo.contract.judge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 私有测试数据版本清单。哈希针对原始 UTF-8 JSON，不能先重新序列化。
 */
@Data
public class TestDataManifest {

    private Long problemId;
    private Long versionId;
    private List<Entry> entries = new ArrayList<>();

    @Data
    public static class Entry {

        private Integer id;
        private String input;
        private String output;
        private Long inputFileId;
        private Long outputFileId;
        private Long inputSize;
        private Long outputSize;
        private String inputSha256;
        private String outputSha256;
    }
}

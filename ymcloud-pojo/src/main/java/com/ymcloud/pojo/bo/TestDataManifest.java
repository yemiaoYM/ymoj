package com.ymcloud.pojo.bo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本清单中的逻辑路径与不可变私有文件引用。
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

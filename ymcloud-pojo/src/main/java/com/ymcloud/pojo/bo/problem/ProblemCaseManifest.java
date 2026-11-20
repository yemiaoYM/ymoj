package com.ymcloud.pojo.bo.problem;

import lombok.Data;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 按执行顺序排列的题目测试点与不可变文件引用
 */
@Data
public class ProblemCaseManifest {

    /**
     * 按判题顺序排列的测试点，清空后为空列表
     */
    private List<Entry> cases;

    /**
     * 一个测试点，输入和预期输出均为必填
     */
    @Data
    public static class Entry {

        /**
         * 同一清单内唯一的测试点名称，取配对文件去掉扩展名后的名称
         */
        private String name;

        /**
         * 测试点的输入文件引用
         */
        private FileRef input;

        /**
         * 测试点的预期输出文件引用
         */
        private FileRef output;
    }

    /**
     * 不可变的文件主键、原始字节数和文件摘要
     */
    @Data
    public static class FileRef {

        /**
         * 测试文件的雪花主键，对应 file.id，在持久化清单中使用 JSON 整数
         */
        private Long fileId;

        /**
         * 原始文件的字节数，允许为 0，最大为 50 MiB
         */
        private Integer size;

        /**
         * 原始文件字节的 SHA-256 摘要，使用 64 位小写十六进制表示
         */
        private String sha256;
    }
}

package com.ymcloud.judge.data;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Worker 只通过受限服务接口读取版本数据，不持有数据库或对象存储凭据
 */
public interface TestDataSource {

    /**
     * 条件读取当前清单
     * @param problemId 正数题目 ID
     * @param version 缓存版本，首次读取为空
     * @return 发布版本和有界清单，未修改时正文为空
     * @throws IOException 版本不可用、传输失败或超限
     */
    ManifestResponse fetchManifest(Long problemId, Long version) throws IOException;

    /**
     * 按题目和不可变文件 ID 打开数据流，调用方必须关闭
     * @param problemId 正数题目 ID
     * @param fileId 清单中的正数文件 ID
     * @return 有截止时间的数据流
     * @throws IOException 资源无权访问或传输失败
     */
    InputStream openFile(Long problemId, Long fileId) throws IOException;

    /**
     * 当前清单响应，304 时仅携带已确认的版本号
     */
    @lombok.Value
    class ManifestResponse {

        /**
         * 服务端确认的当前清单版本
         */
        Long version;

        /**
         * 清单的 UTF-8 原始字节，返回 HTTP 304 时为 null
         */
        byte[] body;
    }
}

package com.ymcloud.judge.data;

import java.io.IOException;
import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Worker 只通过受限服务接口读取版本数据，不持有数据库或对象存储凭据。
 */
public interface TestDataSource {

    /**
     * 读取指定版本的有界原始清单。
     * @param problemId 正数题目 ID
     * @param versionId 正数版本 ID
     * @return 原始 JSON 字节，不重新序列化
     * @throws IOException 版本不可用、传输失败或超限
     */
    byte[] fetchManifest(Long problemId, Long versionId) throws IOException;

    /**
     * 打开指定版本的文件，调用方必须关闭。
     * @param problemId 正数题目 ID
     * @param versionId 正数版本 ID
     * @param fileId 清单中的正数文件 ID
     * @return 有截止时间的数据流
     * @throws IOException 资源无权访问或传输失败
     */
    InputStream openFile(Long problemId, Long versionId, Long fileId) throws IOException;
}

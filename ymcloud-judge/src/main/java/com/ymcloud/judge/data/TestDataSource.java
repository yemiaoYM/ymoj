package com.ymcloud.judge.data;

import java.io.IOException;
import java.io.InputStream;

/**
 * Worker 只通过受限服务接口读取版本数据，不持有数据库或对象存储凭据。
 */
public interface TestDataSource {

    byte[] fetchManifest(Long problemId, Long versionId) throws IOException;

    InputStream openFile(Long problemId, Long versionId, Long fileId) throws IOException;
}

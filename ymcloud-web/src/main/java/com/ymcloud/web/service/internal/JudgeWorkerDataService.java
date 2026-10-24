package com.ymcloud.web.service.internal;

import com.ymcloud.pojo.entity.TestDataVersion;

import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: Worker 测试数据读取契约，调用方须通过独立服务身份鉴权
 */
public interface JudgeWorkerDataService {

    /**
     * 验证题目与版本归属及清单哈希，保留原始清单。
     * @param problemId 正数题目编号
     * @param versionId 属于该题目的版本编号
     * @return 内部版本记录，不直接序列化为 HTTP 响应
     */
    TestDataVersion getVerifiedVersion(Long problemId, Long versionId);

    /**
     * 解析清单中的文件，拒绝跨版本引用。
     * @param problemId 正数题目编号
     * @param versionId 已发布版本编号
     * @param fileId 清单中的文件编号
     * @return 经过版本检查的文件引用
     */
    FileReference resolveFile(Long problemId, Long versionId, Long fileId);

    /**
     * 按已校验引用打开测试文件。
     * @param problemId 题目编号
     * @param versionId 固定版本编号
     * @param file 服务端解析的文件引用，不能由请求绑定生成
     * @return 文件流，由调用方关闭并校验传输长度
     */
    InputStream openFile(Long problemId, Long versionId, FileReference file);

    /** 仅供内部流式传输使用；路径不对外公开，长度单位为字节。 */
    record FileReference(String path, long size, String sha256) {
    }
}

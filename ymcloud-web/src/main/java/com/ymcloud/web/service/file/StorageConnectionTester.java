package com.ymcloud.web.service.file;

import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.pojo.entity.File;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 存储配置连通性和已有对象验证入口
 */
public interface StorageConnectionTester {

    /**
     * 只读验证全部引用对象的大小和哈希
     * @param profile 待验证的内部客户端配置
     * @param files 需核验的全部历史文件引用
     */
    void verifyReferencedFiles(StorageProperties.Profile profile, List<File> files);

    /**
     * 使用独立客户端测试随机对象并清理探测资源
     * @param profile 待验证的内部客户端配置
     */
    void test(StorageProperties.Profile profile);
}

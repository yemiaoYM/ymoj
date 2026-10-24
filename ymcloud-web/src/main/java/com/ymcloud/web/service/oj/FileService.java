package com.ymcloud.web.service.oj;

import com.ymcloud.pojo.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: FileService
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  23:02
 * @Description: FileService
 */
public interface FileService {

    /**
     * 校验公开路径和文件访问条件，不回退历史目录。
     * @param path 公开 URL 前缀后的路径，包含开头的斜杠
     * @return 已登记且允许公开读取的文件元数据，仅在服务端使用
     */
    File resolvePublicFile(String path);

    /**
     * 打开已由 resolvePublicFile 验证的文件。
     * @param file 已验证的公开文件元数据，不能来自请求体
     * @return 文件流，由调用方或 HTTP 资源处理器关闭
     */
    InputStream openPublicFile(File file);

    /**
     * 保存文件并插入数据库
     * @param file 源文件
     * @param fileRecord 文件记录
     */
    void saveFile(MultipartFile file, File fileRecord);

    /**
     * 根据url删除文件并删除数据库中文件
     * @param url url
     */
    void deleteFileByUrl(String url);
}

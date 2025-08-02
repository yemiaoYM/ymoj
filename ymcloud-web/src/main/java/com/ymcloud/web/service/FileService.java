package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import org.springframework.web.multipart.MultipartFile;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: FileService
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  23:02
 * @Description: FileService
 */
public interface FileService extends IService<File> {

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

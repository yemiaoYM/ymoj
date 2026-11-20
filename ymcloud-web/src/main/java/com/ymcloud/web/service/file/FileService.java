package com.ymcloud.web.service.file;

import com.ymcloud.pojo.bo.FileUploadBO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 文件应用服务，负责元数据和对象生命周期
 */
public interface FileService {

    /**
     * 分配文件主键及随机对象位置，保存文件并管理事务回滚清理
     * @param request 业务调用方已授权的创建参数
     * @param input 调用方负责关闭的内容流
     * @param size 声明的上传字节数
     * @return 已保存的文件元数据
     */
    FileInfo upload(FileUploadBO request, InputStream input, long size);

    /**
     * 按主键查询已登记文件
     * @param fileId 文件主键
     * @return 已存在的文件元数据
     */
    FileInfo getById(Long fileId);

    /**
     * 打开文件
     * @param fileId 文件id
     * @return 调用方负责关闭的文件流
     */
    ResponseEntity<Resource> open(Long fileId);

    /**
     * 下载文件
     * @param fileId 文件id
     */
    ResponseEntity<Resource> download(Long fileId);

    /**
     * 删除普通文件登记并在提交后清理对象
     * @param fileIds 内部文件编号
     */
    void delete(List<Long> fileIds);
}

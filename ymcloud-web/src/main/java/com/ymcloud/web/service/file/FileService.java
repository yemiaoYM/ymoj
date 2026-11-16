package com.ymcloud.web.service.file;

import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 文件应用服务，负责元数据和对象生命周期
 */
public interface FileService {

    /**
     * 创建独立公开标识及随机对象位置，保存文件并管理事务回滚清理
     * @param command 业务调用方已授权的创建参数
     * @param input 调用方负责关闭的内容流
     * @param size 声明的上传字节数
     * @return 已保存的文件元数据
     */
    File create(CreateStoredFileCommand command, InputStream input, long size);

    /**
     * 按内部编号获取文件，不存在时抛出文件异常
     * @param id 内部文件编号
     * @return 已存在的文件元数据
     */
    File get(Long id);

    /**
     * 按对外 UUIDv4 查询元数据，业务调用方负责授权
     * @param publicId 32 位小写 UUIDv4 标识
     * @return 已登记文件
     */
    File getByPublicId(String publicId);

    /**
     * 按已登记公开 URL 查询文件
     * @param url 已登记的公开 URL
     * @return 公开文件元数据，不存在时为空
     */
    File resolvePublicUrl(String url);

    /**
     * 打开已授权文件，由调用方关闭流
     * @param file 已授权的文件元数据
     * @return 调用方负责关闭的文件流
     */
    InputStream open(File file);

    /**
     * 删除普通文件登记并在提交后清理对象
     * @param id 内部文件编号
     */
    void delete(Long id);

    /**
     * 校验公开路径和文件访问条件，不回退历史目录
     * @param path 公开 URL 前缀后的路径，包含开头的斜杠
     * @return 已登记且允许公开读取的文件元数据，仅在服务端使用
     */
    File resolvePublicFile(String path);

    /**
     * 保存文件并插入数据库
     * @param file 源文件
     * @param fileRecord 文件记录
     * @return 已保存的头像文件记录
     */
    File saveFile(MultipartFile file, File fileRecord);

    /**
     * 删除已登记的公开文件，事务提交后清理对象与相关缓存
     * @param url 已登记的公开文件 URL，未找到时不做操作
     */
    void deleteFileByUrl(String url);
}

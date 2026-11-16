package com.ymcloud.web.service.file;

import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;

import java.io.InputStream;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 文件应用服务，负责元数据和对象生命周期
 */
public interface StoredFileService {

    /**
     * 保存文件对象及元数据，保留事务回滚清理
     * @param input 调用方负责关闭的上传流
     * @param size 声明的上传字节数
     * @param originalName 不包含目录的原始文件名
     * @param contentType 上传媒体类型
     * @param bizType 调用方授权的业务类型
     * @param bizId 关联业务编号，无关联时可为空
     * @param publicAccess 是否公开，测试数据必须为私有
     * @return 已保存的文件元数据
     */
    File save(InputStream input, long size, String originalName, String contentType,
              FileBizType bizType, Long bizId, boolean publicAccess);

    /**
     * 保存文件对象及元数据，保留事务回滚清理
     * @param input 调用方负责关闭的上传流
     * @param size 声明的上传字节数
     * @param file 已授权的文件元数据
     * @return 已保存的文件元数据
     */
    File save(InputStream input, long size, File file);

    /**
     * 按内部编号获取文件，不存在时抛出文件异常
     * @param id 内部文件编号
     * @return 已存在的文件元数据
     */
    File get(Long id);

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
     * 打开迁移期间保留的历史公开文件
     * @param relativePath 历史公开目录内的相对路径
     * @return 调用方负责关闭的历史文件流
     */
    InputStream openLegacyPublic(String relativePath);

    /**
     * 删除普通文件登记并在提交后清理对象
     * @param id 内部文件编号
     */
    void delete(Long id);

    /**
     * 列出尚未迁移的本地文件记录
     * @return 待迁移的本地文件列表
     */
    List<File> legacyInventory();

    /**
     * 核验或迁移历史本地文件并保留原始对象
     * @param id 内部文件编号
     * @param apply 是否执行迁移，false 时只核验
     * @return 核验或迁移后的文件记录
     */
    File migrateLocal(Long id, boolean apply);
}

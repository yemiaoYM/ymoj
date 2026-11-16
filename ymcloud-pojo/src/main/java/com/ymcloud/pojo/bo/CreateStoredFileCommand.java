package com.ymcloud.pojo.bo;

import com.ymcloud.pojo.enums.FileBizType;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-17
 * @Description: 调用方已授权的文件创建参数，不接受客户端指定的对象位置或公开标识
 * @param fileName 展示和下载使用的原始文件名
 * @param contentType 声明或识别出的媒体类型
 * @param namespace 业务指定的相对命名空间，不包含原始文件名
 * @param bizType 文件所属业务类别
 * @param bizId 业务内部编号，未绑定资源时为空
 * @param publicAccess 是否允许存入公开文件空间
 * @param uploaderId 服务端取得的上传者内部编号，系统导入可为空
 * @param uploaderUsername 上传者名称，系统导入可为空
 */
public record CreateStoredFileCommand(String fileName, String contentType, String namespace,
                                      FileBizType bizType, Long bizId, boolean publicAccess,
                                      Long uploaderId, String uploaderUsername) {}

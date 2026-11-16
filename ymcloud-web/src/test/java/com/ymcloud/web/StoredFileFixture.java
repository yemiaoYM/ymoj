package com.ymcloud.web;

import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-17
 * @Description: 图片及文件入口测试共用的存储返回夹具，不模拟对象 I/O 或数据库事务
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StoredFileFixture {

    /**
     * 将入口捕获的创建命令转换为存储替身的返回记录
     * @param command 测试捕获的实际创建命令
     * @return 带独立公开标识的文件记录，内部编号由具体夹具按需设置
     */
    static File saved(CreateStoredFileCommand command) {
        File file = new File();
        file.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        file.setFileName(command.fileName());
        file.setFileType(command.contentType());
        file.setBizType(command.bizType());
        file.setBizId(command.bizId());
        file.setIsPublic(command.publicAccess() ? 1 : 0);
        file.setUploaderId(command.uploaderId());
        file.setUploaderUsername(command.uploaderUsername());
        return file;
    }
}

package com.ymcloud.common.exception.file;

/**
 * @PackageName: com.ymcloud.common.exception.file
 * @ClassName: FileNotExistsException
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  21:23
 * @Description: 文件不存在或已删除异常
 */
public class FileNotExistsException extends FileException{

    public FileNotExistsException() {
        super("文件不存在或已删除");
    }

}

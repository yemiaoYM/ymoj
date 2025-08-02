package com.ymcloud.common.exception.file;

/**
 * @PackageName: com.ymcloud.common.exception.file
 * @ClassName: FileEmptyException
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  21:25
 * @Description: 文件大小为空异常
 */
public class FileEmptyException extends FileException{

    public FileEmptyException() {
        super("文件大小为空");
    }

    public FileEmptyException(String message) {
        super(message);
    }

}

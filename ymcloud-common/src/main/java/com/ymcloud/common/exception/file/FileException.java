package com.ymcloud.common.exception.file;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception
 * @ClassName: FileException
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  21:20
 * @Description: 文件相关异常
 */
public class FileException extends ServiceException {

    public FileException(){
        super("文件操作异常");
    }

    public FileException(String message) {
        super(message);
    }

    public FileException(String message, Integer code) {
        super(message, code);
    }

}

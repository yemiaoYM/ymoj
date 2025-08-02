package com.ymcloud.common.exception.file;

/**
 * @PackageName: com.ymcloud.common.exception.file
 * @ClassName: FileTypeNotMatchException
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:37
 * @Description: 文件类型不匹配异常
 */
public class FileTypeNotMatchException extends FileException{

    public FileTypeNotMatchException() {
        super("文件类型不支持或不匹配");
    }

    public FileTypeNotMatchException(String ext) {
        super(ext+"文件类型不支持或不匹配");
    }

    public FileTypeNotMatchException(String ...ext) {
        super(mergeExt(ext));
    }

    private static String mergeExt(String ...ext){
        StringBuilder msg= new StringBuilder("文件类型不支持或不匹配");
        for(String extName:ext){
            msg.insert(0, extName + ",");
        }
        return msg.toString();
    }

}

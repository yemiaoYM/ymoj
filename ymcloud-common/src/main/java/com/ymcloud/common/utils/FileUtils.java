package com.ymcloud.common.utils;

import cn.hutool.core.io.FileUtil;
import com.ymcloud.common.exception.file.FileException;

import java.io.File;
import java.util.List;

/**
 * @PackageName: com.ymcloud.common.utils
 * @ClassName: FileUtils
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  23:26
 * @Description: 通用文件工具操作类
 */
public class FileUtils {

    /**
     * 文件内容是否为空
     * @param path 文件路径
     * @return 是否为空
     */
    public static Boolean empty(String path) {
        try{
            return FileUtil.isEmpty(FileUtil.file(path));
        }catch (Exception e){
            return true;
        }
    }

    /**
     * 文件是否存在
     * @param path 文件路径
     * @return 是否存在
     */
    public static Boolean exists(String path) {
        try{
            return FileUtil.exist(path);
        }catch (Exception e){
            return false;
        }
    }

    /**
     * 创建文件
     * @param fileName 文件名
     * @return 如果存在返回当前文件，如果不存在则创建并且返回
     */
    public static File createFile(String fileName) {
        try{
            return FileUtil.touch(fileName);
        }catch (Exception e){
            throw new FileException("创建文件失败",e);
        }
    }

    /**
     * 将内容写入文件
     * @param fileName 文件名
     * @param content 内容
     */
    public static void writeFile(String fileName, String content){
        try{
            //如果不存在，自动创建文件
            FileUtil.writeUtf8String(content,fileName);
        }catch (Exception e){
            throw new FileException("文件写入异常",e);
        }
    }

    /**
     * 读取文件内容
     * @param fileName 文件名
     * @return 内容以字符串整体返回
     */
    public static String readFileString(String fileName){
        try{
            return FileUtil.readUtf8String(fileName);
        }catch (Exception e){
            throw new FileException("读取文件失败",e);
        }
    }

    /**
     * 读取文件内容
     * @param fileName 文件名
     * @return 将每行内容封装为list返回
     */
    public static List<String> readFileLines(String fileName){
        try{
            return FileUtil.readUtf8Lines(fileName);
        }catch (Exception e){
            throw new FileException("读取文件失败",e);
        }
    }

    /**
     * 根据文件后缀返回当前文件夹中的数量
     * @param fileName 文件名
     * @param suffix 后缀名
     * @return 数量
     */
    public static Long countFileBySuffix(String fileName,String suffix){
        try{
            return FileUtil.listFileNames(fileName)
                    .stream().filter(name->name.endsWith(suffix)).count();
        }catch (Exception e){
            return 0L;
        }
    }

}

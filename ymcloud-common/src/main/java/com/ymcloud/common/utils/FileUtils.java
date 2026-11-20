package com.ymcloud.common.utils;

import cn.hutool.core.io.FileUtil;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @PackageName: com.ymcloud.common.utils
 * @ClassName: FileUtils
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  23:26
 * @Description: 通用文件工具操作类
 */
public class FileUtils {

    /**
     * 读取图片头并返回实际格式，供上传和远程题面导入校验原图
     * @param input 图片输入流，由调用方关闭
     * @param formats 当前业务允许的图片格式
     * @return 标准小写格式名，如 jpeg、png 或 webp
     * @throws FileException 图片格式不受支持、内容损坏或读取失败
     */
    public static String getImageFormat(InputStream input, Set<String> formats) {
        try (var stream = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new FileException("文件内容不是受支持的图片", HttpStatus.UNSUPPORTED_TYPE);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!formats.contains(format)) {
                    throw new FileException("不支持的图片格式", HttpStatus.UNSUPPORTED_TYPE);
                }
                reader.setInput(stream);
                reader.getWidth(0);
                reader.getHeight(0);
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IIOException | EOFException e) {
            throw new FileException("图片内容不完整或已损坏", HttpStatus.UNSUPPORTED_TYPE, e);
        } catch (IOException e) {
            throw new FileException("图片读取失败", HttpStatus.ERROR, e);
        }
    }

    /**
     * 自动根据环境获取文件路径
     * @param path 文件路径
     * @return 文件路径
     */
    public static String getAbsolutePath(String path) {
        try{
            return FileUtil.getCanonicalPath(new File(path));
        }catch (Exception e){
            throw new FileException("获取文件路径失败",e);
        }
    }

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
     * 删除文件
     * @param fileName 文件名
     * @return 是否删除成功
     */
    public static Boolean deleteFile(String fileName){
        try{
            return FileUtil.del(fileName);
        }catch (Exception e){
            return false;
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

    /**
     * 返回文件夹中文件的数量
     * @param fileName 文件路径
     * @return 文件数量
     */
    public static int countFile(String fileName){
        try{
            return FileUtil.listFileNames(fileName).size();
        }catch (Exception e){
            return 0;
        }
    }

}

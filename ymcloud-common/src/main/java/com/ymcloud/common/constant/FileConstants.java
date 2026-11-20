package com.ymcloud.common.constant;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:02
 * @Description: 文件相关常量
 */
public class FileConstants {

    /**
     * 公开文件访问前缀，后接与存储位置无关的 fileId
     */
    public static final String PUBLIC_URL_PREFIX = "/api/files/";

    /**
     * 单张图片允许上传的最大字节数
     */
    public static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    /**
     * 公共文件夹
     */
    public static final String DIR_PUBLIC = "public";
    /**
     * 私有文件夹
     */
    public static final String DIR_PRIVATE = "private";
    /**
     * 临时文件夹
     */
    public static final String DIR_TMP = "tmp";
}

package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: FileConstants
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  22:02
 * @Description: 文件相关常量
 */
public class FileConstants {

    /**
     * 本地存储
     */
    public static final int STORAGE_LOCAL = 0;
    /**
     * 阿里云 OSS
     */
    public static final int STORAGE_OSS = 1;
    /**
     * MinIO
     */
    public static final int STORAGE_MINIO = 2;
    /**
     * 私有文件（需权限访问）
     */
    public static final int ACCESS_PRIVATE = 0;
    /**
     * 公开文件（直接访问）
     */
    public static final int ACCESS_PUBLIC = 1;
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
    /**
     * 题目业务子目录
     */
    public static final String SUB_DIR_PROBLEM = "problem";
    /**
     * 比赛业务子目录
     */
    public static final String SUB_DIR_CONTEST = "contest";
    /**
     * 公告业务子目录
     */
    public static final String SUB_DIR_ANNOUNCEMENT = "announcement";
    /**
     * 图片图像业务子目录
     */
    public static final String SUB_DIR_IMAGE = "image";
    /**
     * 视频业务子目录
     */
    public static final String SUB_DIR_VIDEO = "video";

}

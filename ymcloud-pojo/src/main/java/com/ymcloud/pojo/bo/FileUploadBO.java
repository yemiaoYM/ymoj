package com.ymcloud.pojo.bo;

import com.ymcloud.pojo.enums.FileBizType;
import lombok.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-17
 * @Description: 已授权的文件创建参数，对象位置和公开标识由文件服务分配
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileUploadBO {

    /**
     * 展示和下载使用的原始文件名
     */
    private String fileName;

    /**
     * 声明或识别出的媒体类型
     */
    private String contentType;

    /**
     * 文件所属业务类别
     */
    private FileBizType bizType = FileBizType.GENERAL;

    /**
     * 业务内部编号，未绑定资源时为空
     */
    private Long bizId;

    /**
     * 服务端取得的上传者 ID，系统导入可为空
     */
    private Long uploaderId;

}

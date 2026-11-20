package com.ymcloud.pojo.bo;

import com.ymcloud.pojo.enums.FileBizType;
import lombok.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19  14:42
 * @Description: 图片上传
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ImageUploadBO  {

    /**
     * 文件所属业务类别
     */
    @Builder.Default
    private FileBizType bizType = FileBizType.GENERAL;

    /**
     * 业务内部编号，未绑定资源时为空
     */
    private Long bizId;

    /**
     * 服务端取得的上传者 ID，系统导入可为空
     */
    private Long uploaderId;

    /**
     * 文件大小限制
     * 默认最大3MB
     */
    @Builder.Default
    private int maxBytes = 3 * 1024 * 1024;

}

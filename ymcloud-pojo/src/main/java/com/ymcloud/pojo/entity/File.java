package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.FileBizType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="文件表")
public class File {
    @TableId(type = IdType.AUTO)
    @Schema(description="文件ID")
    private Long id;
    @Schema(description="原始文件名，例如 data.zip")
    private String fileName;
    @Schema(description="文件类型/MIME类型，例如 application/zip、image/png")
    private String fileType;
    @Schema(description="文件大小（字节）")
    private Long fileSize;
    @Schema(description="文件扩展名，例如 zip、png、pdf")
    private String fileExt;
    @Schema(description="文件存储相对路径，例如 public/problem/1001/data.zip")
    private String storagePath;
    @Schema(description="存储方式：0=本地，1=OSS，2=MinIO 等")
    private Integer storageType;
    @Schema(description="文件可访问URL（如果是OSS/MinIO则存完整URL）")
    private String url;
    @Schema(description="上传者用户ID")
    private Long uploaderId;
    @Schema(description="上传者用户名")
    private String uploaderUsername;
    @Schema(description="业务类型：0=通用，1=题目附件，2=题目数据包，3=比赛附件，4=公告附件，5=图像图片")
    private FileBizType bizType;
    @Schema(description="业务ID，例如题目ID、比赛ID")
    private Long bizId;
    @Schema(description="是否公开访问：0=私有，1=公开")
    private Integer isPublic;
    @Schema(description="上传时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

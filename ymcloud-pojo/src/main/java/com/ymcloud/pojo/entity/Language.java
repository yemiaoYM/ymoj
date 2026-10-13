package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 编程语言表。 */
@Data
public class Language {

    /** 语言ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 固定语言入口：Java、C、C++ 或 Python */
    private String name;

    /** 历史镜像兼容元数据，不用于执行或镜像校验 */
    private String image;

    /** 历史运行环境兼容元数据，不用于执行或镜像校验 */
    private String env;

    /** 语言版本信息，如 g++ 9.4 / Python 3.10 */
    private String version;

    /** MIME类型，用于前端代码高亮，如 text/x-c++src */
    private String contentType;

    /** 默认文件名，如main.py,Main.java,main.cpp,main.c */
    private String fileName;

    /** 默认文件扩展名，如 .cpp、.py */
    private String fileExtension;

    /** 历史编译指令兼容元数据，Worker 使用固定工具链命令 */
    private String compileCmd;

    /** 历史运行指令兼容元数据，Worker 使用固定语言入口 */
    private String runCmd;

    /** 语言默认代码模板，显示在编辑器中 */
    private String templateCode;

    /** 题面中用于填空题的嵌套模板 */
    private String codeTemplate;

    /** 语言类型：0=编译型，1=解释型 */
    private Integer type;

    /** 启用状态：1=启用，0=停用 */
    private Integer status;

    /** 归属 OJ 标识，本地平台为 myy */
    private String oj;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}

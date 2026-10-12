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

    /** 语言名称，如 C++ 17 */
    private String name;

    /** docker语言镜像，如：gcc:10,openjdk:17-slim */
    private String image;

    /** docker自定义镜像环境,如oj-cpp-env */
    private String env;

    /** 语言版本信息，如 g++ 9.4 / Python 3.10 */
    private String version;

    /** MIME类型，用于前端代码高亮，如 text/x-c++src */
    private String contentType;

    /** 默认文件名，如main.py,Main.java,main.cpp,main.c */
    private String fileName;

    /** 默认文件扩展名，如 .cpp、.py */
    private String fileExtension;

    /** 编译指令，编译型语言需要，如 g++ main.cpp -o main */
    private String compileCmd;

    /** 运行指令，如 ./main、python3 main.py */
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

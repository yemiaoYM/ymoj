package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="编程语言表")
public class Language {

    @TableId(type = IdType.AUTO)
    @Schema(description="语言ID")
    private Long id;
    @Schema(description="语言名称，如 C++ 17")
    private String name;
    @Schema(description="docker语言镜像，如：gcc:10,openjdk:17-slim")
    private String image;
    @Schema(description="docker自定义镜像环境,如oj-cpp-env")
    private String env;
    @Schema(description="语言版本信息，如 g++ 9.4 / Python 3.10")
    private String version;
    @Schema(description="MIME类型，用于前端代码高亮，如 text/x-c++src")
    private String contentType;
    @Schema(description="默认文件名，如main.py,Main.java,main.cpp,main.c")
    private String fileName;
    @Schema(description="默认文件扩展名，如 .cpp、.py")
    private String fileExtension;
    @Schema(description="编译指令，编译型语言需要，如 g++ main.cpp -o main")
    private String compileCmd;
    @Schema(description="运行指令，如 ./main、python3 main.py")
    private String runCmd;
    @Schema(description="语言默认代码模板，显示在编辑器中")
    private String templateCode;
    @Schema(description="题面中用于填空题的嵌套模板")
    private String codeTemplate;
    @Schema(description="语言类型：0=编译型，1=解释型")
    private Integer type;
    @Schema(description="启用状态：1=启用，0=停用")
    private Integer status;
    @Schema(description="归属OJ标识，自身平台填myy")
    private String oj;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}

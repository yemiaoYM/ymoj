package com.ymcloud.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: LanguageTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-04  20:12
 * @Description: 展示语言实体
 */
@Data
public class LanguageTableVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 语言名称，如 C++ 17
     */
    private String name;
    /**
     * 语言版本信息，如 g++ 9.4 / Python 3.10
     */
    private String version;
    /**
     * MIME类型，用于前端代码高亮，如 text/x-c++src
     */
    private String contentType;
    /**
     * 编译指令，编译型语言需要，如 g++ main.cpp -o main
     */
    private String compileCmd;
    /**
     * 语言默认代码模板，显示在编辑器中
     */
    private String templateCode;
}

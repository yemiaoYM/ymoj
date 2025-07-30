package com.ymcloud.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: LanguageVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-24  20:19
 * @Description: 前端编程语言选项实体
 */
@Data
public class LanguageVO implements Serializable {
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
     * 语言默认代码模板，显示在编辑器中
     */
    private String templateCode;
    /**
     * 题面中用于填空题的嵌套模板
     */
    private String codeTemplate;
}

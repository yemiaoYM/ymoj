package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交时固定的语言配置，不包含数据库或用户信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeLanguage {

    private String name;
    private String image;
    private String env;
    private String version;
    private String contentType;
    private String fileName;
    private String fileExtension;
    private String compileCmd;
    private String runCmd;
    private Integer type;
}

package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交时固定的语言配置，不包含数据库或用户信息。
 * 历史镜像、环境和命令原样保留为兼容元数据，不参与执行或镜像校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeLanguage {

    private String name;

    /** 历史镜像兼容元数据 */
    private String image;

    /** 历史运行环境兼容元数据 */
    private String env;

    private String version;
    private String contentType;
    private String fileName;
    private String fileExtension;

    /** 历史编译指令兼容元数据，实际命令由 Worker 固定 */
    private String compileCmd;

    /** 历史运行指令兼容元数据，实际命令由 Worker 固定 */
    private String runCmd;

    private Integer type;
}

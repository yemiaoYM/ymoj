package com.ymcloud.pojo.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class JudgePageQuery extends PageQuery implements Serializable {
    private String username;
    private String problemTitle;
    private Integer statusCode;
    private Boolean share=true;
}

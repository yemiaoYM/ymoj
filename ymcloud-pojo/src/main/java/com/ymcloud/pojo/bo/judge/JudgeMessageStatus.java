package com.ymcloud.pojo.bo.judge;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.ymcloud.pojo.enums.JudgeStatus;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 整次执行和单个测试点共用的判题消息状态编解码
 */
public interface JudgeMessageStatus {

    /**
     * 读取业务判题状态
     * @return 本次判定，尚未设置时为空
     */
    @JsonIgnore
    JudgeStatus getStatus();

    /**
     * 记录业务判题状态
     * @param status 本次判定
     */
    @JsonIgnore
    void setStatus(JudgeStatus status);

    /**
     * 按消息协议输出判题状态
     * @return 显式编号，尚未设置状态时为空
     */
    @JsonGetter("status")
    default Integer statusCode() {
        JudgeStatus status = getStatus();
        return status == null ? null : status.getCode();
    }

    /**
     * 解析消息中的显式状态编号，缺失状态由消息入口校验
     * @param code 数字状态编号
     * @throws IllegalArgumentException 状态编号未定义
     */
    @JsonSetter("status")
    default void readStatusCode(Integer code) {
        setStatus(code == null ? null : JudgeStatus.fromCode(code));
    }
}

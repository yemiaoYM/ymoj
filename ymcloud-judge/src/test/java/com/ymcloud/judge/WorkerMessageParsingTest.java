package com.ymcloud.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 验证 Worker 消息解析拒绝隐式类型转换及多份 JSON。
 */
class WorkerMessageParsingTest {

    private final ObjectMapper mapper = new WorkerAmqpConfig().workerObjectMapper();

    /**
     * 正常任务保持往返一致，尾随 JSON 和重复身份字段不能穿过解析边界。
     * @throws Exception 序列化或解析失败
     */
    @Test
    void validTaskRoundTripsButTrailingAndDuplicateFieldsFail() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        String json = mapper.writeValueAsString(task);
        assertEquals(task, mapper.readValue(json, JudgeTask.class));
        assertThrows(IOException.class, () -> mapper.readValue(json + " {}", JudgeTask.class));
        assertThrows(IOException.class,
                () -> mapper.readValue("{\"submissionId\":1,\"submissionId\":2}", JudgeTask.class));
    }

    /**
     * 消息中的整数、布尔值及状态码不能从字符串、小数或 null 猜测恢复。
     */
    @Test
    void coercionCannotChangeTaskIdentityOrRecoveredVerdict() {
        for (String json : List.of("{\"submissionId\":1.9}", "{\"submissionId\":\"1\"}",
                "{\"strategy\":\"0\"}", "{\"schemaVersion\":null}")) {
            assertThrows(IOException.class, () -> mapper.readValue(json, JudgeTask.class), json);
        }
        for (String json : List.of("{\"status\":0.9}", "{\"status\":\"0\"}",
                "{\"terminal\":\"true\"}", "{\"terminal\":null}")) {
            assertThrows(IOException.class, () -> mapper.readValue(json, JudgeResult.class), json);
        }
    }
}

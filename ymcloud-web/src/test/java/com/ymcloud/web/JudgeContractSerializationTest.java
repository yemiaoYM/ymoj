package com.ymcloud.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 判题消息使用确定字段与数字状态，诊断字符串排除私有评测正文
 */
class JudgeContractSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 私有源码与结果仅进入业务消息，不进入诊断字符串
     * @throws Exception JSON 序列化或解析失败
     */
    @Test
    void privateContentRemainsInJsonButIsExcludedFromDiagnosticStrings() throws Exception {
        JudgeTask task = JudgeTask.builder()
                .executionId("test-execution")
                .code("private-code-sentinel")
                .input(" \tprivate-input-sentinel\n")
                .expectedOutput("private-expected-sentinel\n")
                .build();
        JsonNode taskJson = mapper.readTree(mapper.writeValueAsBytes(task));
        assertFalse(taskJson.has("schemaVersion"));
        assertEquals(task.getCode(), taskJson.get("code").asText());
        assertEquals(task.getInput(), taskJson.get("input").asText());
        assertEquals(task.getExpectedOutput(), taskJson.get("expectedOutput").asText());
        assertTrue(task.toString().contains("test-execution"));
        assertFalse(task.toString().contains("private-code-sentinel"));
        assertFalse(task.toString().contains("private-input-sentinel"));
        assertFalse(task.toString().contains("private-expected-sentinel"));

        TestCaseResult testCase = TestCaseResult.builder()
                .caseIndex(1)
                .status(JudgeVerdict.STATUS_RUNTIME_ERROR)
                .errorMessage("private-case-error-sentinel")
                .output("private-output-sentinel")
                .build();
        JudgeResult result = JudgeResult.builder()
                .executionId("test-execution")
                .status(JudgeVerdict.STATUS_RUNTIME_ERROR)
                .errorMessage("private-result-error-sentinel")
                .testCaseResults(List.of(testCase))
                .build();
        JsonNode resultJson = mapper.readTree(mapper.writeValueAsBytes(result));
        assertFalse(resultJson.has("schemaVersion"));
        assertEquals(3, resultJson.get("status").asInt());
        assertEquals(result.getErrorMessage(), resultJson.get("errorMessage").asText());
        assertEquals(testCase.getOutput(),
                resultJson.get("testCaseResults").get(0).get("output").asText());
        assertFalse(result.toString().contains("private-result-error-sentinel"));
        assertFalse(result.toString().contains("private-output-sentinel"));
        assertFalse(testCase.toString().contains("private-case-error-sentinel"));
        assertFalse(testCase.toString().contains("private-output-sentinel"));
    }

    /**
     * 使用业务状态码传输判定并拒绝未知状态
     * @throws Exception JSON 序列化或解析失败
     */
    @Test
    void verdictsUseExistingNumericProtocolAndRejectUnknownStatuses() throws Exception {
        assertEquals("0", mapper.writeValueAsString(JudgeVerdict.STATUS_ACCEPTED));
        assertEquals("-2", mapper.writeValueAsString(JudgeVerdict.STATUS_COMPILE_ERROR));
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, mapper.readValue("4", JudgeVerdict.class));
        assertThrows(JsonMappingException.class, () -> mapper.readValue("999", JudgeVerdict.class));
    }
}

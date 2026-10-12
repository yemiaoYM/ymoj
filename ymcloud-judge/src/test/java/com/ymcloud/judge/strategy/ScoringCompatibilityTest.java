package com.ymcloud.judge.strategy;

import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoringCompatibilityTest {

    @Test
    void acmUsesFirstFailingCaseAndOiKeepsCeilingScoreRule() {
        JudgeResult result = JudgeResult.builder().testCaseResults(List.of(testCase(JudgeVerdict.STATUS_ACCEPTED),
                testCase(JudgeVerdict.STATUS_RUNTIME_ERROR), testCase(JudgeVerdict.STATUS_WRONG_ANSWER))).build();
        JudgeRun.score(result, 0);
        assertEquals(JudgeVerdict.STATUS_RUNTIME_ERROR, result.getStatus());
        assertEquals(0, result.getScore());
        JudgeRun.score(result, 1);
        assertEquals(JudgeVerdict.STATUS_PARTIAL_ACCEPTED, result.getStatus());
        assertEquals(34, result.getScore());
        result.setTestCaseResults(List.of(testCase(JudgeVerdict.STATUS_WRONG_ANSWER)));
        JudgeRun.score(result, 1);
        assertEquals(JudgeVerdict.STATUS_WRONG_ANSWER, result.getStatus());
    }

    private TestCaseResult testCase(JudgeVerdict verdict) {
        TestCaseResult result = new TestCaseResult();
        result.setStatus(verdict);
        return result;
    }
}

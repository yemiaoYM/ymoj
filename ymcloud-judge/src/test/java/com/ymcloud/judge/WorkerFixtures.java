package com.ymcloud.judge;

import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 创建具有独立执行身份的正式及自测任务测试数据
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WorkerFixtures {

    /**
     * 创建具备有效数据身份、资源和期限的任务
     * @param strategy 正式或自测任务类别
     * @param mode ACM 或 OI 计分模式
     * @return 可供消息入口验证及执行测试的任务
     */
    static JudgeTask task(int strategy, int mode) {
        JudgeLanguage language = new JudgeLanguage();
        language.setName("Java");
        return JudgeTask.builder()
                .executionId(UUID.randomUUID().toString())
                .submissionId(100L)
                .problemId(strategy == 0 ? 10L : null)
                .testDataVersionId(strategy == 0 ? 77L : null)
                .manifestSha256(strategy == 0 ? "a".repeat(64) : null)
                .submittedAtEpochMs(System.currentTimeMillis())
                .deadlineEpochMs(System.currentTimeMillis() + 60000)
                .strategy(strategy)
                .mode(mode)
                .code("class Main {}")
                .input("input\n")
                .expectedOutput("answer\n")
                .languageConfig(language)
                .timeLimit(1000L)
                .memoryLimit(262144L)
                .build();
    }
}

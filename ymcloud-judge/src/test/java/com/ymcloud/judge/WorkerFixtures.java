package com.ymcloud.judge;

import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeTask;

import java.util.UUID;

final class WorkerFixtures {

    private WorkerFixtures() {
    }

    static JudgeTask task(int strategy, int mode) {
        JudgeLanguage language = new JudgeLanguage();
        language.setName("Java");
        language.setEnv("oj-java-env");
        language.setFileName("Main.java");
        language.setCompileCmd("javac -encoding UTF8 Main.java");
        language.setRunCmd("java Main");
        language.setType(0);
        return JudgeTask.builder().executionId(UUID.randomUUID().toString()).submissionId(100L)
                .problemId(strategy == 0 ? 10L : null).testDataVersionId(strategy == 0 ? 77L : null)
                .manifestSha256(strategy == 0 ? "a".repeat(64) : null)
                .submittedAtEpochMs(System.currentTimeMillis()).deadlineEpochMs(System.currentTimeMillis() + 60000)
                .strategy(strategy).mode(mode).code("class Main {}")
                .input("input\n").expectedOutput("answer\n").languageConfig(language)
                .timeLimit(1000L).memoryLimit(262144L).build();
    }
}

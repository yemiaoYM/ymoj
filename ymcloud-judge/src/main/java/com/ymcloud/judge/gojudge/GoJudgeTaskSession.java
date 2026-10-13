package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一次任务尝试拥有的远端文件；重试重新准备，不复用失效的文件 ID。
 */
@Slf4j
public class GoJudgeTaskSession implements AutoCloseable {

    private final GoJudgeClient client;
    private final JudgeTask task;
    private final Set<String> fileIds = new LinkedHashSet<>();

    public GoJudgeTaskSession(GoJudgeClient client, JudgeTask task) {
        this.client = client;
        this.task = task;
    }

    public JudgeTask task() {
        return task;
    }

    public String upload(InputStream input, String name) throws IOException {
        String fileId = client.upload(input, name, task.getDeadlineEpochMs());
        track(fileId);
        return fileId;
    }

    public void track(JsonNode response) throws IOException {
        JsonNode ids = response.get("fileIds");
        if (ids != null && !ids.isNull()) {
            if (!ids.isObject()) {
                throw new IOException("go-judge 返回的文件列表无效");
            }
            var values = ids.elements();
            while (values.hasNext()) {
                JsonNode value = values.next();
                if (!value.isTextual()) {
                    throw new IOException("go-judge 返回的文件 ID 无效");
                }
                track(value.textValue());
            }
        }
    }

    private void track(String fileId) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new IOException("go-judge 返回空文件 ID");
        }
        fileIds.add(fileId);
    }

    /**
     * 清理有独立的短截止边界，任务中断不能阻止释放已经创建的文件。
     */
    public void release(String fileId) {
        release(fileId, System.currentTimeMillis() + 2000L);
    }

    private void release(String fileId, long deadline) {
        boolean interrupted = Thread.interrupted();
        try {
            client.delete(fileId, deadline);
            fileIds.remove(fileId);
        } catch (GoJudgeFileMissingException absent) {
            fileIds.remove(fileId);
        } catch (IOException e) {
            log.warn("清理判题缓存文件失败 execution={} version={} cause={}",
                    task.getExecutionId(), task.getTestDataVersionId(), e.getClass().getSimpleName());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        long deadline = System.currentTimeMillis() + 2000L;
        for (String fileId : Set.copyOf(fileIds)) {
            if (System.currentTimeMillis() >= deadline) {
                log.warn("判题缓存清理达到截止边界 execution={} version={} remaining={}",
                        task.getExecutionId(), task.getTestDataVersionId(), fileIds.size());
                break;
            }
            release(fileId, deadline);
        }
    }
}

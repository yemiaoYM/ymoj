package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.pojo.bo.judge.JudgeTask;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 一次任务尝试拥有的远端文件；重试重新准备，不复用失效的文件 ID。
 */
@Slf4j
public class GoJudgeTaskSession implements AutoCloseable {

    /**
     * 任务结束后回收沙箱文件的独立预算，停机等待也须覆盖此阶段
     */
    public static final long CLEANUP_TIMEOUT_MILLIS = 2000L;

    private final GoJudgeClient client;
    private final JudgeTask task;
    private final Semaphore taskSlots;
    private final Set<String> fileIds = new LinkedHashSet<>();
    private boolean closed;

    /**
     * 为一次任务持有已取得的共享额度及独立沙箱文件集合
     * @param client 受控沙箱客户端
     * @param task 固定任务快照
     * @param taskSlots 本会话关闭时归还的 Worker 总任务额度
     */
    GoJudgeTaskSession(GoJudgeClient client, JudgeTask task, Semaphore taskSlots) {
        this.client = client;
        this.task = task;
        this.taskSlots = taskSlots;
    }

    /**
     * 读取本会话的固定任务快照。
     * @return 本次执行任务
     */
    public JudgeTask task() {
        return task;
    }

    /**
     * 流式上传并登记文件，输入由上传过程关闭。
     * @param input 测试输入或源码流
     * @param name 受控上传文件名
     * @return 需要由本会话回收的文件 ID
     * @throws IOException 上传或文件 ID 校验失败
     */
    public String upload(InputStream input, String name) throws IOException {
        String fileId = client.upload(input, name, task.getDeadlineEpochMs());
        track(fileId);
        return fileId;
    }

    /**
     * 在任务截止内读取已登记的缓存输出
     * @param fileId 本次沙箱执行返回的文件 ID
     * @return 由调用方关闭的有界原始文件流
     * @throws IOException 下载失败、超限、超时或任务中断
     */
    public InputStream openFile(String fileId) throws IOException {
        return client.openFile(fileId, task.getDeadlineEpochMs());
    }

    /**
     * 登记响应中的沙箱文件，保证异常路径仍可回收。
     * @param response 已验证的命令响应
     * @throws IOException 文件映射或 ID 非法
     */
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

    /**
     * 登记单个非空沙箱文件 ID。
     * @param fileId 本次执行产生的文件 ID
     * @throws IOException ID 为空
     */
    private void track(String fileId) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new IOException("go-judge 返回空文件 ID");
        }
        fileIds.add(fileId);
    }

    /**
     * 清理有独立的短截止边界，任务中断不能阻止释放已经创建的文件。
     * @param fileId 待回收文件 ID
     */
    public void release(String fileId) {
        release(fileId, System.currentTimeMillis() + CLEANUP_TIMEOUT_MILLIS);
    }

    /**
     * 回收文件并恢复原中断状态，失败留在集合供后续清理。
     * @param fileId 待回收文件 ID
     * @param deadline 清理截止时间，UTC epoch 毫秒
     */
    private void release(String fileId, long deadline) {
        boolean interrupted = Thread.interrupted();
        try {
            client.delete(fileId, deadline);
            fileIds.remove(fileId);
        } catch (GoJudgeFileMissingException absent) {
            fileIds.remove(fileId);
        } catch (IOException e) {
            log.warn("清理判题缓存文件失败 execution={} cause={}",
                    task.getExecutionId(), e.getClass().getSimpleName());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 在两秒清理预算内回收文件，所有退出路径均只归还一次任务额度
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            long deadline = System.currentTimeMillis() + CLEANUP_TIMEOUT_MILLIS;
            for (String fileId : Set.copyOf(fileIds)) {
                if (System.currentTimeMillis() >= deadline) {
                    log.warn("判题缓存清理达到截止边界 execution={} remaining={}",
                            task.getExecutionId(), fileIds.size());
                    break;
                }
                release(fileId, deadline);
            }
        } finally {
            taskSlots.release();
        }
    }
}

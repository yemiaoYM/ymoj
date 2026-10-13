package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 固定四个语言入口，将结构化 go-judge 结果转换为现有业务判定。
 */
@Slf4j
@Component
public class GoJudgeExecutor {

    private static final String OUTPUT_LIMITER = "/usr/local/bin/ymcloud-output-limit";
    private static final List<String> ENVIRONMENT = List.of(
            "PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "HOME=/w");

    private final GoJudgeClient client;
    private final GoJudgeProperties properties;
    private final WorkerProperties worker;

    public GoJudgeExecutor(GoJudgeClient client, GoJudgeProperties properties, WorkerProperties worker) {
        this.client = client;
        this.properties = properties;
        this.worker = worker;
    }

    public GoJudgeTaskSession openTask(JudgeTask task) {
        return new GoJudgeTaskSession(client, task);
    }

    public String capacityFailure(JudgeTask task) {
        if (task.getMemoryLimit() > properties.getMaxProblemMemoryKiB()) {
            return "题目内存限制超过当前评测部署容量（上限 " + properties.getMaxProblemMemoryKiB() + " KiB）";
        }
        if (task.getTimeLimit() + 2000L > properties.getRequestTimeout().toMillis()) {
            return "题目限时超过当前评测部署的 HTTP 执行预算";
        }
        return null;
    }

    public PreparedProgram compile(GoJudgeTaskSession session) throws IOException {
        JudgeTask task = session.task();
        String language = task.getLanguageConfig().getName();
        String source = switch (language) {
            case "Java" -> "Main.java";
            case "C" -> "main.c";
            case "C++" -> "main.cpp";
            case "Python" -> "main.py";
            default -> throw new IOException("不支持的语言入口");
        };
        String artifact = switch (language) {
            case "Java" -> "program.jar";
            case "Python" -> "main.py";
            default -> "program";
        };
        List<String> arguments = switch (language) {
            case "Java" -> List.of("/bin/sh", "-c",
                    "mkdir classes && /usr/bin/javac -J-Xmx256m -J-XX:+UseSerialGC -encoding UTF-8"
                            + " -J-Djava.io.tmpdir=/w -d classes Main.java"
                            + " && /usr/bin/jar -J-Xmx128m -J-Djava.io.tmpdir=/w cf program.jar -C classes .");
            case "C" -> List.of("/usr/bin/gcc", "-std=c11", "-O2", "-pipe", "main.c", "-o", "program", "-lm");
            case "C++" -> List.of("/usr/bin/g++", "-std=c++17", "-O2", "-pipe", "main.cpp", "-o", "program");
            case "Python" -> List.of("/usr/bin/python3", "-I", "-m", "py_compile", "main.py");
            default -> throw new IOException("不支持的语言入口");
        };
        String sourceId;
        try (InputStream code = new ByteArrayInputStream(task.getCode().getBytes(StandardCharsets.UTF_8))) {
            sourceId = session.upload(code, source);
        }
        try {
            Map<String, Object> command = command(arguments, properties.getCompileWallTimeMillis(),
                    properties.getCompileMemoryBytes());
            command.put("copyIn", Map.of(source, Map.of("fileId", sourceId)));
            // 失败编译没有产物时，不让缺少可选文件掩盖实际的编译状态。
            command.put("copyOutCached", List.of(artifact + "?"));
            JsonNode response = execute(session, command, "compile");
            String status = status(response);
            if ("File Error".equals(status)) {
                if (fileSizeExceeded(response)) {
                    throw new CompileException("编译产物超过大小限制");
                }
                throw new IOException("编译文件处理失败");
            }
            if (!"Accepted".equals(status) || exitStatus(response) != 0) {
                throw new CompileException("编译失败（" + status + "）：" + diagnostic(response));
            }
            if (nonNegative(response, "runTime") > TimeUnit.MILLISECONDS.toNanos(properties.getCompileWallTimeMillis())) {
                throw new CompileException("编译超过墙钟时间限制");
            }
            if (nonNegative(response, "memory") > properties.getCompileMemoryBytes()) {
                throw new CompileException("编译超过内存限制");
            }
            JsonNode fileId = response.path("fileIds").get(artifact);
            if (fileId == null || !fileId.isTextual() || fileId.textValue().isBlank()) {
                throw new IOException("编译成功但未返回完整产物");
            }
            return new PreparedProgram(language, artifact, fileId.textValue());
        } finally {
            session.release(sourceId);
        }
    }

    public ExecutionResult run(GoJudgeTaskSession session, PreparedProgram program, InputStream input)
            throws IOException {
        JudgeTask task = session.task();
        String inputId = session.upload(input, "stdin");
        try {
            List<String> arguments = switch (program.language()) {
                case "Java" -> List.of("/usr/bin/java", "-Xms16m", "-Xmx" + task.getMemoryLimit() + "k",
                        "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1", "-XX:+ExitOnOutOfMemoryError",
                        "-cp", "program.jar", "Main");
                case "Python" -> List.of("/usr/bin/python3", "-I", "main.py");
                default -> List.of("./program");
            };
            Map<String, Object> command = command(arguments, task.getTimeLimit(),
                    Math.multiplyExact(task.getMemoryLimit(), 1024L));
            command.put("files", files(Map.of("fileId", inputId)));
            command.put("copyIn", Map.of(program.artifact(), Map.of("fileId", program.fileId())));
            JsonNode response = execute(session, command, "run");
            return classify(response, task);
        } finally {
            session.release(inputId);
        }
    }

    private Map<String, Object> command(List<String> arguments, long wallMillis, long memoryBytes) {
        List<String> limited = new ArrayList<>(List.of(OUTPUT_LIMITER,
                Integer.toString(worker.getMaxOutputBytes()), Long.toString(properties.getMaxFileBytes())));
        limited.addAll(arguments);
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("args", limited);
        command.put("env", ENVIRONMENT);
        command.put("files", files(Map.of("content", "")));
        // CPU 仅作保护：覆盖可分配核心及默认 100ms CFS 周期内的并行突发。
        // 业务墙钟边界始终使用原始限时，不因系统负载或 CPU 配额而自动加时。
        long cpuMultiplier = Math.max(2L, (properties.getCpuRateLimit() + 999L) / 1000L);
        command.put("cpuLimit", TimeUnit.MILLISECONDS.toNanos((wallMillis + 100L) * cpuMultiplier));
        command.put("clockLimit", TimeUnit.MILLISECONDS.toNanos(wallMillis));
        command.put("memoryLimit", memoryBytes);
        command.put("procLimit", properties.getProcessLimit());
        command.put("cpuRateLimit", properties.getCpuRateLimit());
        command.put("copyOutMax", properties.getMaxFileBytes());
        command.put("copyOutTruncate", false);
        return command;
    }

    private List<Map<String, Object>> files(Map<String, Object> input) {
        return List.of(input,
                Map.of("name", "stdout", "max", worker.getMaxOutputBytes(), "pipe", true),
                Map.of("name", "stderr", "max", worker.getMaxOutputBytes(), "pipe", true),
                Map.of("name", "launcher", "max", 256, "pipe", true));
    }

    private JsonNode execute(GoJudgeTaskSession session, Map<String, Object> command, String stage)
            throws IOException {
        List<JsonNode> responses = client.execute(Map.of("cmd", List.of(command), "pipeMapping", List.of()),
                session.task().getDeadlineEpochMs());
        for (JsonNode response : responses) {
            session.track(response);
        }
        if (responses.size() != 1) {
            throw new IOException("go-judge 返回的命令数量不一致");
        }
        JsonNode response = responses.get(0);
        validateLauncher(response);
        textFile(response, "stdout");
        textFile(response, "stderr");
        long cpu = nonNegative(response, "time");
        long wall = nonNegative(response, "runTime");
        long memory = nonNegative(response, "memory");
        log.info("沙箱阶段完成 execution={} version={} stage={} status={} cpuMs={} wallMs={} memoryKiB={}",
                session.task().getExecutionId(), session.task().getTestDataVersionId(), stage,
                status(response), millis(cpu), millis(wall), kibibytes(memory));
        return response;
    }

    ExecutionResult classify(JsonNode response, JudgeTask task) throws IOException {
        validateLauncher(response);
        String status = status(response);
        long wall = nonNegative(response, "runTime");
        long memory = nonNegative(response, "memory");
        JudgeVerdict verdict = switch (status) {
            case "Accepted" -> exitStatus(response) == 0 ? JudgeVerdict.STATUS_ACCEPTED : JudgeVerdict.STATUS_RUNTIME_ERROR;
            case "Time Limit Exceeded" -> JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED;
            case "Output Limit Exceeded", "Nonzero Exit Status", "Signalled" -> JudgeVerdict.STATUS_RUNTIME_ERROR;
            case "File Error" -> {
                if (fileSizeExceeded(response)) {
                    yield JudgeVerdict.STATUS_RUNTIME_ERROR;
                }
                throw new IOException("运行文件处理失败");
            }
            default -> throw new IOException("go-judge 返回未知或内部错误状态");
        };
        if (verdict == JudgeVerdict.STATUS_ACCEPTED) {
            if (wall > TimeUnit.MILLISECONDS.toNanos(task.getTimeLimit())) {
                verdict = JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED;
            } else if (memory > Math.multiplyExact(task.getMemoryLimit(), 1024L)) {
                verdict = JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED;
            }
        }
        String stdout = textFile(response, "stdout");
        textFile(response, "stderr");
        // 共享输出预算由可信启动器按原始字节计数；JSON 替换非法 UTF-8 后不能重新计数。
        String error = verdict == JudgeVerdict.STATUS_RUNTIME_ERROR ? "程序运行异常（" + status + "）" : null;
        return new ExecutionResult(verdict, stdout, error, millis(wall), kibibytes(memory));
    }

    private static String status(JsonNode response) throws IOException {
        String status = response.path("status").asText("");
        if (!List.of("Accepted", "Time Limit Exceeded", "Memory Limit Exceeded", "Output Limit Exceeded",
                "Nonzero Exit Status", "Signalled", "File Error").contains(status)) {
            throw new IOException("go-judge 返回未知或内部错误状态");
        }
        return status;
    }

    private static boolean fileSizeExceeded(JsonNode response) {
        JsonNode errors = response.path("fileError");
        if (!errors.isArray() || errors.isEmpty()) {
            return false;
        }
        for (JsonNode error : errors) {
            String type = error.path("type").asText();
            if (!"CopyOutSizeExceeded".equals(type) && !"CollectSizeExceeded".equals(type)) {
                return false;
            }
        }
        return true;
    }

    private static long exitStatus(JsonNode response) throws IOException {
        return nonNegative(response, "exitStatus");
    }

    private static long nonNegative(JsonNode response, String field) throws IOException {
        JsonNode value = response.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IOException("go-judge 返回无效的结构化资源统计");
        }
        return value.longValue();
    }

    private static String textFile(JsonNode response, String name) throws IOException {
        JsonNode value = response.path("files").get(name);
        if (value == null || !value.isTextual()) {
            throw new IOException("go-judge 返回无效的输出文件");
        }
        return value.textValue();
    }

    private static void validateLauncher(JsonNode response) throws IOException {
        String diagnostic = textFile(response, "launcher");
        if (!diagnostic.isEmpty()) {
            // 诊断位于不传给用户程序的 FD 3，内容不得拼进日志或用户错误信息。
            throw new IOException("沙箱启动器报告基础设施故障");
        }
    }

    private static String diagnostic(JsonNode response) throws IOException {
        String message = textFile(response, "stderr");
        return message.isBlank() ? "编译器返回失败状态" : message.substring(0, Math.min(message.length(), 4000));
    }

    private static long millis(long nanoseconds) {
        return nanoseconds / 1_000_000L + (nanoseconds % 1_000_000L == 0 ? 0 : 1);
    }

    private static long kibibytes(long bytes) {
        return bytes / 1024L + (bytes % 1024L == 0 ? 0 : 1);
    }

    public record PreparedProgram(String language, String artifact, String fileId) {
    }

    public record ExecutionResult(JudgeVerdict status, String stdout, String errorMessage,
                                  long timeMillis, long memoryKiB) {
    }
}

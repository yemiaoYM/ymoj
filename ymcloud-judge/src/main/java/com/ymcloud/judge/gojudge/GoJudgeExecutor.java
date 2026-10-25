package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 固定四种语言入口，将结构化沙箱结果转换为业务判定。
 */
@Slf4j
@Component
public class GoJudgeExecutor {

    private static final String OUTPUT_LIMITER = "/usr/local/bin/ymcloud-output-limit";
    private static final List<String> ENVIRONMENT = List.of(
            "PATH=/usr/local/bin:/usr/bin:/bin",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "HOME=/w");

    @Autowired
    private GoJudgeClient client;

    @Autowired
    private GoJudgeProperties properties;

    @Autowired
    private WorkerProperties worker;

    /**
     * 为一次任务创建独立的沙箱文件生命周期。
     * @param task 已通过入口校验的任务
     * @return 由调用方关闭的任务会话
     */
    public GoJudgeTaskSession openTask(JudgeTask task) {
        return new GoJudgeTaskSession(client, task);
    }

    /**
     * 检查题目资源是否超过当前部署能力，不将容量不足当作用户代码错误。
     * @param task 已验证的任务资源限制
     * @return 安全的拒绝原因；容量允许时为 null
     */
    public String capacityFailure(JudgeTask task) {
        if (task.getMemoryLimit() > properties.getMaxProblemMemoryKiB()) {
            return "题目内存限制超过当前评测部署容量（上限 " + properties.getMaxProblemMemoryKiB() + " KiB）";
        }
        if (task.getTimeLimit() + 2000L > properties.getRequestTimeout().toMillis()) {
            return "题目限时超过当前评测部署的 HTTP 执行预算";
        }
        return null;
    }

    /**
     * 使用固定语言入口编译，并释放上传的源码文件。
     * @param session 当前任务会话
     * @return 语言及沙箱编译产物 ID
     * @throws CompileException 用户代码编译失败或超过编译资源限制
     * @throws IOException 传输失败、沙箱内部故障或产物无效
     */
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

    /**
     * 上传当前测试点输入并执行固定程序，结束后释放输入文件。
     * @param session 当前任务会话
     * @param program 已经编译的程序
     * @param input 当前输入流，由上传过程关闭
     * @return 结构化判定、耗时毫秒和内存 KiB
     * @throws IOException 上传、执行或响应校验失败
     */
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

    /**
     * 构造可信启动器命令，统一墙钟、内存、输出和进程上限。
     * @param arguments 固定语言命令及参数
     * @param wallMillis 墙钟时间上限，毫秒
     * @param memoryBytes 内存上限，字节
     * @return 可发送给 go-judge 的命令字段
     */
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

    /**
     * 声明标准输入及受限的 stdout、stderr 和启动器诊断通道。
     * @param input 标准输入内容或沙箱文件引用
     * @return 按文件描述符顺序排列的通道配置
     */
    private List<Map<String, Object>> files(Map<String, Object> input) {
        return List.of(input,
                Map.of("name", "stdout", "max", worker.getMaxOutputBytes(), "pipe", true),
                Map.of("name", "stderr", "max", worker.getMaxOutputBytes(), "pipe", true),
                Map.of("name", "launcher", "max", 256, "pipe", true));
    }

    /**
     * 执行固定沙箱命令并登记产物，逐阶段统计仅记 DEBUG，避免每个测试点放大 INFO 日志。
     * @param session 当前任务的文件生命周期
     * @param command 已由本执行器构造的受限命令
     * @param stage 编译或运行阶段
     * @return 已验证启动器诊断和资源统计的唯一执行响应
     * @throws IOException 传输、启动器或响应校验失败
     */
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
        log.debug("沙箱阶段完成 execution={} version={} stage={} status={} cpuMs={} wallMs={} memoryKiB={}",
                session.task().getExecutionId(), session.task().getTestDataVersionId(), stage,
                status(response), millis(cpu), millis(wall), kibibytes(memory));
        return response;
    }

    /**
     * 将沙箱状态转换为判题结果，并复核墙钟和内存边界。
     * @param response 已验证结构的单个命令响应
     * @param task 本次任务及资源上限
     * @return 业务判定及受限输出
     * @throws IOException 资源字段、启动器诊断或输出结构无效
     */
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

    /**
     * 只接受项目支持的沙箱状态，未知状态视为基础设施失败。
     * @param response 单个命令响应
     * @return 受支持的沙箱状态文本
     * @throws IOException 状态未知或属于内部故障
     */
    private static String status(JsonNode response) throws IOException {
        String status = response.path("status").asText("");
        if (!List.of("Accepted", "Time Limit Exceeded", "Memory Limit Exceeded", "Output Limit Exceeded",
                "Nonzero Exit Status", "Signalled", "File Error").contains(status)) {
            throw new IOException("go-judge 返回未知或内部错误状态");
        }
        return status;
    }

    /**
     * 确认所有文件错误都是容量超限，避免掩盖其他文件故障。
     * @param response 单个命令响应
     * @return 是否只有受支持的文件超限错误
     */
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

    /**
     * 读取非负整数退出状态。
     * @param response 单个命令响应
     * @return 退出状态码
     * @throws IOException 字段缺失或类型、范围非法
     */
    private static long exitStatus(JsonNode response) throws IOException {
        return nonNegative(response, "exitStatus");
    }

    /**
     * 读取非负整数资源统计，禁止隐式转换。
     * @param response 单个命令响应
     * @param field 要读取的统计字段
     * @return 非负统计值，单位由字段决定
     * @throws IOException 字段缺失或非合法整数
     */
    private static long nonNegative(JsonNode response, String field) throws IOException {
        JsonNode value = response.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IOException("go-judge 返回无效的结构化资源统计");
        }
        return value.longValue();
    }

    /**
     * 读取沙箱文本通道，不把缺失输出当作空字符串。
     * @param response 单个命令响应
     * @param name 固定输出通道名
     * @return 通道文本，可为空串
     * @throws IOException 输出字段缺失或不是字符串
     */
    private static String textFile(JsonNode response, String name) throws IOException {
        JsonNode value = response.path("files").get(name);
        if (value == null || !value.isTextual()) {
            throw new IOException("go-judge 返回无效的输出文件");
        }
        return value.textValue();
    }

    /**
     * 启动器诊断通道非空时终止判题，不向用户传播诊断正文。
     * @param response 单个命令响应
     * @throws IOException 诊断缺失或报告基础设施故障
     */
    private static void validateLauncher(JsonNode response) throws IOException {
        String diagnostic = textFile(response, "launcher");
        if (!diagnostic.isEmpty()) {
            // 诊断位于不传给用户程序的 FD 3，内容不得拼进日志或用户错误信息。
            throw new IOException("沙箱启动器报告基础设施故障");
        }
    }

    /**
     * 提取有长度上限的编译诊断，用于既有编译错误契约。
     * @param response 编译命令响应
     * @return 最多 4000 个字符的诊断或安全默认提示
     * @throws IOException stderr 字段无效
     */
    private static String diagnostic(JsonNode response) throws IOException {
        String message = textFile(response, "stderr");
        return message.isBlank() ? "编译器返回失败状态" : message.substring(0, Math.min(message.length(), 4000));
    }

    /**
     * 将纳秒向上取整为毫秒，避免低估实际耗时。
     * @param nanoseconds 非负纳秒数
     * @return 向上取整的毫秒数
     */
    private static long millis(long nanoseconds) {
        return nanoseconds / 1_000_000L + (nanoseconds % 1_000_000L == 0 ? 0 : 1);
    }

    /**
     * 将字节向上取整为 KiB，避免低估实际内存。
     * @param bytes 非负字节数
     * @return 向上取整的 KiB 数
     */
    private static long kibibytes(long bytes) {
        return bytes / 1024L + (bytes % 1024L == 0 ? 0 : 1);
    }

    public record PreparedProgram(String language, String artifact, String fileId) {}

    public record ExecutionResult(JudgeVerdict status, String stdout, String errorMessage,
                                  long timeMillis, long memoryKiB) {}
}

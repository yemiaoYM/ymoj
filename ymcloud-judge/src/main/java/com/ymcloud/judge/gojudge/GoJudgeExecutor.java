package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
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

    private Semaphore taskSlots;

    /**
     * 由 Worker 总任务上限建立唯一准入额度，正式判题和编译预检共用
     */
    @PostConstruct
    public void initialize() {
        taskSlots = new Semaphore(worker.getConcurrency(), true);
    }

    /**
     * 在任务截止前取得共享额度，会话关闭时归还额度并清理沙箱文件
     * @param task 已通过入口校验的任务
     * @return 由调用方关闭的任务会话
     * @throws IOException 等待额度超时或被中断
     */
    public GoJudgeTaskSession openTask(JudgeTask task) throws IOException {
        long remaining = task.getDeadlineEpochMs() - System.currentTimeMillis();
        try {
            if (remaining <= 0 || !taskSlots.tryAcquire(remaining, TimeUnit.MILLISECONDS)) {
                throw new IOException("等待 Worker 任务额度超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("等待 Worker 任务额度被中断");
        }
        return new GoJudgeTaskSession(client, task, taskSlots);
    }

    /**
     * 检查题目内存是否超过当前部署能力，不将容量不足当作用户代码错误
     * @param task 已验证的任务资源限制
     * @return 安全的拒绝原因；容量允许时为 null
     */
    public String capacityFailure(JudgeTask task) {
        if (task.getMemoryLimit() > properties.getMaxProblemMemoryKiB()) {
            return "题目内存限制超过当前评测部署容量（上限 " + properties.getMaxProblemMemoryKiB() + " KiB）";
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
        return compileSource(session, task.getLanguageConfig().getName(), task.getCode(), false);
    }

    /**
     * 编译任务快照中的校验器，独立命名产物避免与选手程序混用
     * @param session 当前任务会话
     * @param spj 已验证的校验器快照
     * @return 本会话持有的校验器编译产物
     * @throws IOException 编译或沙箱处理失败
     */
    public PreparedProgram compileSpj(GoJudgeTaskSession session, SpjProgram spj) throws IOException {
        return compileSource(session, spj.getLanguage(), spj.getCode(), true);
    }

    /**
     * 复用固定语言编译入口，源码只作为文件内容上传
     * @param session 当前任务会话
     * @param language 支持的语言
     * @param sourceCode 有界源码
     * @param checker 是否生成校验器产物
     * @return 已登记到会话的编译产物
     * @throws IOException 编译或文件处理失败
     */
    private PreparedProgram compileSource(GoJudgeTaskSession session, String language, String sourceCode,
                                          boolean checker) throws IOException {
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
            default -> checker ? "checker" : "program";
        };
        List<String> arguments = switch (language) {
            case "Java" -> List.of("/bin/sh", "-c",
                    "mkdir classes && /usr/bin/javac -J-Xmx256m -J-XX:+UseSerialGC -encoding UTF-8"
                            + " -J-Djava.io.tmpdir=/w -d classes Main.java"
                            + " && /usr/bin/jar -J-Xmx128m -J-Djava.io.tmpdir=/w cf program.jar -C classes .");
            case "C" -> List.of("/usr/bin/gcc", "-std=c11", "-O2", "-pipe", "main.c", "-o", artifact, "-lm");
            case "C++" -> List.of("/usr/bin/g++", "-std=c++17", "-O2", "-pipe", "main.cpp", "-o", artifact);
            case "Python" -> List.of("/usr/bin/python3", "-I", "-m", "py_compile", "main.py");
            default -> throw new IOException("不支持的语言入口");
        };
        String sourceId;
        try (InputStream code = new ByteArrayInputStream(sourceCode.getBytes(StandardCharsets.UTF_8))) {
            sourceId = session.upload(code, source);
        }
        String headerId = null;
        try {
            Map<String, Object> command = command(arguments, properties.getCompileWallTimeMillis(),
                    properties.getCompileMemoryBytes());
            Map<String, Object> copyIn = new LinkedHashMap<>();
            copyIn.put(source, Map.of("fileId", sourceId));
            if (checker) {
                try (InputStream header = new ClassPathResource("testlib/testlib.h").getInputStream()) {
                    headerId = session.upload(header, "testlib.h");
                }
                copyIn.put("testlib.h", Map.of("fileId", headerId));
            }
            command.put("copyIn", copyIn);
            // 失败编译没有产物时，不让缺少可选文件掩盖实际的编译状态。
            command.put("copyOutCached", List.of("stdout", "stderr", artifact + "?"));
            JsonNode response = execute(session, command, "compile");
            try {
                String status = status(response);
                if ("File Error".equals(status)) {
                    if (fileSizeExceeded(response)) {
                        throw new CompileException("编译产物超过大小限制");
                    }
                    throw new IOException("编译文件处理失败");
                }
                if (!"Accepted".equals(status) || exitStatus(response) != 0) {
                    throw new CompileException("编译失败（" + status + "）：" + diagnostic(session, response));
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
                releaseOutput(session, response, "stdout");
                releaseOutput(session, response, "stderr");
            }
        } finally {
            session.release(sourceId);
            if (headerId != null) {
                session.release(headerId);
            }
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
            try {
                ExecutionResult result = classify(response, task);
                String preview = result.stdoutFileId() == null || task.getSpj() != null
                        ? "" : readOutput(session, result.stdoutFileId(), 301);
                return new ExecutionResult(result.status(), preview, result.errorMessage(), result.timeMillis(),
                        result.memoryKiB(), result.stdoutFileId());
            } finally {
                releaseOutput(session, response, "stderr");
            }
        } finally {
            session.release(inputId);
        }
    }

    /**
     * 在独立沙箱中校验原始输出，诊断正文不进入选手结果
     * @param session 当前任务会话
     * @param checker 已编译校验器
     * @param input 测试输入流
     * @param answer 标准答案流
     * @param outputId 当前测试点的原始输出文件 ID
     * @return 校验器协议判定，运行故障为系统错误
     * @throws IOException 文件传输或沙箱协议失败
     */
    public JudgeVerdict check(GoJudgeTaskSession session, PreparedProgram checker, InputStream input,
                              InputStream answer, String outputId) throws IOException {
        String inputId = null;
        String answerId = null;
        try {
            inputId = session.upload(input, "input.txt");
            answerId = session.upload(answer, "answer.txt");
            Map<String, Object> command = command(
                    List.of("./checker", "input.txt", "output.txt", "answer.txt"),
                    SpjProgram.TIME_LIMIT_MILLIS, SpjProgram.MEMORY_LIMIT_BYTES);
            command.put("copyIn", Map.of(
                    "checker", Map.of("fileId", checker.fileId()),
                    "input.txt", Map.of("fileId", inputId),
                    "output.txt", Map.of("fileId", outputId),
                    "answer.txt", Map.of("fileId", answerId)));
            JsonNode response = execute(session, command, "spj");
            try {
                return checkerVerdict(response);
            } finally {
                releaseOutput(session, response, "stdout");
                releaseOutput(session, response, "stderr");
            }
        } finally {
            if (inputId != null) {
                session.release(inputId);
            }
            if (answerId != null) {
                session.release(answerId);
            }
        }
    }

    /**
     * 解释校验器的退出码，资源超限和异常不能误判为选手错误
     * @param response 已验证的沙箱响应
     * @return AC、WA、PE 或 SE
     * @throws IOException 沙箱响应结构无效
     */
    JudgeVerdict checkerVerdict(JsonNode response) throws IOException {
        String state = status(response);
        if ((!"Accepted".equals(state) && !"Nonzero Exit Status".equals(state))
                || nonNegative(response, "runTime") > TimeUnit.MILLISECONDS.toNanos(SpjProgram.TIME_LIMIT_MILLIS)
                || nonNegative(response, "memory") > SpjProgram.MEMORY_LIMIT_BYTES) {
            return JudgeVerdict.STATUS_SYSTEM_ERROR;
        }
        long exit = exitStatus(response);
        if (exit == SpjProgram.ACCEPTED) {
            return JudgeVerdict.STATUS_ACCEPTED;
        }
        if (exit == SpjProgram.WRONG_ANSWER) {
            return JudgeVerdict.STATUS_WRONG_ANSWER;
        }
        if (exit == SpjProgram.PRESENTATION_ERROR) {
            return JudgeVerdict.STATUS_PRESENTATION_ERROR;
        }
        return JudgeVerdict.STATUS_SYSTEM_ERROR;
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
        command.put("copyOutCached", List.of("stdout", "stderr"));
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
        cachedOutput(response, "stdout");
        cachedOutput(response, "stderr");
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
        String stdoutFileId = cachedOutput(response, "stdout");
        // stdout 和 stderr 的共享预算由可信启动器按原始字节计数
        String error = verdict == JudgeVerdict.STATUS_RUNTIME_ERROR ? "程序运行异常（" + status + "）" : null;
        return new ExecutionResult(verdict, "", error, millis(wall), kibibytes(memory), stdoutFileId);
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
     * @param session 当前输出文件会话
     * @param response 编译命令响应
     * @return 最多 4000 个字符的诊断或安全默认提示
     * @throws IOException stderr 字段无效
     */
    private static String diagnostic(GoJudgeTaskSession session, JsonNode response) throws IOException {
        String fileId = cachedOutput(response, "stderr");
        String message = fileId == null ? "" : readOutput(session, fileId, 4000);
        return message.isBlank() ? "编译器返回失败状态" : message;
    }

    /**
     * 取得原始输出引用，成功命令必须返回完整输出文件
     * @param response 沙箱结构化响应
     * @param name 固定输出通道
     * @return 缓存文件 ID，失败且未产生文件时为 null
     * @throws IOException 成功响应缺少输出引用
     */
    private static String cachedOutput(JsonNode response, String name) throws IOException {
        JsonNode fileId = response.path("fileIds").get(name);
        if (fileId != null && fileId.isTextual() && !fileId.textValue().isBlank()) {
            return fileId.textValue();
        }
        if ("Accepted".equals(status(response))) {
            throw new IOException("go-judge 成功响应缺少缓存输出文件");
        }
        return null;
    }

    /**
     * 仅读取预览需要的字符，完整输出仍留在沙箱缓存供比对
     * @param session 本次任务会话
     * @param fileId 原始输出文件 ID
     * @param maximum 最多读取的 UTF-16 字符数
     * @return 有界预览文本
     * @throws IOException 缓存文件读取失败或请求被中断
     */
    private static String readOutput(GoJudgeTaskSession session, String fileId, int maximum) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(session.openFile(fileId), StandardCharsets.UTF_8)) {
            char[] preview = new char[maximum];
            int count = 0;
            int read;
            while (count < maximum && (read = reader.read(preview, count, maximum - count)) != -1) {
                count += read;
            }
            return new String(preview, 0, count);
        }
    }

    /**
     * 及时释放阶段输出，失败清理由会话及沙箱 TTL 继续负责
     * @param session 当前任务会话
     * @param response 已验证并登记的响应
     * @param name 固定输出通道
     */
    private static void releaseOutput(GoJudgeTaskSession session, JsonNode response, String name) {
        JsonNode fileId = response.path("fileIds").get(name);
        if (fileId != null) {
            session.release(fileId.textValue());
        }
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
                                  long timeMillis, long memoryKiB, String stdoutFileId) {}
}

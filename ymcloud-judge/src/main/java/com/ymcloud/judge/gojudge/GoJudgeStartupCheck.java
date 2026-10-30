package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.judge.config.WorkerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: Rabbit 消费启动前验证固定版本、服务身份、cgroup v2、seccomp 与实际沙箱工具链。
 * 上游 API 仅报告 seccomp 是否启用；内置默认配置的来源同时由固定镜像和入口脚本约束。
 */
@Slf4j
@Component("goJudgeStartupCheck")
public class GoJudgeStartupCheck {

    public static final String REQUIRED_VERSION = "v1.13.3";

    @Autowired
    private GoJudgeClient client;

    @Autowired
    private GoJudgeProperties properties;

    @Autowired
    private WorkerProperties workerProperties;

    /**
     * 消费者启动前验证版本、鉴权、隔离和工具链，失败则阻止启动。
     * @throws GoJudgeException 沙箱不符合部署安全约束
     */
    @PostConstruct
    public void verify() throws GoJudgeException {
        long started = System.nanoTime();
        if (workerProperties.getConcurrency() != properties.getSandboxConcurrency()
                || properties.getMaxResponseBytes() < 6L * workerProperties.getMaxOutputBytes() + 65536) {
            throw new GoJudgeException("Worker 并发、沙箱并发及 HTTP 输出预算不匹配");
        }
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        validateVersion(client.version(deadline));
        client.verifyAuthentication(deadline);
        validateConfig(client.config(deadline));
        verifyToolchain(List.of("/usr/bin/java", "-Xmx64m", "-XX:+UseSerialGC", "-version"),
                "(?s).*openjdk version \"17\\.[^\"]+\".*", deadline);
        verifyToolchain(List.of("/usr/bin/javac", "-J-Xmx64m", "-J-XX:+UseSerialGC", "-version"),
                "(?s).*javac 17\\.[^\\s]+.*", deadline);
        verifyToolchain(List.of("/usr/bin/gcc", "-dumpfullversion"), "12\\.[0-9.]+", deadline);
        verifyToolchain(List.of("/usr/bin/g++", "-dumpfullversion"), "12\\.[0-9.]+", deadline);
        verifyToolchain(List.of("/usr/bin/python3", "--version"), "Python 3\\.11\\.[0-9]+", deadline);
        verifyToolchain(List.of("/usr/local/bin/ymcloud-output-limit", "--version"),
                "ymcloud-output-limit 2", deadline);
        verifyToolchain(List.of("/usr/local/bin/ymcloud-output-limit", "65536",
                Long.toString(properties.getMaxFileBytes()), "/usr/bin/printf", "launcher-ready"),
                "launcher-ready", deadline);
        verifyIsolation(deadline);
        log.info("go-judge 启动检查通过 version={} phase={} elapsedMs={} concurrency={} memoryKiB={} processes={}",
                REQUIRED_VERSION, "startup", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                properties.getSandboxConcurrency(), properties.getMaxProblemMemoryKiB(), properties.getProcessLimit());
    }

    /**
     * 验证固定 Linux 版本及项目依赖的安全修复标记。
     * @param version 沙箱版本响应
     * @throws GoJudgeException 版本或能力标记不符合要求
     */
    static void validateVersion(JsonNode version) throws GoJudgeException {
        if (!version.isObject() || !REQUIRED_VERSION.equals(version.path("buildVersion").asText())
                || !"linux".equals(version.path("os").asText())
                || !version.path("fixSymlinkEscape").asBoolean()
                || !version.path("copyOutTruncate").asBoolean()) {
            throw new GoJudgeException("go-judge 必须运行固定的 Linux v1.13.3 正式版本");
        }
    }

    /**
     * 验证 cgroup v2、seccomp、控制器及独占磁盘文件缓存。
     * @param config 沙箱配置响应
     * @throws GoJudgeException 隔离或缓存配置不符合要求
     */
    static void validateConfig(JsonNode config) throws GoJudgeException {
        JsonNode runner = config.path("runnerConfig");
        Set<String> controllers = new HashSet<>();
        JsonNode configuredControllers = runner.path("cgroupControllers");
        if (configuredControllers.isArray()) {
            configuredControllers.forEach(controller -> controllers.add(controller.asText()));
        }
        if (!runner.isObject() || runner.path("cgroupType").asInt() != 2
                || !runner.path("seccomp").asBoolean()
                || !controllers.containsAll(Set.of("cpu", "memory", "pids"))
                || !"/w".equals(runner.path("workDir").asText())) {
            throw new GoJudgeException("go-judge 必须启用 cgroup v2 的 CPU、内存、进程限制及默认 seccomp");
        }
        String path = config.path("fileStorePath").asText();
        if (path.isBlank() || !path.startsWith("/") || path.startsWith("/dev/shm") || path.startsWith("/tmp")) {
            throw new GoJudgeException("go-judge 文件缓存必须使用独占磁盘目录");
        }
    }

    /**
     * 在真实沙箱执行探针并校验输出版本，失败时说明预期、实际输出及更新方式。
     * @param args 固定探针命令
     * @param expression 预期输出的完整匹配表达式
     * @param deadline 启动截止时间，UTC epoch 毫秒
     * @throws GoJudgeException 命令失败或版本输出不匹配
     */
    private void verifyToolchain(List<String> args, String expression, long deadline) throws GoJudgeException {
        JsonNode result = probe(args, deadline);
        String output = (result.path("files").path("stdout").asText() + "\n"
                + result.path("files").path("stderr").asText()).trim();
        if (!Pattern.matches(expression, output)) {
            throw new GoJudgeException("go-judge 沙箱工具链版本检查失败：" + args.get(0)
                    + "，预期匹配：" + expression + "，实际输出：" + output
                    + "。请使用当前项目源码重新构建 go-judge 镜像并重建容器，仅重启 Worker 不会更新沙箱工具链");
        }
    }

    /**
     * 在沙箱内验证权限、只读挂载及文件大小限制。
     * @param deadline 启动截止时间，UTC epoch 毫秒
     * @throws GoJudgeException 实际隔离不符合部署约束
     */
    private void verifyIsolation(long deadline) throws GoJudgeException {
        // 默认 sandbox 使用命名空间内的无特权 root；不将 UID 0 等同于宿主 root 权限。
        String script = "import os,resource\n"
                + "status=dict(line.split(':',1) for line in open('/proc/self/status') if ':' in line)\n"
                + "assert status.get('Seccomp','').strip()=='2'\n"
                + "assert all(int(status.get(k,'-1').strip(),16)==0 for k in ['CapEff','CapPrm','CapAmb'])\n"
                + "assert status.get('NoNewPrivs','').strip()=='1'\n"
                + "assert all(os.statvfs(p).f_flag & os.ST_RDONLY for p in ['/usr','/lib'])\n"
                + "assert resource.getrlimit(resource.RLIMIT_FSIZE)[0]==" + properties.getMaxFileBytes() + "\n"
                + "print('sandbox-ready')\n";
        verifyToolchain(List.of("/usr/bin/python3", "-I", "-c", script), "sandbox-ready", deadline);
    }

    /**
     * 以固定的小资源预算运行启动探针。
     * @param args 固定探针命令
     * @param deadline 启动截止时间，UTC epoch 毫秒
     * @return 唯一且正常退出的探针响应
     * @throws GoJudgeException 沙箱请求或探针运行失败
     */
    private JsonNode probe(List<String> args, long deadline) throws GoJudgeException {
        Map<String, Object> command = Map.ofEntries(
                Map.entry("args", args),
                Map.entry("env", List.of("PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "HOME=/w")),
                Map.entry("files", List.of(Map.of("content", ""),
                        Map.of("name", "stdout", "max", 32768, "pipe", true),
                        Map.of("name", "stderr", "max", 32768, "pipe", true),
                        Map.of("name", "launcher", "max", 256, "pipe", true))),
                Map.entry("cpuLimit", TimeUnit.SECONDS.toNanos(5)),
                Map.entry("clockLimit", TimeUnit.SECONDS.toNanos(10)),
                Map.entry("memoryLimit", properties.getCompileMemoryBytes()),
                Map.entry("procLimit", properties.getProcessLimit()),
                Map.entry("cpuRateLimit", properties.getCpuRateLimit()),
                Map.entry("copyOutMax", properties.getMaxFileBytes()));
        JsonNode result = client.execute(Map.of("cmd", List.of(command)), deadline).get(0);
        JsonNode diagnostic = result.path("files").path("launcher");
        if (!diagnostic.isTextual() || !diagnostic.textValue().isEmpty()
                || !"Accepted".equals(result.path("status").asText()) || result.path("exitStatus").asInt(-1) != 0) {
            throw new GoJudgeException("go-judge 沙箱启动探测执行失败：" + args.get(0));
        }
        return result;
    }
}

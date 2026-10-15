package com.ymcloud.web;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用独立 JVM 加载正式日志配置，避免改动测试进程的全局日志上下文。 */
class LoggingConfigurationTest {

    private static final String INFO_MESSAGE = "日志验证_INFO_中文唯一";
    private static final String WARN_MESSAGE = "日志验证_WARN_中文唯一";
    private static final String ERROR_MESSAGE = "日志验证_ERROR_中文唯一";
    private static final String LOGGER_NAME = "com.ymcloud.logging.Verification";
    private static final int FLUSH_EVENTS = 500;
    private static final int ROLL_EVENTS = 48;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");

    @ParameterizedTest
    @EnumSource(Service.class)
    void developmentAndProductionUseSameUtf8ConsoleAndSeparateFiles(Service service) throws Exception {
        for (String profile : List.of("dev", "prod")) {
            Path directory = runProbe(service, "messages", profile);
            assertMessages(directory.resolve("files/logs/" + service.logDirectory), service);
            assertConsole(directory.resolve("console.log"), service);
        }
    }

    @ParameterizedTest
    @EnumSource(Service.class)
    void standardLoggingFilePathEnvironmentOverrideControlsBothFiles(Service service) throws Exception {
        Path directory = runProbe(service, "environment", "prod");
        assertMessages(directory.resolve("override-中文"), service);
        assertConsole(directory.resolve("console.log"), service);
        assertFalse(Files.exists(directory.resolve("files/logs/" + service.logDirectory)));
    }

    @ParameterizedTest
    @EnumSource(Service.class)
    void shutdownFlushesPendingInfoAndErrorEventsExactlyOnce(Service service) throws Exception {
        Path directory = runProbe(service, "flush", "prod");
        Path logs = directory.resolve("files/logs/" + service.logDirectory);
        String application = Files.readString(logs.resolve("application.log"), StandardCharsets.UTF_8);
        String errors = Files.readString(logs.resolve("error.log"), StandardCharsets.UTF_8);
        assertEquals(FLUSH_EVENTS, application.lines().filter(line -> line.contains("FLUSH|")).count());
        assertEquals(FLUSH_EVENTS / 2, errors.lines().filter(line -> line.contains("FLUSH|")).count());
        for (int index = 0; index < FLUSH_EVENTS; index++) {
            String marker = "FLUSH|" + index + "|中文";
            assertEquals(1, occurrences(application, marker), marker);
            assertEquals(index % 2 == 0 ? 1 : 0, occurrences(errors, marker), marker);
        }
    }

    @ParameterizedTest
    @EnumSource(Service.class)
    void sizeAndDayRolloverProduceReadableGzipWithoutLoss(Service service) throws Exception {
        Path directory = runProbe(service, "rolling", "prod");
        Path logs = directory.resolve("files/logs/" + service.logDirectory);
        for (String file : List.of("application", "error")) {
            List<Path> archives;
            try (var files = Files.list(logs.resolve("archive"))) {
                archives = files.filter(path -> path.getFileName().toString().startsWith(file + ".")).sorted().toList();
            }
            assertTrue(archives.size() > 1, () -> "小文件上限应触发多次真实滚动\n" + diagnostics(directory));
            StringBuilder archived = new StringBuilder();
            boolean sizeLimitReached = false;
            for (Path archive : archives) {
                assertTrue(archive.getFileName().toString().matches(file + "\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+\\.log\\.gz"));
                try (var input = new GZIPInputStream(Files.newInputStream(archive))) {
                    byte[] bytes = input.readAllBytes();
                    assertTrue(bytes.length < 8192, "滚动归档只能超出 4KB 上限一个日志事件");
                    sizeLimitReached |= bytes.length >= 4096;
                    archived.append(new String(bytes, StandardCharsets.UTF_8));
                }
            }
            assertTrue(sizeLimitReached, "至少一个归档必须由大小上限触发");
            String current = Files.readString(logs.resolve(file + ".log"), StandardCharsets.UTF_8);
            assertTrue(current.contains("ROLL_DAY_END_中文"));
            assertFalse(archived.toString().contains("ROLL_DAY_END_中文"));
            assertFalse(current.contains("ROLL|"), "跨日后旧事件应全部归档");
            for (int index = 0; index < ROLL_EVENTS; index++) {
                assertEquals(1, occurrences(archived.toString(), "ROLL|" + index + "|中文"));
            }
            assertFalse(archived.toString().contains("\u001B"));
        }
    }

    private Path runProbe(Service service, String mode, String profile) throws Exception {
        Path repository = repository();
        Path output = Files.createDirectories(repository.resolve("target/phase3-verification/logging"));
        Path directory = Files.createTempDirectory(output, service.logDirectory + "-" + mode + "-" + profile + "-");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Dfile.encoding=UTF-8", "-cp", classpath, Probe.class.getName(),
                repository.toString(), service.name(), mode, profile, directory.toString());
        builder.directory(directory.toFile());
        builder.environment().keySet().removeIf(name -> List.of(
                "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(name));
        builder.redirectOutput(directory.resolve("console.log").toFile());
        builder.redirectError(directory.resolve("stderr.log").toFile());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "日志验证子进程超时：" + directory);
            assertEquals(0, process.exitValue(), () -> diagnostics(directory));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        return directory;
    }

    private Path repository() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("ymcloud-web/src/main/resources/application.yml"))
                    && Files.isRegularFile(path.resolve("ymcloud-judge/src/main/resources/application.yml"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("未找到两个模块的正式日志配置");
    }

    private String diagnostics(Path directory) {
        try {
            String diagnostics = directory + "\n" + Files.readString(directory.resolve("stderr.log"), StandardCharsets.UTF_8);
            Path rollingStatus = directory.resolve("rolling-status.log");
            if (Files.isRegularFile(rollingStatus)) {
                diagnostics += Files.readString(rollingStatus, StandardCharsets.UTF_8);
            }
            return diagnostics;
        } catch (IOException exception) {
            return directory + "：无法读取子进程诊断，" + exception.getClass().getSimpleName();
        }
    }

    private void assertMessages(Path logs, Service service) throws IOException {
        String application = Files.readString(logs.resolve("application.log"), StandardCharsets.UTF_8);
        String errors = Files.readString(logs.resolve("error.log"), StandardCharsets.UTF_8);
        for (String message : List.of(INFO_MESSAGE, WARN_MESSAGE, ERROR_MESSAGE)) {
            assertEquals(1, occurrences(application, message));
            assertEquals(message.equals(ERROR_MESSAGE) ? 1 : 0, occurrences(errors, message));
        }
        for (String content : List.of(application, errors)) {
            assertFalse(content.contains("日志验证_DEBUG"));
            assertFalse(content.contains("日志验证_TRACE"));
            assertFalse(content.contains("\u001B"));
            assertTrue(content.contains("java.lang.IllegalStateException: 异常根因中文"));
            assertTrue(content.contains("Caused by: java.lang.IllegalArgumentException: 底层异常中文"));
            assertTrue(content.contains("LoggingConfigurationTest$Probe"));
        }
        assertLine(application, INFO_MESSAGE, "INFO", service);
        assertLine(application, WARN_MESSAGE, "WARN", service);
        assertLine(errors, ERROR_MESSAGE, "ERROR", service);
    }

    private void assertConsole(Path console, Service service) throws IOException {
        String content = Files.readString(console, StandardCharsets.UTF_8);
        for (String message : List.of(INFO_MESSAGE, WARN_MESSAGE, ERROR_MESSAGE)) {
            assertEquals(1, occurrences(content, message));
        }
        assertTrue(lineWith(content, INFO_MESSAGE).contains("\u001B[32m"));
        assertTrue(lineWith(content, WARN_MESSAGE).contains("\u001B[33m"));
        assertTrue(lineWith(content, ERROR_MESSAGE).contains("\u001B[31m"));
        assertFalse(content.contains("日志验证_DEBUG"));
        assertFalse(content.contains("日志验证_TRACE"));
        assertLine(ANSI.matcher(content).replaceAll(""), INFO_MESSAGE, "INFO", service);
        assertTrue(content.contains("异常根因中文"));
        assertTrue(content.contains("底层异常中文"));
    }

    private void assertLine(String content, String message, String level, Service service) {
        String line = lineWith(content, message);
        assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*"), line);
        assertTrue(line.contains("[" + service.applicationName + "] [日志验证线程]"), line);
        assertTrue(line.contains(level), line);
        assertTrue(line.contains(LOGGER_NAME), line);
    }

    private String lineWith(String content, String marker) {
        return content.lines().filter(line -> line.contains(marker)).findFirst().orElseThrow();
    }

    private static int occurrences(String content, String marker) {
        return content.split(Pattern.quote(marker), -1).length - 1;
    }

    enum Service {
        WEB("ymcloud-web", "web", "ymcloud"),
        WORKER("ymcloud-judge", "worker", "ymcloud-judge-worker");

        private final String module;
        private final String logDirectory;
        private final String applicationName;

        Service(String module, String logDirectory, String applicationName) {
            this.module = module;
            this.logDirectory = logDirectory;
            this.applicationName = applicationName;
        }
    }

    /** 仅由测试子进程启动；不创建业务上下文，不连接外部服务。 */
    public static class Probe {

        public static void main(String[] arguments) throws Exception {
            Path resources = Path.of(arguments[0]).resolve(Service.valueOf(arguments[1]).module + "/src/main/resources");
            Service service = Service.valueOf(arguments[1]);
            String mode = arguments[2];
            StandardEnvironment environment = environment(resources, mode, arguments[3], Path.of(arguments[4]));
            Thread.currentThread().setName("日志验证线程");
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
            LogbackLoggingSystem system = new LogbackLoggingSystem(Probe.class.getClassLoader());
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(environment),
                    resources.resolve("logback-spring.xml").toUri().toString(), LogFile.get(environment));
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            try {
                assertConfiguration(context, resources, service, mode);
                Logger logger = context.getLogger(LOGGER_NAME);
                if (mode.equals("rolling")) {
                    roll(context, logger);
                } else if (mode.equals("flush")) {
                    flush(context, logger);
                } else {
                    logger.trace("日志验证_TRACE");
                    logger.debug("日志验证_DEBUG");
                    logger.info(INFO_MESSAGE);
                    logger.warn(WARN_MESSAGE);
                    logger.error(ERROR_MESSAGE, new IllegalStateException("异常根因中文", new IllegalArgumentException("底层异常中文")));
                }
            } finally {
                context.stop();
                if (mode.equals("rolling")) {
                    StringBuilder statuses = new StringBuilder();
                    context.getStatusManager().getCopyOfStatusList().forEach(status -> statuses.append(status).append('\n'));
                    Files.writeString(Path.of(arguments[4]).resolve("rolling-status.log"), statuses, StandardCharsets.UTF_8);
                }
            }
            assertTrue(context.getStatusManager().getCopyOfStatusList().stream().noneMatch(status -> status.getLevel() == Status.ERROR));
        }

        private static StandardEnvironment environment(Path resources, String mode, String profile, Path output) throws IOException {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            // 使用标准环境变量属性源测试名称转换，不继承宿主机路径或私有 profile 配置。
            if (mode.equals("environment")) {
                environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-environment",
                        Map.of("LOGGING_FILE_PATH", output.resolve("override-中文").toString())));
            }
            if (mode.equals("rolling")) {
                environment.getPropertySources().addFirst(new MapPropertySource("small-rolling-files",
                        Map.of("logging.logback.rollingpolicy.max-file-size", "4KB")));
            }
            for (var source : new YamlPropertySourceLoader().load("public-application", new FileSystemResource(resources.resolve("application.yml")))) {
                environment.getPropertySources().addLast(source);
            }
            environment.setActiveProfiles(profile);
            return environment;
        }

        private static void assertConfiguration(LoggerContext context, Path resources, Service service, String mode) throws Exception {
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            assertEquals(Level.INFO, root.getLevel());
            assertEquals(service.applicationName, context.getProperty("APP_NAME"));
            assertEquals(mode.equals("rolling") ? 4096 : 20 * 1024 * 1024,
                    FileSize.valueOf(context.getProperty("APP_LOG_MAX_FILE_SIZE")).getSize());
            assertEquals(200 * 1024 * 1024, FileSize.valueOf(context.getProperty("APP_LOG_TOTAL_SIZE_CAP")).getSize());
            List<String> names = new ArrayList<>();
            root.iteratorForAppenders().forEachRemaining(appender -> names.add(appender.getName()));
            assertEquals(3, names.size());
            assertEquals(Set.of("CONSOLE", "APPLICATION_ASYNC", "ERROR_ASYNC"), Set.copyOf(names));
            ConsoleAppender<?> console = assertInstanceOf(ConsoleAppender.class, root.getAppender("CONSOLE"));
            assertTrue(console.isStarted());
            assertEquals(StandardCharsets.UTF_8, assertInstanceOf(PatternLayoutEncoder.class, console.getEncoder()).getCharset());
            for (String name : List.of("APPLICATION", "ERROR")) {
                AsyncAppender async = async(context, name);
                assertTrue(async.isStarted());
                assertEquals(512, async.getQueueSize());
                assertEquals(0, async.getDiscardingThreshold());
                assertEquals(5000, async.getMaxFlushTime());
                assertFalse(async.isIncludeCallerData());
                assertFalse(async.isNeverBlock());
                RollingFileAppender<ILoggingEvent> file = file(context, name);
                assertTrue(file.isStarted());
                PatternLayoutEncoder encoder = assertInstanceOf(PatternLayoutEncoder.class, file.getEncoder());
                assertEquals(StandardCharsets.UTF_8, encoder.getCharset());
                assertFalse(encoder.getPattern().contains("%clr"));
                SizeAndTimeBasedRollingPolicy<?> policy = assertInstanceOf(SizeAndTimeBasedRollingPolicy.class, file.getRollingPolicy());
                assertTrue(policy.isStarted());
                assertEquals(15, policy.getMaxHistory());
                assertTrue(policy.isCleanHistoryOnStart());
                assertEquals(CompressionMode.GZ, policy.getCompressionMode());
                assertTrue(policy.getFileNamePattern().contains("%d{yyyy-MM-dd}.%i.log.gz"));
            }
            assertRollingPropertyBindings(resources.resolve("logback-spring.xml"));
        }

        private static void assertRollingPropertyBindings(Path configuration) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(configuration.toFile());
            assertEquals(0, document.getElementsByTagName("springProfile").getLength());
            var policies = document.getElementsByTagName("rollingPolicy");
            assertEquals(2, policies.getLength());
            for (int index = 0; index < policies.getLength(); index++) {
                Element policy = (Element) policies.item(index);
                assertEquals("${APP_LOG_MAX_FILE_SIZE}", policy.getElementsByTagName("maxFileSize").item(0).getTextContent());
                assertEquals("${APP_LOG_TOTAL_SIZE_CAP}", policy.getElementsByTagName("totalSizeCap").item(0).getTextContent());
            }
        }

        private static void roll(LoggerContext context, Logger logger) throws Exception {
            for (String name : List.of("APPLICATION", "ERROR")) {
                RollingFileAppender<ILoggingEvent> file = file(context, name);
                var policy = (SizeAndTimeBasedRollingPolicy<ILoggingEvent>) file.getRollingPolicy();
                awaitRollingJobs(policy);
                for (int index = 0; index < ROLL_EVENTS; index++) {
                    file.doAppend(event(logger, "ROLL|" + index + "|中文" + "x".repeat(256)));
                    awaitRollingJobs(policy);
                }
                var trigger = policy.getTimeBasedFileNamingAndTriggeringPolicy();
                trigger.setCurrentTime(trigger.getCurrentTime() + Duration.ofDays(2).toMillis());
                file.doAppend(event(logger, "ROLL_DAY_END_中文"));
                awaitRollingJobs(policy);
            }
        }

        /**
         * 滚动仍由正式策略和真实字节数触发，仅在测试推进虚拟时钟前等待后台任务结束。
         * Logback 1.5.18 的触发器与异步历史清理共用可变日历，不能将两天时间压缩到尚未完成的清理中。
         * 逐次等待也避免后续滚动覆盖策略保存的 Future，禁止用固定休眠或减少归档断言规避竞态。
         */
        private static void awaitRollingJobs(SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy) throws Exception {
            for (String field : List.of("compressionFuture", "cleanUpFuture")) {
                Object pending = ReflectionTestUtils.getField(policy, field);
                if (pending != null) {
                    assertInstanceOf(Future.class, pending).get(5, TimeUnit.SECONDS);
                }
            }
        }

        private static void flush(LoggerContext context, Logger logger) throws Exception {
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            for (String name : List.of("APPLICATION", "ERROR")) {
                BlockingFilter filter = new BlockingFilter(entered, release);
                filter.start();
                RollingFileAppender<ILoggingEvent> appender = file(context, name);
                List<Filter<ILoggingEvent>> existing = appender.getCopyOfAttachedFiltersList();
                appender.clearAllFilters();
                appender.addFilter(filter);
                existing.forEach(appender::addFilter);
            }
            try {
                logger.error("FLUSH|{}|中文", 0);
                assertTrue(entered.await(5, TimeUnit.SECONDS), "两个文件写线程应已暂停");
                for (int index = 1; index < FLUSH_EVENTS; index++) {
                    if (index % 2 == 0) {
                        logger.error("FLUSH|{}|中文", index);
                    } else {
                        logger.info("FLUSH|{}|中文", index);
                    }
                }
                AsyncAppender application = async(context, "APPLICATION");
                assertTrue(application.getNumberOfElementsInQueue() > 400);
                assertTrue(async(context, "ERROR").getNumberOfElementsInQueue() > 200);
                FutureTask<Void> shutdown = new FutureTask<>(() -> {
                    context.stop();
                    return null;
                });
                Thread stopping = new Thread(shutdown, "日志关闭验证");
                stopping.setDaemon(true);
                stopping.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (application.isStarted() && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertFalse(application.isStarted());
                assertFalse(shutdown.isDone(), "待写队列尚未释放时，关闭应等待刷新");
                release.countDown();
                shutdown.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }

        private static AsyncAppender async(LoggerContext context, String name) {
            return assertInstanceOf(AsyncAppender.class, context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(name + "_ASYNC"));
        }

        @SuppressWarnings("unchecked")
        private static RollingFileAppender<ILoggingEvent> file(LoggerContext context, String name) {
            return assertInstanceOf(RollingFileAppender.class, async(context, name).getAppender(name));
        }

        private static LoggingEvent event(Logger logger, String message) {
            return new LoggingEvent(Logger.class.getName(), logger, Level.ERROR, message, null, null);
        }
    }

    /** 仅暂停测试创建的事件，保证关闭测试确实面对尚未刷出的异步队列。 */
    private static class BlockingFilter extends Filter<ILoggingEvent> {

        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicBoolean first = new AtomicBoolean(true);

        private BlockingFilter(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public FilterReply decide(ILoggingEvent event) {
            if (!first.compareAndSet(true, false)) {
                return FilterReply.NEUTRAL;
            }
            entered.countDown();
            boolean interrupted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            try {
                while (release.getCount() > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new IllegalStateException("测试日志写入等待超时");
                    }
                    try {
                        if (release.await(remaining, TimeUnit.NANOSECONDS)) {
                            break;
                        }
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return FilterReply.NEUTRAL;
        }
    }
}

#!/usr/bin/env python3
"""Opt-in Linux filesystem verification of the packaged Worker in local Docker.

Uses the existing oj-java-env image, no Docker socket in the test container, no
database/message broker/Redis, and no application credentials or real files.
Set YMCLOUD_WORKER_LINUX_TEST=true explicitly before running this script.
"""

import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import uuid
import zipfile


PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[5]
JAVA_HELPER = r'''
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.data.TestDataCacheProperties;
import com.ymcloud.judge.data.TestDataSource;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.io.WorkerPaths;
import com.ymcloud.pojo.contract.judge.TestDataManifest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class LinuxWorkerCheck {

    private static final Path STATE = Path.of("/state");
    private static final byte[] INPUT = " \tfirst\r\nlast  \n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OUTPUT = "answer\n".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] arguments) throws Exception {
        check(Files.getFileStore(STATE).supportsFileAttributeView("posix"), "Linux POSIX filesystem required");
        check(((Number) Files.getAttribute(STATE, "unix:uid")).intValue() == 1000, "State owner must be UID 1000");
        check(((Number) Files.getAttribute(STATE, "unix:gid")).intValue() == 1000, "State group must be GID 1000");
        permissions(STATE, "rwx------");
        verifyCache();
        verifyFactoryAndExecution();
        System.out.println("PASS Linux UID/GID, private permissions, cold/warm/restart/corrupt cache, "
                + "symlink rejection, Factory isolation and raw stdin");
    }

    private static void verifyCache() throws Exception {
        Source source = new Source();
        TestDataCacheProperties properties = properties(STATE.resolve("cache"));
        Path input;
        try (TestDataCache cache = new TestDataCache(source, properties)) {
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash)) {
                check(lease.entries().size() == 1, "Unexpected cached cases");
                input = lease.entries().get(0).inputPath();
                check(Arrays.equals(read(lease.entries().get(0).openInput()), INPUT), "Raw input changed");
                check(Arrays.equals(read(lease.entries().get(0).openOutput()), OUTPUT), "Raw output changed");
                permissions(input.getParent(), "rwx------");
                permissions(input, "rw-------");
                permissions(lease.entries().get(0).outputPath(), "rw-------");
                permissions(input.getParent().resolve("manifest.json"), "rw-------");
                permissions(input.getParent().resolve("manifest.sha256"), "rw-------");
            }
            permissions(Path.of(properties.getDirectory()), "rwx------");
            permissions(Path.of(properties.getDirectory()).resolve(".worker.lock"), "rw-------");
            try (TestDataCache.Lease lease = cache.acquire(10L, 7L, source.hash)) {
                check(Arrays.equals(read(lease.entries().get(0).openInput()), INPUT), "Warm bytes changed");
            }
            check(source.manifestReads == 1 && source.fileReads == 2, "Warm cache downloaded again");
            check(cache.statistics().hits() == 1 && cache.statistics().misses() == 1, "Cold/warm counters changed");
            expectIOException(() -> cache.acquire(10L, 7L, "0".repeat(64)), "Wrong task hash accepted");
            check(Files.exists(input), "Wrong task deleted a valid cached version");
        }
        int reads = source.fileReads;
        Files.setPosixFilePermissions(input.getParent(), PosixFilePermissions.fromString("rwxr-xr-x"));
        try (var files = Files.list(input.getParent())) {
            for (Path file : files.toList()) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
            }
        }
        try (TestDataCache recovered = new TestDataCache(source, properties);
             TestDataCache.Lease lease = recovered.acquire(10L, 7L, source.hash)) {
            check(Arrays.equals(read(lease.entries().get(0).openInput()), INPUT), "Recovered cache bytes changed");
            check(source.fileReads == reads, "Restart did not reuse verified cache");
            permissions(input.getParent(), "rwx------");
            try (var files = Files.list(input.getParent())) {
                for (Path file : files.toList()) {
                    permissions(file, "rw-------");
                }
            }
        }
        byte[] corrupt = INPUT.clone();
        Arrays.fill(corrupt, (byte) 'x');
        Files.write(input, corrupt);
        try (TestDataCache recovered = new TestDataCache(source, properties);
             TestDataCache.Lease lease = recovered.acquire(10L, 7L, source.hash)) {
            check(Arrays.equals(read(lease.entries().get(0).openInput()), INPUT), "Corrupt cache was not repaired");
            check(source.fileReads == reads + 2, "Corrupt recovery did not fetch both files");
            permissions(lease.entries().get(0).inputPath(), "rw-------");
        }

        Path outside = STATE.resolve("outside");
        Files.createDirectory(outside);
        Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rwx------"));
        Path marker = outside.resolve("untouched.txt");
        Files.writeString(marker, "outside-data", StandardCharsets.UTF_8);
        Path rootLink = STATE.resolve("linked-cache");
        Files.createSymbolicLink(rootLink, outside);
        expectIOException(() -> new TestDataCache(source, properties(rootLink)), "Symlink cache root accepted");
        expectIOException(() -> new TestDataCache(source, properties(rootLink.resolve("child"))), "Symlink cache ancestor accepted");
        expectIOException(() -> WorkerPaths.rejectSymbolicLinks(rootLink.resolve("child")), "Runtime symlink ancestor accepted");
        check(!Files.exists(outside.resolve("child")), "Rejected cache changed the symlink target");

        Files.delete(input);
        Files.createSymbolicLink(input, marker);
        try (TestDataCache recovered = new TestDataCache(source, properties);
             TestDataCache.Lease lease = recovered.acquire(10L, 7L, source.hash)) {
            check(!Files.isSymbolicLink(lease.entries().get(0).inputPath()), "Linked cached file retained");
            check(Arrays.equals(read(lease.entries().get(0).openInput()), INPUT), "Linked cache was not repaired");
        }
        check(Files.readString(marker).equals("outside-data"), "Cache cleanup followed a symlink");
    }

    private static void verifyFactoryAndExecution() throws Exception {
        Path work = STATE.resolve("work");
        Files.createDirectory(work);
        Files.setPosixFilePermissions(work, PosixFilePermissions.fromString("rwx------"));
        Map<String, Object> calls = new HashMap<>();
        CreateContainerCmd command = (CreateContainerCmd) Proxy.newProxyInstance(
                LinuxWorkerCheck.class.getClassLoader(), new Class<?>[]{CreateContainerCmd.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("exec")) {
                        CreateContainerResponse response = new CreateContainerResponse();
                        response.setId("fake-local-test-only");
                        return response;
                    }
                    if (method.getName().startsWith("with")) {
                        calls.put(method.getName(), values[0]);
                        return proxy;
                    }
                    if (method.getName().equals("toString")) {
                        return "local-test-proxy";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        DockerClient client = (DockerClient) Proxy.newProxyInstance(
                LinuxWorkerCheck.class.getClassLoader(), new Class<?>[]{DockerClient.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("createContainerCmd")) {
                        calls.put("image", values[0]);
                        return command;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        new CodeExecContainerFactory(client, work.toString(), "/app", 262144L, "oj-java-env")
                .createDockerContainer();
        check("1000:1000".equals(calls.get("withUser")), "Factory did not use private directory owner");
        check("/app".equals(calls.get("withWorkingDir")), "Container working directory changed");
        check("oj-java-env".equals(calls.get("image")), "Factory image changed");
        HostConfig config = (HostConfig) calls.get("withHostConfig");
        check(config != null, "Factory omitted HostConfig");
        check("none".equals(config.getNetworkMode()), "Sandbox network enabled");
        check(Boolean.TRUE.equals(config.getReadonlyRootfs()), "Sandbox root is writable");
        check(Long.valueOf(262144L * 1024).equals(config.getMemory()), "Memory units changed");
        check(config.getMemory().equals(config.getMemorySwap()), "Swap is enabled");
        check(Long.valueOf(64).equals(config.getPidsLimit()), "Process limit changed");
        check(Arrays.stream(config.getCapDrop()).anyMatch(value -> value.name().equals("ALL")), "Capabilities retained");
        check(config.getSecurityOpts().contains("no-new-privileges:true"), "Privilege escalation is enabled");
        check(config.getBinds().length == 1 && config.getBinds()[0].getPath().equals(work.toString()), "Unexpected mount source");
        check(config.getTmpFs().get("/tmp").contains("noexec"), "Executable /tmp enabled");

        Path rootLink = STATE.resolve("linked-cache");
        boolean rejected = false;
        try {
            new CodeExecContainerFactory(client, rootLink.toString(), "/app", 262144L, "oj-java-env");
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, "Factory accepted a symbolic work directory");

        Path program = work.resolve("Echo.java");
        String programSource = """
                public class Echo {
                    public static void main(String[] args) throws Exception {
                        System.in.transferTo(System.out);
                    }
                }
                """;
        Files.writeString(program, programSource, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rw-------"));
        Path stdin = work.resolve("stdin");
        Files.write(stdin, INPUT);
        Files.setPosixFilePermissions(stdin, PosixFilePermissions.fromString("rw-------"));
        Path compiled = work.resolve("compile.log");
        Process compile = new ProcessBuilder("javac", "--release", "17", program.toString())
                .redirectErrorStream(true).redirectOutput(compiled.toFile()).start();
        check(compile.waitFor(30, TimeUnit.SECONDS) && compile.exitValue() == 0, "Private work directory Java compilation failed");
        Path stdout = work.resolve("stdout");
        Process run = new ProcessBuilder("java", "-Xmx64m", "-cp", work.toString(), "Echo")
                .redirectInput(stdin.toFile()).redirectOutput(stdout.toFile())
                .redirectError(work.resolve("run.log").toFile()).start();
        check(run.waitFor(10, TimeUnit.SECONDS) && run.exitValue() == 0, "Private work directory Java execution failed");
        check(Arrays.equals(Files.readAllBytes(stdout), INPUT), "Linux execution changed raw stdin");
    }

    private static TestDataCacheProperties properties(Path directory) {
        TestDataCacheProperties result = new TestDataCacheProperties();
        result.setDirectory(directory.toString());
        result.setMaxBytes(1024 * 1024);
        result.setMaxVersionBytes(1024 * 1024);
        result.setMaxFileBytes(1024);
        return result;
    }

    private static void permissions(Path path, String mode) throws IOException {
        check(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                .equals(PosixFilePermissions.fromString(mode)), "Incorrect private POSIX permissions");
    }

    private static byte[] read(InputStream stream) throws IOException {
        try (stream) {
            return stream.readAllBytes();
        }
    }

    private static String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectIOException(Action action, String label) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(label);
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static final class Source implements TestDataSource {
        private final byte[] manifest;
        private final String hash;
        private int manifestReads;
        private int fileReads;

        private Source() throws Exception {
            TestDataManifest data = new TestDataManifest();
            data.setProblemId(10L);
            data.setVersionId(7L);
            TestDataManifest.Entry entry = new TestDataManifest.Entry();
            entry.setId(1);
            entry.setInput("/in/1.in");
            entry.setOutput("/out/1.out");
            entry.setInputFileId(701L);
            entry.setOutputFileId(702L);
            entry.setInputSize((long) INPUT.length);
            entry.setOutputSize((long) OUTPUT.length);
            entry.setInputSha256(hash(INPUT));
            entry.setOutputSha256(hash(OUTPUT));
            data.setEntries(List.of(entry));
            manifest = new ObjectMapper().writeValueAsBytes(data);
            hash = hash(manifest);
        }

        @Override
        public byte[] fetchManifest(Long problemId, Long versionId) {
            check(problemId == 10L && versionId == 7L, "Unexpected source version");
            manifestReads++;
            return manifest.clone();
        }

        @Override
        public InputStream openFile(Long problemId, Long versionId, Long fileId) {
            check(problemId == 10L && versionId == 7L, "Unexpected file source version");
            check(fileId == 701L || fileId == 702L, "Unexpected source file");
            fileReads++;
            return new ByteArrayInputStream(fileId == 701L ? INPUT : OUTPUT);
        }
    }
}
'''


def require(condition, label):
    if not condition:
        raise AssertionError(label)


def confined_test_path(path, target):
    project = PROJECT_ROOT.resolve()
    absolute_target = pathlib.Path(os.path.abspath(target))
    absolute_path = pathlib.Path(os.path.abspath(path))
    require(absolute_target.is_relative_to(project), "Linux test root is outside this project")
    require(absolute_path.is_relative_to(absolute_target), "Linux test path is outside its dedicated root")
    for candidate in [absolute_target, absolute_path]:
        current = project
        for part in candidate.relative_to(project).parts:
            current = current / part
            require(not current.is_symlink() and current.resolve() == current,
                    "Linux test path contains a symlink or directory junction")
    return absolute_path


def hidden_process_options():
    return {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}


def command(arguments, timeout=30):
    result = subprocess.run(arguments, capture_output=True, timeout=timeout,
                            text=True, encoding="utf-8", **hidden_process_options())
    require(result.returncode == 0, "Local Docker test command failed")
    return result.stdout.strip()


def local_daemon():
    host = os.environ.get("DOCKER_HOST") or command(
        ["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"])
    require(host.startswith(("npipe://", "unix://", "tcp://127.0.0.1:", "tcp://localhost:")),
            "Linux verification requires the local Docker daemon")


def extract_worker(jar, folder):
    with zipfile.ZipFile(jar) as source:
        require("BOOT-INF/classes/com/ymcloud/judge/data/TestDataCache.class" in source.namelist(),
                "Build the current independent Worker Boot jar first")
        for item in source.infolist():
            if item.is_dir():
                continue
            if item.filename.startswith("BOOT-INF/classes/"):
                relative = pathlib.PurePosixPath("classes") / item.filename[len("BOOT-INF/classes/"):]
            elif item.filename.startswith("BOOT-INF/lib/") and item.filename.endswith(".jar"):
                relative = pathlib.PurePosixPath("lib") / item.filename[len("BOOT-INF/lib/"):]
            else:
                continue
            require(not relative.is_absolute() and ".." not in relative.parts and "\\" not in str(relative),
                    "Invalid packaged Worker path")
            destination = folder.joinpath(*relative.parts)
            require(destination.resolve().is_relative_to(folder.resolve()), "Extracted file escaped the test directory")
            destination.parent.mkdir(parents=True, exist_ok=True)
            with source.open(item) as input_file, destination.open("wb") as output:
                shutil.copyfileobj(input_file, output, length=64 * 1024)
            if os.name != "nt":
                destination.chmod(0o444)
    if os.name != "nt":
        for directory in folder.rglob("*"):
            if directory.is_dir():
                directory.chmod(0o755)


def cleanup_container(container, identifier):
    if container is None:
        return
    require(re.fullmatch(r"[0-9a-f]{64}", container), "Invalid owned Docker container ID")
    inspected = subprocess.run(
        ["docker", "inspect", "--format", '{{json .Config.Labels}}', container],
        capture_output=True, text=True, encoding="utf-8", timeout=30, **hidden_process_options())
    if inspected.returncode != 0:
        return
    labels = json.loads(inspected.stdout)
    require(labels.get("com.ymcloud.phase2-linux-test") == identifier, "Refusing to delete another Docker container")
    command(["docker", "rm", "-f", container])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=pathlib.Path,
                        default=PROJECT_ROOT / "ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar")
    args = parser.parse_args()
    require(os.environ.get("YMCLOUD_WORKER_LINUX_TEST", "").lower() == "true", "Explicit Linux test opt-in is required")
    require(args.jar.resolve().is_relative_to(PROJECT_ROOT.resolve()), "Worker jar must be in this project")
    require(args.jar.is_file(), "Packaged Worker jar is missing")
    identifier = uuid.uuid4().hex
    target = confined_test_path(PROJECT_ROOT / "target" / "phase2-linux", PROJECT_ROOT / "target" / "phase2-linux")
    folder = confined_test_path(target / identifier, target)
    require(folder.resolve().parent == target, "Linux test directory escaped ignored target")
    container = None
    succeeded = False
    try:
        local_daemon()
        # Docker 29 may resolve short names differently during image inspection.
        command(["docker", "image", "inspect", "docker.io/library/oj-java-env:latest", "--format", "{{.Id}}"])
        folder.mkdir(parents=True, exist_ok=False)
        if os.name != "nt":
            folder.chmod(0o755)
        extract_worker(args.jar, folder)
        helper = folder / "LinuxWorkerCheck.java"
        helper.write_text(JAVA_HELPER, encoding="utf-8", newline="\n")
        if os.name != "nt":
            helper.chmod(0o444)
        script = ('mkdir /state/classes && javac --release 17 -cp "/fixture/classes:/fixture/lib/*" '
                  '-d /state/classes /fixture/LinuxWorkerCheck.java && '
                  'java -Xmx192m -cp "/state/classes:/fixture/classes:/fixture/lib/*" LinuxWorkerCheck')
        container = command([
            "docker", "create", "--rm", "--name", "ymcloud-worker-linux-" + identifier,
            "--label", "com.ymcloud.phase2-linux-test=" + identifier,
            "--user", "1000:1000", "--read-only", "--network", "none", "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges:true", "--pids-limit", "96",
            "--memory", "512m", "--memory-swap", "512m", "--cpus", "1",
            "--tmpfs", "/state:rw,nosuid,nodev,size=128m,uid=1000,gid=1000,mode=0700",
            "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=64m,uid=1000,gid=1000,mode=1777",
            "--mount", "type=bind,source=" + str(folder.resolve()) + ",target=/fixture,readonly",
            "--entrypoint", "/bin/sh", "oj-java-env", "-c", script])
        require(re.fullmatch(r"[0-9a-f]{64}", container), "Invalid created test container ID")
        output = command(["docker", "start", "-a", container], timeout=150)
        # Worker 生命周期日志可能先于最终断言摘要输出，仍须找到完整的通过标记。
        summary = next((line for line in output.splitlines() if line.startswith("PASS Linux ")), None)
        require(summary is not None, "Linux Worker assertions did not complete")
        print(summary)
        succeeded = True
    except Exception as error:
        print(json.dumps({"ok": False, "errorType": type(error).__name__}))
    finally:
        try:
            cleanup_container(container, identifier)
        except Exception as error:
            succeeded = False
            print(json.dumps({"ok": False, "cleanup": "ownedContainer", "errorType": type(error).__name__}))
        try:
            require(re.fullmatch(r"[0-9a-f]{32}", identifier), "Invalid generated test directory ID")
            require(confined_test_path(folder, target).parent == target, "Refusing to remove a foreign test directory")
            if folder.exists():
                shutil.rmtree(folder)
        except Exception as error:
            succeeded = False
            print(json.dumps({"ok": False, "cleanup": "ownedFiles", "errorType": type(error).__name__}))
    return 0 if succeeded else 1


if __name__ == "__main__":
    sys.exit(main())

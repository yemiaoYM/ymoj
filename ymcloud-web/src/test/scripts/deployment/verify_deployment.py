#!/usr/bin/env python3
"""Validate a release; --live mutates only an explicitly isolated test stack.

Run against a copied release under target/phase3-verification/full-stack-test.
The caller starts the stack. Only --live --recreate removes and recreates its
verified isolated containers; named volumes are retained. Development connection
settings are never read. Compose output and credentials stay in memory; reports
contain only check names, verdicts and elapsed times.
"""

import argparse
import http.client
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.parse
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[5]
DOCKER_DIRECTORY = ROOT / "docker"
if str(DOCKER_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(DOCKER_DIRECTORY))

from project_version import project_version


TEST_ROOT = ROOT / "target" / "phase3-verification" / "full-stack-test"
SERVICES = {"preflight", "mysql", "redis", "rabbitmq", "web", "worker", "go-judge", "frontend"}
PROJECT_SERVICES = {"web", "worker", "go-judge", "frontend"}
UPSTREAM_VERSIONS = {"mysql": "8.4", "redis": "7.4-alpine", "rabbitmq": "4.1-alpine"}
IMAGE_ID = re.compile(r"sha256:[a-f0-9]{64}")
REPOSITORY_TAG = re.compile(r"([a-z0-9][a-z0-9./:_-]*):([A-Za-z0-9_][A-Za-z0-9_.-]{0,127})")
ACTIVE = {"Pending", "Compiling", "Judging", "Submitting"}
TERMINAL = {"Accepted", "Wrong Answer", "Compile Error", "Time Limit Exceeded",
            "Memory Limit Exceeded", "Runtime Error", "System Error", "Partial Accepted",
            "Cancelled", "Submitted Failed", "Presentation Error"}
SOURCES = {
    "C": '#include <stdio.h>\nint main(void) { long a,b; if(scanf("%ld%ld",&a,&b)==2) printf("%ld\\n",a+b); return 0; }\n',
    "C++": '#include <iostream>\nint main() { long a,b; std::cin >> a >> b; std::cout << a+b << "\\n"; }\n',
    "Java": ('import java.util.Scanner;\nclass Helper { static long sum(long a,long b) { return a+b; } }\n'
             'public class Main { public static void main(String[] args) { Scanner s=new Scanner(System.in); '
             'System.out.println(Helper.sum(s.nextLong(),s.nextLong())); } }\n'),
    "Python": 'import sys\na,b=map(int,sys.stdin.read().split())\nprint(a+b)\n',
}
TEST_CASE_FILES = (("01.in", b"2 3\n"), ("01.out", b"5\n"),
                   ("02.in", b"-9 17\n"), ("02.out", b"8\n"))


def require(condition, label):
    if not condition:
        raise AssertionError(label)


def confined_path(path, root=TEST_ROOT):
    """Reject both lexical escapes and symlinks/junctions before live writes."""
    absolute = pathlib.Path(os.path.abspath(path))
    boundary = pathlib.Path(os.path.abspath(root))
    require(absolute.is_relative_to(boundary), "Live paths must stay inside the dedicated test directory")
    current = ROOT
    require(boundary.is_relative_to(ROOT), "Test root is outside this repository")
    for part in absolute.relative_to(ROOT).parts:
        current = current / part
        require(not current.is_symlink() and current.resolve() == current,
                "Live paths must not contain symlinks or directory junctions")
    return absolute


def command(arguments, label, input_text=None, timeout=60):
    options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    result = subprocess.run(arguments, input=input_text, capture_output=True, text=True,
                            encoding="utf-8", errors="strict", timeout=timeout, **options)
    # Raw stdout/stderr may contain credentials, resolved configuration or source.
    require(result.returncode == 0, label)
    return result.stdout


def positive_integer(value, label):
    """Compose versions serialize normalized byte limits as either strings or integers."""
    require(type(value) in (int, str) and re.fullmatch(r"[0-9]+", str(value)), label)
    result = int(value)
    require(result > 0, label)
    return result


def parse_compose_config(raw):
    """Undo Compose's re-escaping of literal dollars in rendered environment values."""
    model = json.loads(raw)
    for service in model.get("services", {}).values():
        environment = service.get("environment", {})
        for key, value in environment.items():
            if isinstance(value, str):
                environment[key] = value.replace("$$", "$")
    return model


def canonical_repository_tag(reference):
    """Normalize Docker Hub aliases while preserving the explicit version tag."""
    match = REPOSITORY_TAG.fullmatch(reference) if isinstance(reference, str) else None
    require(match is not None and (":" not in match[1] or match[1].rfind(":") < match[1].find("/")),
            "Expected an explicit repository version tag")
    repository, tag = match.groups()
    parts = repository.split("/")
    if parts[0] in ("docker.io", "index.docker.io"):
        parts = parts[1:]
        repository = "docker.io/" + ("library/" if len(parts) == 1 else "") + "/".join(parts)
    elif "." not in parts[0] and ":" not in parts[0] and parts[0] != "localhost":
        repository = "docker.io/" + ("library/" if len(parts) == 1 else "") + repository
    return repository + ":" + tag


def release_image_tag(service):
    service = "web" if service == "preflight" else service
    require(service in PROJECT_SERVICES or service in UPSTREAM_VERSIONS, "Unexpected release service")
    version = project_version(ROOT) if service in PROJECT_SERVICES else UPSTREAM_VERSIONS[service]
    return service + "-" + version


def check_image_tag(reference, service):
    normalized = canonical_repository_tag(reference)
    require(normalized.rsplit(":", 1)[1] == release_image_tag(service),
            "Release image must use its exact service version tag: " + service)
    return normalized


def check_shared_credentials(services):
    """A configured secret must reach every producer and consumer unchanged."""
    destinations = {
        "MYSQL_ROOT_PASSWORD": (("mysql", "MYSQL_ROOT_PASSWORD"),),
        "MYSQL_PASSWORD": (("mysql", "MYSQL_PASSWORD"), ("web", "SPRING_DATASOURCE_PASSWORD")),
        "REDIS_PASSWORD": (("redis", "REDIS_PASSWORD"), ("web", "SPRING_DATA_REDIS_PASSWORD"),
                           ("worker", "SPRING_DATA_REDIS_PASSWORD")),
        "RABBITMQ_PASSWORD": (("rabbitmq", "RABBITMQ_DEFAULT_PASS"), ("web", "SPRING_RABBITMQ_PASSWORD"),
                              ("worker", "SPRING_RABBITMQ_PASSWORD")),
        "TOKEN_SECRET": (("web", "YMCLOUD_TOKEN_SECRET"),),
        "WORKER_TOKEN": (("web", "YMCLOUD_WORKER_SERVICE_TOKEN"), ("worker", "YMCLOUD_WORKER_SERVICE_TOKEN")),
        "GO_JUDGE_TOKEN": (("worker", "YMCLOUD_GO_JUDGE_TOKEN"), ("go-judge", "YMCLOUD_GO_JUDGE_TOKEN")),
    }
    checked = services["preflight"].get("environment", {})
    for key, consumers in destinations.items():
        value = checked.get(key)
        require(isinstance(value, str) and bool(value), "Preflight is missing a configured credential: " + key)
        for name, target in consumers:
            require(services[name].get("environment", {}).get(target) == value,
                    "Configured credential differs between preflight and " + name + ": " + target)


def check_contract(config, directory, live=False):
    """Check the normalized Compose model without logging its environment."""
    services = config.get("services", {})
    require(set(services) == SERVICES, "The release must contain the complete eight-service stack")
    require(not any(path.suffix.lower() == ".py" for path in directory.rglob("*")),
            "Deployment packages must not contain external Python scripts")
    require(not (directory / ".env.example").exists(), "Deployment packages must provide .env directly")
    preflight = services["preflight"]
    require(preflight.get("image") == services["web"].get("image")
            and preflight.get("entrypoint") == ["python3", "/opt/ymcloud/deployment-preflight.py"]
            and not preflight.get("volumes"), "Preflight must use the script built into the Web image")
    check_shared_credentials(services)
    for name, service in services.items():
        reference = service.get("image")
        require(isinstance(reference, str) and reference and "__YMCLOUD_" not in reference,
                "Release image placeholders must be resolved")
        local = bool(IMAGE_ID.fullmatch(reference))
        if not local:
            check_image_tag(reference, name)
        require("build" not in service, "Deployment must not build images on the user's machine")
        require(service.get("pull_policy") == ("never" if local else "missing"),
                "Local image IDs require never; repository version tags require missing")
        require(service.get("platform") == "linux/amd64", "Local release platform must be explicit")
        require(not service.get("container_name"), "Container names must remain scoped to the Compose project")
        require(service.get("network_mode") != "host", "Full-stack services must not use host networking")
        require(not service.get("privileged") or name == "go-judge", "Only go-judge may be privileged")
        require(not service.get("ports") or name == "frontend", "Only the frontend may publish host ports")
        positive_integer(service.get("mem_limit"), "Every service needs a positive memory limit")
        positive_integer(service.get("pids_limit"), "Every service needs a positive process limit")
        logging = service.get("logging", {})
        require(logging.get("driver") == "json-file" and logging.get("options", {}).get("max-size")
                and logging.get("options", {}).get("max-file"), "Every service needs bounded log rotation")
        require(name == "preflight" or bool(service.get("healthcheck", {}).get("test")),
                "Every long-running service needs a healthcheck")
        for mount in service.get("volumes", []):
            require("docker.sock" not in str(mount.get("source", "")), "Docker socket mounts are forbidden")
            if mount.get("type") == "bind":
                source = pathlib.Path(mount["source"]).resolve()
                seed = (name == "mysql"
                        and mount.get("target") == "/docker-entrypoint-initdb.d/01-ymcloud.sql"
                        and source in {directory.resolve() / "initdb/ymcloud.sql",
                                       directory.resolve().parent / "sql/ymcloud.sql"}
                        and not mount.get("bind", {}).get("create_host_path", False))
                require((source.is_relative_to(directory.resolve()) or seed) and mount.get("read_only"),
                        "Release bind mounts must be read-only files inside the release")
                if mount.get("target") == "/docker-entrypoint-initdb.d/01-ymcloud.sql":
                    require(seed, "Seed SQL must use its declared path without creating missing host files")
                if live:
                    confined_path(source)
    judge = services["go-judge"]
    require(judge.get("privileged") is True and judge.get("cgroup") == "private",
            "go-judge needs its dedicated private cgroup namespace")
    require(positive_integer(judge.get("memswap_limit"), "go-judge needs an explicit swap limit")
            == positive_integer(judge.get("mem_limit"), "go-judge needs an explicit memory limit"),
            "go-judge swap must be disabled")
    for name in ("web", "worker"):
        service = services[name]
        require(service.get("user") == "10001:10001" and service.get("read_only"),
                "Application containers must be non-root with read-only roots")
        env = service.get("environment", {})
        require(env.get("SPRING_DATA_REDIS_HOST") == "redis"
                and env.get("SPRING_RABBITMQ_HOST") == "rabbitmq", "Application services must use the bundled data services")
    require(services["web"]["environment"].get("SPRING_DATASOURCE_URL", "").startswith("jdbc:mysql://mysql:3306/ymcloud?"),
            "Web must use the isolated bundled database")
    worker_env = services["worker"]["environment"]
    require(worker_env.get("YMCLOUD_WORKER_API_URL") == "http://web:9091"
            and worker_env.get("YMCLOUD_GO_JUDGE_URL") == "http://go-judge:5050",
            "Worker endpoints must remain inside the bundled stack")
    for name in ("data", "judge"):
        require(config.get("networks", {}).get(name, {}).get("internal"), "Data and sandbox networks must be internal")
    if live:
        confined_path(directory)
        project = config.get("name", "")
        require(re.fullmatch(r"ymcloud-e2e-[a-z0-9-]{8,40}", project), "Live project name must use ymcloud-e2e- and a unique suffix")
        for kind in ("volumes", "networks"):
            for item in config.get(kind, {}).values():
                require(not item.get("external") and item.get("name", "").startswith(project + "_"),
                        "Live resources must be owned by this isolated Compose project")
        ports = services["frontend"].get("ports", [])
        require(len(ports) == 1 and ports[0].get("host_ip") == "127.0.0.1",
                "Live frontend must publish one loopback-only port")


class Verification:
    def __init__(self, args):
        self.args = args
        self.directory = pathlib.Path(args.project_dir).resolve()
        if args.live:
            confined_path(pathlib.Path(args.project_dir))
        require((self.directory / "compose.yaml").is_file(), "Release compose.yaml is missing")
        require((self.directory / ".env").is_file(), "Release .env is missing")
        self.compose = ["docker", "compose", "--project-directory", str(self.directory),
                        "--env-file", str(self.directory / ".env"), "-f", str(self.directory / "compose.yaml")]
        self.config = parse_compose_config(command(self.compose + ["config", "--format", "json"],
                                                    "Compose configuration is invalid"))
        self.report = {"ok": False, "live": args.live, "checks": [], "submissions": []}
        self.token = None
        self.fixture_id = None
        self.fixture_route = None
        self.fixture_cases = None
        self.completed_results = []
        self.identifier = uuid.uuid4().hex

    def check(self, label, condition=True):
        require(condition, label)
        self.report["checks"].append(label)
        print("PASS: " + label, flush=True)

    def containers(self):
        raw = command(self.compose + ["ps", "--all", "--format", "json"], "Cannot read isolated container state")
        if raw.lstrip().startswith("["):
            values = json.loads(raw)
        else:
            values = [json.loads(line) for line in raw.splitlines() if line.strip()]
        return {item["Service"]: item for item in values}

    def wait_healthy(self, deadline=None):
        if deadline is None:
            deadline = time.monotonic() + self.args.timeout
        while time.monotonic() < deadline:
            values = self.containers()
            if set(values) == SERVICES:
                preflight = values["preflight"]
                require(preflight.get("State") in ("running", "exited"), "Preflight container has an unexpected state")
                if preflight.get("State") == "exited":
                    require(preflight.get("ExitCode") == 0, "Preflight rejected deployment configuration")
                    if all(values[name].get("State") == "running" and values[name].get("Health") == "healthy"
                           for name in SERVICES - {"preflight"}):
                        return values
            time.sleep(2)
        raise AssertionError("Isolated stack did not become healthy before the deadline")

    def volume_snapshot(self):
        volumes = self.config.get("volumes", {})
        names = {value["name"]: key for key, value in volumes.items()}
        require(names and len(names) == len(volumes), "Isolated volumes must have unique declared names")
        raw = command(["docker", "volume", "inspect"] + sorted(names), "Cannot inspect isolated data volumes")
        values = json.loads(raw)
        require(isinstance(values, list) and len(values) == len(names), "An isolated data volume is missing")
        snapshot = {}
        for value in values:
            name = value.get("Name")
            labels = value.get("Labels") or {}
            require(name in names and name not in snapshot
                    and labels.get("com.docker.compose.project") == self.config["name"]
                    and labels.get("com.docker.compose.volume") == names[name],
                    "Data volume belongs to another Compose project or declaration")
            require(value.get("CreatedAt") and value.get("Mountpoint"), "Data volume identity is incomplete")
            snapshot[name] = {key: value.get(key) for key in
                              ("Name", "CreatedAt", "Mountpoint", "Driver", "Options", "Labels")}
        return snapshot

    def recreate_stack(self):
        """Cold-start only this verified test project, retaining the exact named volumes."""
        require(self.args.live, "Container recreation requires an isolated live stack")
        check_contract(self.config, self.directory, live=True)
        current = parse_compose_config(command(self.compose + ["config", "--format", "json"],
                                               "Cannot revalidate the isolated Compose configuration"))
        require(current == self.config, "Compose configuration changed during live verification")
        before = self.wait_healthy()
        self.verify_container_ownership(before)
        volumes = self.volume_snapshot()
        command(self.compose + ["down", "--timeout", str(min(45, self.args.timeout))],
                "Cannot stop and remove the isolated containers", timeout=min(180, self.args.timeout))
        require(not self.containers(), "Cold recreation did not remove all isolated containers")
        require(self.volume_snapshot() == volumes, "Cold recreation removed or replaced an isolated data volume")
        deadline = time.monotonic() + self.args.timeout
        command(self.compose + ["up", "-d", "--no-build", "--wait", "--wait-timeout", str(self.args.timeout)],
                "Cannot recreate the isolated stack before the health deadline", timeout=self.args.timeout)
        after = self.wait_healthy(deadline=deadline)
        self.verify_container_ownership(after)
        require(all(before[name]["ID"] != after[name]["ID"] for name in SERVICES),
                "Cold recreation must replace every container, including preflight")
        require(self.volume_snapshot() == volumes, "Cold recreation did not retain the original data volumes")
        self.check("Cold recreation replaces all eight containers and retains every original named volume")

    def verify_container_ownership(self, states):
        identifiers = [states[name]["ID"] for name in sorted(SERVICES)]
        require(all(re.fullmatch(r"[a-f0-9]{12,64}", value) for value in identifiers),
                "Unexpected container identifier")
        raw = command(["docker", "inspect"] + identifiers, "Cannot verify isolated container ownership")
        containers = json.loads(raw)
        images = {}
        for container in containers:
            labels = container["Config"].get("Labels", {})
            require(labels.get("com.docker.compose.project") == self.config["name"],
                    "Container belongs to another Compose project")
            working_dir = labels.get("com.docker.compose.project.working_dir", "")
            require(bool(working_dir) and pathlib.Path(working_dir).resolve() == self.directory,
                    "Container belongs to a different release directory")
            name = labels.get("com.docker.compose.service")
            require(name in SERVICES, "Container has an unexpected service label")
            model = self.config["services"][name]
            reference = model["image"]
            if reference not in images:
                images[reference] = self.verified_image_id(reference, name)
            require(container.get("Image") == images[reference],
                    "Running container image content differs from the validated release: " + name)
            actual_env = dict(value.split("=", 1) for value in container["Config"].get("Env", []) if "=" in value)
            require(all(actual_env.get(key) == str(value) for key, value in model.get("environment", {}).items()),
                    "Running container environment differs from the validated release")
            host = container["HostConfig"]
            require(host.get("NetworkMode") != "host", "Running service must not use host networking")
            require(not host.get("Privileged") or name == "go-judge", "Running application is unexpectedly privileged")
            bindings = host.get("PortBindings") or {}
            if name != "frontend":
                require(not bindings, "Internal running service unexpectedly publishes host ports")
            else:
                published = [entry for entries in bindings.values() for entry in entries or []]
                require(len(published) == 1 and published[0].get("HostIp") == "127.0.0.1"
                        and published[0].get("HostPort") == str(self.port),
                        "Running frontend port differs from the isolated test endpoint")
            for mount in container.get("Mounts", []):
                if mount.get("Type") == "volume":
                    require(mount.get("Name", "").startswith(self.config["name"] + "_"),
                            "Running container references a volume from another project")
                elif mount.get("Type") == "bind":
                    # Compose sources were confined above. Docker Desktop rewrites host
                    # paths, so inspect runtime destinations and read-only flags here.
                    declared = {item["target"] for item in model.get("volumes", []) if item.get("type") == "bind"}
                    require(mount.get("Destination") in declared and not mount.get("RW"),
                            "Running container has an undeclared or writable host mount")
        self.check("Running containers, credentials, ports and data volumes belong to this isolated release")

    def verified_image_id(self, reference, service):
        raw = command(["docker", "image", "inspect", reference], "Cannot inspect the release image")
        values = json.loads(raw)
        require(isinstance(values, list) and len(values) == 1 and isinstance(values[0], dict),
                "Release image inspection returned an unexpected result")
        image = values[0]
        identifier = image.get("Id", "")
        require(isinstance(identifier, str) and IMAGE_ID.fullmatch(identifier)
                and image.get("Os") == "linux" and image.get("Architecture") == "amd64"
                and image.get("Variant", "") in ("", "v1"),
                "Release image must contain the expected linux/amd64 content")
        if IMAGE_ID.fullmatch(reference):
            require(identifier == reference, "Local release image ID differs from the expected content")
        else:
            expected = check_image_tag(reference, service)
            references = image.get("RepoTags") or []
            require(any(isinstance(value, str) and REPOSITORY_TAG.fullmatch(value)
                        and canonical_repository_tag(value) == expected for value in references),
                    "Release image does not contain the expected repository version tag")
        if service in PROJECT_SERVICES or service == "preflight":
            labels = (image.get("Config") or {}).get("Labels") or {}
            require(labels.get("org.opencontainers.image.version") == project_version(ROOT),
                    "Project image OCI version differs from the parent POM")
        return identifier

    def verify_internal_authentication(self):
        script = '''import http.client, json
results = []
for host, port, route, header in [
    ("web", 9091, "/internal/judge-worker/problems/1/versions/1/manifest", "X-Judge-Worker-Token"),
    ("go-judge", 5050, "/file", "Authorization")]:
    for headers in ({}, {header: "Bearer invalid-deployment-smoke-token"}):
        connection = http.client.HTTPConnection(host, port, timeout=10)
        try:
            connection.request("GET", route, headers=headers)
            response = connection.getresponse()
            results.append(response.status)
        finally:
            connection.close()
print(json.dumps(results))
'''
        output = command(self.compose + ["exec", "-T", "worker", "python3", "-c", script],
                         "Cannot check internal service authentication")
        statuses = json.loads(output)
        self.check("Web and sandbox reject missing and invalid internal service tokens",
                   len(statuses) == 4 and all(status in (401, 403) for status in statuses))

    def request(self, method, path, payload=None, content_type=None):
        headers = {"Accept": "application/json", "User-Agent": "YMCLOUD-Deployment-Smoke"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if payload is not None:
            if content_type:
                headers["Content-Type"] = content_type
            else:
                payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                headers["Content-Type"] = "application/json"
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=20)
        try:
            connection.request(method, path, body=payload, headers=headers)
            response = connection.getresponse()
            body = response.read(4 * 1024 * 1024 + 1)
            require(len(body) <= 4 * 1024 * 1024, "Unexpected oversized HTTP response")
            try:
                value = json.loads(body)
            except (ValueError, UnicodeDecodeError):
                value = None
            return response.status, value, body
        finally:
            connection.close()

    def verify_logs_and_cleanup(self, previous=None):
        snapshots = {}
        script = '''import gzip, hashlib, json, os, pathlib, re, sys, time, zlib
root = pathlib.Path("/data/logs")
application = root / "application.log"
error = root / "error.log"
previous = json.loads(sys.stdin.read())

def archive_state():
    # Logback renames the active log before its asynchronous gzip task finishes.
    assert not list(root.glob("application*.tmp"))
    assert not list((root / "archive").glob("application*.tmp"))
    archives = []
    for path in (root / "archive").glob("application.*.log.gz"):
        match = re.fullmatch(r"application\\.(\\d{4}-\\d{2}-\\d{2})\\.(\\d+)\\.log\\.gz", path.name)
        assert match
        details = path.stat()
        fingerprint = (details.st_dev, details.st_ino, details.st_size, details.st_mtime_ns)
        archives.append((match[1], int(match[2]), path, fingerprint))
    return sorted(archives)

def read_active(path):
    before = path.stat()
    content = path.read_bytes()
    after = path.stat()
    assert (before.st_dev, before.st_ino) == (after.st_dev, after.st_ino)
    assert before.st_size <= len(content) <= after.st_size
    assert after.st_uid == 10001
    return content

deadline = time.monotonic() + 3
while True:
    try:
        archives = archive_state()
        chunks = []
        for _, _, path, _ in archives:
            with gzip.open(path, "rb") as stream:
                archived = stream.read()
                assert archived
                chunks.append(archived)
        chunks.append(read_active(application))
        errors = read_active(error).decode("utf-8")
        assert archive_state() == archives
        content = b"".join(chunks)
        text = content.decode("utf-8")
        assert content and "\\x1b" not in text + errors
        assert "INFO" in text
        for name in ("YMCLOUD_WORKER_SERVICE_TOKEN", "YMCLOUD_GO_JUDGE_TOKEN", "YMCLOUD_TOKEN_SECRET"):
            secret = os.environ.get(name)
            assert not secret or secret not in text + errors
        if previous:
            assert len(content) >= previous["bytes"]
            assert hashlib.sha256(content[:previous["bytes"]]).hexdigest() == previous["sha256"]
        break
    except (AssertionError, OSError, EOFError, UnicodeError, zlib.error):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise
        time.sleep(min(0.1, remaining))
print(json.dumps({"bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}))
'''
        for service in ("web", "worker"):
            output = command(self.compose + ["exec", "-T", service, "python3", "-c", script],
                             "Application log encoding, ownership, history or output validation failed: " + service,
                             json.dumps(previous.get(service) if previous else None))
            snapshots[service] = json.loads(output)
        self.check("UTF-8 application and error logs have the expected owner and do not contain service tokens")
        if previous:
            self.check("Application log contents survive the service lifecycle check")
        script = '''import hashlib, http.client, json, os, pathlib, re, stat, sys, time
connection = http.client.HTTPConnection("go-judge", 5050, timeout=10)
try:
    connection.request("GET", "/file", headers={"Authorization": "Bearer " + os.environ["YMCLOUD_GO_JUDGE_TOKEN"]})
    response = connection.getresponse()
    assert response.status == 200 and json.loads(response.read()) == {}
finally:
    connection.close()
records = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
           for path in pathlib.Path("/data/results").glob("*.json")}
assert records
previous = json.loads(sys.stdin.read())
assert not previous or all(records.get(name) == digest for name, digest in previous.items())
assert not list(pathlib.Path("/data/results").glob(".result-*.tmp"))
for path in pathlib.Path("/data/work").iterdir():
    temporary = re.fullmatch(r"\\.health-[0-9]{1,20}\\.tmp", path.name)
    assert path.name == "health" or temporary
    try:
        details = path.lstat()
    except FileNotFoundError:
        # A matching temporary probe can disappear after its atomic rename.
        assert temporary
        continue
    assert stat.S_ISREG(details.st_mode)
    if temporary:
        assert 0 <= time.time() - details.st_mtime <= 60
print(json.dumps(records))
'''
        output = command(self.compose + ["exec", "-T", "worker", "python3", "-c", script],
                         "Completed tasks left sandbox files or lost recovery records",
                         json.dumps(previous.get("records") if previous else None))
        snapshots["records"] = json.loads(output)
        self.check("Completed submissions leave no sandbox cache or result temporary files, while recovery records remain")
        return snapshots

    def api(self, method, path, payload=None, content_type=None):
        status, value, _ = self.request(method, "/api" + path, payload, content_type)
        require(status == 200 and isinstance(value, dict) and value.get("code") == 200,
                "API operation failed: " + method + " " + path.split("?")[0])
        return value.get("data")

    def verify_bootstrap(self):
        # Read-only, fixed SQL through the isolated service; credentials are never command arguments.
        sql = ("SELECT COUNT(*) FROM user WHERE username <> 'admin'; "
               "SELECT COUNT(*) FROM problem WHERE title NOT LIKE 'Deployment smoke %'; "
               "SELECT COUNT(*) FROM language WHERE name IN ('C','C++','Java','Python');")
        output = command(self.compose + ["exec", "-T", "mysql", "sh", "-ec",
                         'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -u ymcloud ymcloud'],
                         "Cannot inspect isolated bootstrap data", sql)
        require(output.split() == ["0", "0", "4"], "Live smoke requires fresh seed data or previous deployment smoke fixtures only")
        self.check("Fresh bootstrap contains only the seed administrator and four language entries")

    def login(self):
        captcha_id = "deployment-smoke-" + self.identifier
        redis_key = "captcha_codes:" + captcha_id
        output = command(self.compose + ["exec", "-T", "redis", "sh", "-ec",
                         'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli -x SETEX "$1" 60', "sh", redis_key],
                         "Cannot create a short-lived isolated login challenge", json.dumps("2468"))
        require(output.strip() == "OK", "Isolated login challenge was not stored")
        password = os.environ.get("YMCLOUD_DEPLOYMENT_TEST_ADMIN_PASSWORD", "Ymcloud@123")
        self.token = self.api("POST", "/login", {"username": "admin", "password": password,
                                                  "uuid": captcha_id, "code": "2468"})
        require(isinstance(self.token, str) and len(self.token.split(".")) == 3, "Seed administrator login did not return a JWT")
        self.check("Seed administrator can log in through the frontend reverse proxy")

    def create_fixture(self):
        payload = {"title": "Deployment smoke " + self.identifier[:12], "content": "Calculate a + b.",
                   "input": "Two integers.", "output": "Their sum.", "source": "myy", "difficulty": 1,
                   "mode": 0, "visibility": 1, "openCaseResult": True, "timeLimit": 2000,
                   "memoryLimit": 256, "problemCaseList": [], "tagList": []}
        self.fixture_id = self.api("POST", "/admin/problem", payload)
        require(isinstance(self.fixture_id, str) and re.fullmatch(r"YMOJ-[0-9]+", self.fixture_id),
                "Created problem has an unexpected display identifier")
        page = self.api("GET", "/admin/problem/list?keyword=" + urllib.parse.quote(self.fixture_id))
        records = [item for item in page["records"] if item["problemId"] == self.fixture_id]
        require(len(records) == 1, "Created problem cannot be found by the management API")
        problem_id = int(records[0]["id"])
        boundary = "ymcloud-smoke-" + self.identifier
        parts = []
        for filename, data in TEST_CASE_FILES:
            parts.append(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                          + filename + "\"\r\nContent-Type: text/plain\r\n\r\n").encode() + data + b"\r\n")
        body = b"".join(parts) + ("--" + boundary + "--\r\n").encode()
        route = "/admin/problem/" + str(problem_id) + "/testcases"
        self.api("POST", route + "/upload", body, "multipart/form-data; boundary=" + boundary)
        cases = self.api("GET", route)
        self.check("Management API creates a problem and publishes two paired test cases", len(cases) == 2)
        self.fixture_route = route
        self.fixture_cases = cases

    def verify_uploaded_fixture(self):
        cases = self.api("GET", self.fixture_route)
        require(cases == self.fixture_cases, "Uploaded test-case metadata changed during the lifecycle check")
        require(isinstance(cases, list) and len(cases) == 2 and all(isinstance(case, dict) for case in cases),
                "Uploaded test-case metadata must contain exactly two paired cases")
        paths = [case.get(field) for case in cases for field in ("input", "output")]
        require(all(isinstance(path, str) and path.startswith("/") and ".." not in path.split("/")
                    for path in paths), "Uploaded test-case files must have absolute logical paths")
        expected_files = dict(TEST_CASE_FILES)
        filenames = [pathlib.PurePosixPath(path).name for path in paths]
        require(len(set(paths)) == len(expected_files) and set(filenames) == set(expected_files),
                "Uploaded test-case logical paths must identify all four original files exactly once")
        for logical_path, filename in zip(paths, filenames):
            route = ("/api" + self.fixture_route + "/download?fileName="
                     + urllib.parse.quote(logical_path, safe=""))
            status, _, body = self.request("GET", route)
            require(status == 200 and body == expected_files[filename],
                    "Uploaded test-case file was lost or changed: " + filename)
        self.check("Original authenticated session can read all four unchanged uploaded test-case files")

    def wait_verdict(self, identifier, expected):
        deadline = time.monotonic() + self.args.timeout
        while time.monotonic() < deadline:
            result = self.api("GET", "/judge/status?submissionId=" + str(identifier))
            verdict = result.get("status")
            require(verdict in ACTIVE | TERMINAL, "Status polling returned an unknown verdict")
            if verdict in TERMINAL:
                require(verdict == expected, "Submission did not reach the expected " + expected + " verdict")
                return result
            time.sleep(0.5)
        raise AssertionError("Submission did not reach a terminal verdict before the deadline")

    def submit(self, language, code, expected="Accepted", self_test=False):
        payload = {"language": language, "code": code}
        if self_test:
            path = "/judge/test"
            payload.update({"input": "2 3\n", "expectedOutput": "5\n"})
        else:
            path = "/judge/submit"
            payload.update({"isContest": False, "problemId": self.fixture_id})
        started = time.monotonic()
        identifier = self.api("POST", path, payload)
        require(str(identifier).isdigit(), "Submission API did not return a numeric identifier")
        result = self.wait_verdict(identifier, expected)
        require(all(str(previous["identifier"]) != str(identifier) for previous in self.completed_results),
                "Submission ID was reused after a completed submission")
        self.completed_results.append({"identifier": identifier, "result": result.copy(), "selfTest": self_test})
        self.report["submissions"].append({"language": language, "selfTest": self_test,
                                           "verdict": expected, "elapsedMs": round((time.monotonic() - started) * 1000),
                                           "runTimeMs": result.get("time"), "memoryKiB": result.get("memory")})
        self.check(language + (" self-test" if self_test else " submission") + " reaches " + expected)
        return identifier

    def verify_lifecycle_persistence(self, logs):
        """Keep the last allocated ID formal, then verify old data before any new submission."""
        require(self.completed_results and not self.completed_results[-1]["selfTest"],
                "Lifecycle checks must end with a completed formal submission before shutdown")
        previous = list(self.completed_results)
        token = self.token
        if self.args.recreate:
            self.recreate_stack()
            action = "cold recreation"
        else:
            command(self.compose + ["restart", "--no-deps", "mysql", "redis", "rabbitmq",
                                    "web", "worker", "go-judge", "frontend"],
                    "Cannot restart the isolated stack", timeout=180)
            states = self.wait_healthy()
            self.verify_container_ownership(states)
            action = "full service restart"
        require(self.token == token, "Lifecycle verification must retain the original login session")
        profile = self.api("GET", "/user/profile")
        require(isinstance(profile, dict) and str(profile.get("id")) == "1" and profile.get("username") == "admin",
                "Original administrator session did not survive the lifecycle check")
        for record in previous:
            result = self.api("GET", "/judge/status?submissionId=" + str(record["identifier"]))
            require(result == record["result"], "A completed verdict or its output/resources changed after " + action)
        self.check("All completed verdicts, self-test output and the original login session survive " + action)
        self.verify_uploaded_fixture()
        self.submit("Python", SOURCES["Python"] + "# after " + action + "\n")
        self.check("A new formal submission uses the retained test data after " + action)
        self.verify_logs_and_cleanup(logs)

    def run(self):
        check_contract(self.config, self.directory, self.args.live)
        self.check("Compose deployment contract validates without displaying resolved credentials")
        if not self.args.live:
            self.report["ok"] = True
            return
        self.port = int(self.config["services"]["frontend"]["ports"][0]["published"])
        states = self.wait_healthy()
        self.verify_container_ownership(states)
        self.check("Preflight exits successfully and all seven services are healthy")
        status, _, body = self.request("GET", "/")
        self.check("Frontend serves the built application", status == 200 and b'id="app"' in body)
        status, value, _ = self.request("GET", "/api/captchaImage")
        self.check("Captcha endpoint is available through the same-origin API proxy",
                   status == 200 and isinstance(value, dict) and value.get("code") == 200)
        for route in ("/api/internal/judge-worker/problems/1/versions/1/manifest",
                      "/internal/judge-worker/problems/1/versions/1/manifest",
                      "/api/internal", "/internal"):
            status, _, _ = self.request("GET", route)
            self.check("Frontend blocks internal route " + route, status in (403, 404))
        self.verify_internal_authentication()
        self.verify_bootstrap()
        self.login()
        self.create_fixture()
        for language, source in SOURCES.items():
            self.submit(language, source)
        self.submit("Python", "print('WRONG')\n", "Wrong Answer")
        self.submit("Python", SOURCES["Python"], self_test=True)
        if self.args.restart or self.args.recreate:
            # A final self-test can hide a stale SQL allocation cursor because it
            # does not insert into judge. Make its maximum ID a formal submission.
            self.submit("Python", SOURCES["Python"] + "# before lifecycle check\n")
        logs = self.verify_logs_and_cleanup()
        if self.args.restart or self.args.recreate:
            self.verify_lifecycle_persistence(logs)
        self.report["ok"] = True


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-dir", required=True, help="An extracted release directory containing .env")
    parser.add_argument("--live", action="store_true", help="Opt in to fixtures and submissions in an isolated stack")
    lifecycle = parser.add_mutually_exclusive_group()
    lifecycle.add_argument("--restart", action="store_true", help="Also restart the isolated live stack and verify persistence")
    lifecycle.add_argument("--recreate", action="store_true",
                           help="Recreate isolated containers with down/up while retaining all named volumes")
    parser.add_argument("--timeout", type=int, default=300, help="Per-healthcheck/submission deadline in seconds")
    args = parser.parse_args(argv)
    require(not (args.restart or args.recreate) or args.live, "--restart and --recreate require --live")
    require(10 <= args.timeout <= 900, "Timeout must be between 10 and 900 seconds")
    report_dir = confined_path(TEST_ROOT)
    report_dir.mkdir(parents=True, exist_ok=True)
    verification = None
    try:
        verification = Verification(args)
        verification.run()
        report = verification.report
    except Exception as error:
        report = verification.report if verification else {"ok": False, "checks": []}
        report["error"] = str(error) if isinstance(error, AssertionError) else type(error).__name__
        print("FAIL: " + report["error"], file=sys.stderr)
    output = report_dir / ("live-report.json" if args.live else "static-report.json")
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Report: " + str(output))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())

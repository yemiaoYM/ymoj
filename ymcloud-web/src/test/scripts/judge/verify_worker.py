#!/usr/bin/env python3
"""Opt-in, isolated Web -> RabbitMQ -> Worker -> go-judge -> Web regression.

Requires YMCLOUD_WORKER_E2E=true and an explicitly selected unused Redis DB in
YMCLOUD_WORKER_E2E_REDIS_DATABASE. Uses only the existing local dev MySQL service.
Requires RabbitMQ management administrator access to create an isolated vhost.
No business schema, real files, existing queue, or existing process is modified.
Credentials and submitted payloads are never printed or included in the report.
"""

import argparse
import base64
import copy
import datetime
import hashlib
import http.client
import json
import os
import pathlib
import re
import secrets
import shutil
import socket
import ssl
import subprocess
import sys
import time
import uuid
import urllib.parse


PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[5]
SCHEMA_PATTERN = re.compile(r"ymcloud_worker_e2e_[0-9a-f]{32}")
REDIS_CLAIM = "ymcloud:phase3-verification/business:exclusive-db"
INITIAL_PASSWORD = "Ymcloud@123"
INPUTS = [b" \tleading\r\ntrailing  \n\n", b"\n\t  second\n  "]
VERSION = 101
NEW_VERSION = 102
PROBLEM = 10


def require(condition, label):
    if not condition:
        raise AssertionError(label)


def confined_test_path(path, target):
    project = PROJECT_ROOT.resolve()
    absolute_target = pathlib.Path(os.path.abspath(target))
    absolute_path = pathlib.Path(os.path.abspath(path))
    require(absolute_target.is_relative_to(project), "Test root is outside this project")
    require(absolute_path.is_relative_to(absolute_target), "Test path is outside its dedicated root")
    for candidate in [absolute_target, absolute_path]:
        current = project
        for part in candidate.relative_to(project).parts:
            current = current / part
            require(not current.is_symlink() and current.resolve() == current,
                    "Test path contains a symlink or directory junction")
    return absolute_path


def private_directory(path):
    path.mkdir(parents=True, exist_ok=True)
    if os.name != "nt":
        path.chmod(0o700)


def private_write(path, content):
    with path.open("w", encoding="utf-8", newline="\n") as output:
        output.write(content)
    if os.name != "nt":
        path.chmod(0o600)


def environment_value(value):
    if not isinstance(value, str):
        return value
    def replace(match):
        name, fallback = match.groups()
        resolved = os.environ.get(name, fallback)
        require(resolved is not None, "Required dev environment value is unavailable")
        return resolved
    resolved = re.sub(r"\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}", replace, value)
    require("${" not in resolved, "Unsupported dev environment placeholder")
    return resolved


def resolved_tree(value):
    if isinstance(value, dict):
        return {key: resolved_tree(item) for key, item in value.items()}
    if isinstance(value, list):
        return [resolved_tree(item) for item in value]
    return environment_value(value)


def configuration_string(value, default=None):
    """Bind a YAML scalar to a connection's String field without exposing its value."""
    if value is None:
        return default
    require(type(value) in [str, int, float, bool], "Connection string field must be a YAML scalar")
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def sql_structure(sql):
    """Remove literals/comments before applying the fresh-schema SQL scope guard."""
    result = []
    position = 0
    while position < len(sql):
        character = sql[position]
        if character in ["'", '"']:
            quote = character
            result.append(" ")
            position += 1
            while position < len(sql):
                if sql[position] == "\\":
                    position += 2
                elif sql[position] == quote:
                    position += 1
                    if position < len(sql) and sql[position] == quote:
                        position += 1
                    else:
                        break
                else:
                    position += 1
        elif sql.startswith("/*", position):
            require(not sql.startswith("/*!", position), "Executable SQL comments are not allowed in bootstrap")
            end = sql.find("*/", position + 2)
            require(end != -1, "Unterminated SQL comment")
            result.append(" ")
            position = end + 2
        elif character == "#" or (sql.startswith("--", position)
                                  and (position + 2 == len(sql) or sql[position + 2].isspace())):
            end = sql.find("\n", position)
            result.append("\n")
            position = len(sql) if end == -1 else end + 1
        else:
            result.append(character)
            position += 1
    return "".join(result)


def hidden_process_options():
    return {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}


class Regression:
    def __init__(self, args, yaml_module, mysql_module, redis_module, pika_module):
        self.args = args
        self.yaml = yaml_module
        self.mysql = mysql_module
        self.redis_module = redis_module
        self.pika = pika_module
        self.identifier = uuid.uuid4().hex
        self.schema = "ymcloud_worker_e2e_" + self.identifier
        self.namespace = "phase3e2e." + self.identifier
        self.worker_prefix = "phase3e2e:" + self.identifier + ":"
        self.folder = PROJECT_ROOT / "target" / "phase3-verification/business" / self.identifier
        self.runtime = self.folder / "runtime"
        confined_test_path(self.runtime, PROJECT_ROOT / "target" / "phase3-verification/business")
        self.web_files = self.runtime / "web-files"
        self.worker_files = self.runtime / "worker"
        self.cache = self.worker_files / "cache"
        self.work = self.worker_files / "work"
        self.journal = self.worker_files / "results"
        self.report = {"ok": False, "checks": [], "timingsMs": {}, "cleanup": []}
        self.report["javaBudget"] = {"activeProcessors": 2, "workerMaxHeapMiB": 128, "webMaxHeapMiB": 256}
        self.stage = "prepare"
        self.service_token = secrets.token_hex(32)
        self.processes = []
        self.log_files = []
        self.created_schema = False
        self.created_vhost = False
        self.rabbit_vhost = "ymcloud-worker-e2e-" + self.identifier
        self.redis_claimed = False
        self.mysql_admin = None
        self.database = None
        self.redis = None
        self.mq = None
        self.channel = None
        self.port = None
        self.redis_keys = set()
        self.tokens = []
        self.submissions = []

    def check(self, label, condition=True):
        require(condition, label)
        self.report["checks"].append(label)

    def prepare(self):
        require(SCHEMA_PATTERN.fullmatch(self.schema), "Invalid generated test schema")
        target = confined_test_path(PROJECT_ROOT / "target" / "phase3-verification/business", PROJECT_ROOT / "target" / "phase3-verification/business")
        require(confined_test_path(self.folder, target).parent == target, "Test directory is outside ignored target")
        private_directory(self.runtime)
        private_directory(self.web_files / "private")
        private_directory(self.worker_files)
        with (PROJECT_ROOT / "ymcloud-web/src/main/resources/application-dev.yml").open(encoding="utf-8") as source:
            configured = self.yaml.safe_load(source)["spring"]
        dev = {"spring": {"datasource": resolved_tree(configured["datasource"]),
                          "data": {"redis": resolved_tree(configured["data"]["redis"])},
                          "rabbitmq": resolved_tree(configured["rabbitmq"])}}
        with (PROJECT_ROOT / "ymcloud-web/src/main/resources/application.yml").open(encoding="utf-8") as source:
            base_web = self.yaml.safe_load(source)
        with (PROJECT_ROOT / "ymcloud-judge/src/main/resources/application.yml").open(encoding="utf-8") as source:
            base_worker = self.yaml.safe_load(source)
        datasource = dev["spring"]["datasource"]
        url = configuration_string(datasource.get("url"), "")
        match = re.fullmatch(r"jdbc:mysql://(localhost|127\.0\.0\.1):([0-9]+)/[^?]+(?:\?.*)?", url)
        require(match is not None, "E2E only permits a localhost dev MySQL datasource")
        self.mysql_options = {"host": match.group(1), "port": int(match.group(2)),
                              "user": configuration_string(datasource.get("username"), ""),
                              "password": configuration_string(datasource.get("password"), ""),
                              "charset": "utf8mb4", "autocommit": True, "connect_timeout": 10,
                              "read_timeout": 15, "write_timeout": 15,
                              "cursorclass": self.mysql.cursors.DictCursor}
        self.mysql_admin = self.mysql.connect(**self.mysql_options)
        self.server_uuid = self.scalar("SELECT @@server_uuid", admin=True)
        self.execute("CREATE DATABASE `" + self.schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci",
                     admin=True)
        self.created_schema = True
        self.database = self.mysql.connect(database=self.schema,
                                           client_flag=self.mysql.constants.CLIENT.MULTI_STATEMENTS,
                                           **self.mysql_options)
        self.guard_database()
        sql = (PROJECT_ROOT / "sql/ymcloud.sql").read_text(encoding="utf-8-sig")
        structure = sql_structure(sql)
        require(not re.search(r"(?i)\b(?:USE\s|(?:CREATE|DROP|ALTER)\s+DATABASE\b|GRANT\s|REVOKE\s|LOAD\s+DATA\b|INTO\s+(?:OUTFILE|DUMPFILE)\b)", structure),
                "Bootstrap SQL must not select or mutate another database")
        require(not re.search(r"(?i)\b(?:FROM|JOIN|INTO|UPDATE|TABLE|REFERENCES)\s+`?[A-Za-z0-9_]+`?\s*\.", structure),
                "Bootstrap SQL must not access qualified external tables")
        with self.database.cursor() as cursor:
            cursor.execute(sql)
            while cursor.nextset():
                pass
        self.guard_database()
        self.check("Fresh current SQL bootstrap")
        self.redis_options = copy.deepcopy(dev["spring"]["data"]["redis"])
        selected = os.environ.get("YMCLOUD_WORKER_E2E_REDIS_DATABASE", "")
        require(selected.isdigit() and 1 <= int(selected) <= 15, "Select an unused Redis database 1..15 explicitly")
        require(int(selected) != int(self.redis_options.get("database", 0)), "E2E Redis DB must differ from dev DB")
        self.redis_options["database"] = int(selected)
        options = self.redis_client_options(self.redis_options)
        self.redis = self.redis_module.Redis(**options)
        require(self.redis.dbsize() == 0, "Selected E2E Redis database is not empty")
        require(self.redis.set(REDIS_CLAIM, self.identifier, nx=True, ex=3600), "E2E Redis DB is already claimed")
        self.redis_claimed = True
        self.redis_keys.add(REDIS_CLAIM)
        self.report["scope"] = {"mysqlSchema": self.schema, "rabbitNamespace": self.namespace,
                                "redisDatabase": int(selected), "workerRedisPrefix": self.worker_prefix}
        require(self.redis.dbsize() == 1, "Concurrent activity detected in the selected Redis DB")
        self.rabbit_options = copy.deepcopy(dev["spring"]["rabbitmq"])
        self.rabbit_management("PUT", "vhosts/" + self.rabbit_vhost, {})
        self.created_vhost = True
        username = urllib.parse.quote(configuration_string(self.rabbit_options["username"]), safe="")
        self.rabbit_management("PUT", "permissions/" + self.rabbit_vhost + "/" + username,
                               {"configure": ".*", "write": ".*", "read": ".*"})
        self.rabbit_options["virtual-host"] = self.rabbit_vhost
        self.report["scope"]["rabbitVirtualHost"] = self.rabbit_vhost
        self.mq = self.pika.BlockingConnection(self.mq_parameters())
        self.channel = self.mq.channel()
        self.channel.confirm_delivery()
        self.go_judge_url = os.environ.get("YMCLOUD_GO_JUDGE_URL", "http://127.0.0.1:5050")
        self.go_judge_token = os.environ.get("YMCLOUD_GO_JUDGE_TOKEN", "")
        endpoint = urllib.parse.urlsplit(self.go_judge_url)
        require(endpoint.scheme == "http" and endpoint.hostname in ["127.0.0.1", "localhost"]
                and not endpoint.username and not endpoint.password and not endpoint.query
                and not endpoint.fragment and endpoint.path in ["", "/"],
                "E2E requires a dedicated local go-judge service")
        require(len(self.go_judge_token) >= 32, "A dedicated go-judge token is required")
        self.fixture()
        self.write_configs(base_web, base_worker)

    def redis_client_options(self, options):
        result = {"host": configuration_string(options.get("host", "127.0.0.1"), ""),
                  "port": int(options.get("port", 6379)),
                  "password": configuration_string(options.get("password")) or None,
                  "username": configuration_string(options.get("username")) or None,
                  "db": options["database"], "socket_connect_timeout": 5, "socket_timeout": 5}
        ssl_setting = options.get("ssl", {})
        if isinstance(ssl_setting, dict) and ssl_setting.get("enabled"):
            result["ssl"] = True
        return result

    def mq_parameters(self):
        options = self.rabbit_options
        kwargs = {"host": configuration_string(options.get("host", "127.0.0.1"), ""),
                  "port": int(options.get("port", 5672)),
                  "virtual_host": configuration_string(options.get("virtual-host", "/"), ""),
                  "heartbeat": 60,
                  "socket_timeout": 10, "blocked_connection_timeout": 10,
                  "credentials": self.pika.PlainCredentials(
                      configuration_string(options.get("username", "guest"), ""),
                      configuration_string(options.get("password", "guest"), ""))}
        ssl_setting = options.get("ssl", {})
        if isinstance(ssl_setting, dict) and ssl_setting.get("enabled"):
            kwargs["ssl_options"] = self.pika.SSLOptions(ssl.create_default_context(), kwargs["host"])
        return self.pika.ConnectionParameters(**kwargs)

    def rabbit_management(self, method, route, payload=None):
        """
        Manage only this fixture's virtual host
        Missing permissions fail the test
        """
        endpoint = urllib.parse.urlsplit(os.environ.get("YMCLOUD_WORKER_E2E_RABBIT_MANAGEMENT_URL",
                                                        "http://" + self.rabbit_options["host"] + ":15672"))
        require(endpoint.scheme in ["http", "https"] and endpoint.hostname
                and not endpoint.username and not endpoint.password and not endpoint.query
                and not endpoint.fragment and endpoint.path in ["", "/"], "Invalid RabbitMQ management endpoint")
        connection_type = http.client.HTTPSConnection if endpoint.scheme == "https" else http.client.HTTPConnection
        connection = connection_type(endpoint.hostname, endpoint.port, timeout=15)
        credentials = (configuration_string(self.rabbit_options["username"], "") + ":"
                       + configuration_string(self.rabbit_options["password"], ""))
        headers = {"Authorization": "Basic " + base64.b64encode(credentials.encode()).decode(),
                   "Content-Type": "application/json"}
        try:
            connection.request(method, "/api/" + route,
                               body=None if payload is None else json.dumps(payload), headers=headers)
            response = connection.getresponse()
            response.read()
            require(200 <= response.status < 300,
                    "Isolated RabbitMQ vhost management failed; administrator access is required")
        finally:
            connection.close()

    def guard_database(self):
        require(SCHEMA_PATTERN.fullmatch(self.schema), "Unsafe test schema name")
        require(self.scalar("SELECT DATABASE()") == self.schema, "Database scope changed")
        require(self.scalar("SELECT @@server_uuid") == self.server_uuid, "Local MySQL service identity changed")

    def execute(self, sql, values=None, admin=False):
        connection = self.mysql_admin if admin else self.database
        if not admin:
            self.guard_database()
        with connection.cursor() as cursor:
            cursor.execute(sql, values)
            return cursor.rowcount

    def row(self, sql, values=None):
        with self.database.cursor() as cursor:
            cursor.execute(sql, values)
            return cursor.fetchone()

    def scalar(self, sql, values=None, admin=False):
        connection = self.mysql_admin if admin else self.database
        with connection.cursor() as cursor:
            cursor.execute(sql, values)
            row = cursor.fetchone()
            return next(iter(row.values())) if row else None

    def fixture(self):
        self.execute("INSERT INTO problem(id,problem_id,title,source,visibility,time_limit,memory_limit,author_id,author_username) "
                     "VALUES(%s,'E2E-10','Worker regression',NULL,1,1000,262144,1,'admin')", (PROBLEM,))
        self.version(VERSION, INPUTS, [b"EXACT\n", b"EXACT\n"])
        self.version(NEW_VERSION, [b"changed"], [b"CHANGED\n"])
        self.execute("UPDATE problem SET test_data_version_id=%s WHERE id=%s", (VERSION, PROBLEM))
        now = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).replace(tzinfo=None)
        self.execute("INSERT INTO contest(id,creator_id,creator_username,title,mode,visibility,start_time,end_time) "
                     "VALUES(20,1,'admin','Worker E2E contest',0,0,%s,%s)",
                     (now - datetime.timedelta(hours=1), now + datetime.timedelta(hours=1)))
        self.execute("INSERT INTO contest_problem(id,display_id,contest_id,problem_id,display_title) "
                     "VALUES(30,'A',20,%s,'Worker regression')", (PROBLEM,))
        self.execute("INSERT INTO user(id,username,password,nickname,email) "
                     "SELECT 2,'e2e-user',password,'E2E user','worker-test@example.invalid' FROM user WHERE id=1")
        self.execute("INSERT INTO user_role(user_id,role_id) VALUES(2,1)")

    def version(self, version, inputs, outputs):
        entries = []
        for index, (input_data, output_data) in enumerate(zip(inputs, outputs), start=1):
            entry = {"id": index, "input": "/in/" + str(index).zfill(2) + ".in",
                     "output": "/out/" + str(index).zfill(2) + ".out"}
            for side, content, extension in [("input", input_data, "in"), ("output", output_data, "out")]:
                file_id = version * 100 + index * 2 + (0 if side == "input" else 1)
                key = "e2e-" + str(file_id) + "." + extension
                path = self.web_files / "private" / key
                path.write_bytes(content)
                if os.name != "nt":
                    path.chmod(0o600)
                sha256 = hashlib.sha256(content).hexdigest()
                self.execute("INSERT INTO file(id,file_name,file_type,file_size,file_ext,storage_path,storage_type,"
                             "biz_type,biz_id,is_public,storage_profile,bucket,object_key,sha256) "
                             "VALUES(%s,%s,'text/plain',%s,%s,%s,0,5,%s,0,'local','private',%s,%s)",
                             (file_id, key, len(content), extension, "private/" + key, PROBLEM, key, sha256))
                entry[side + "FileId"] = file_id
                entry[side + "Size"] = len(content)
                entry[side + "Sha256"] = sha256
            entries.append(entry)
        manifest = json.dumps({"problemId": PROBLEM, "versionId": version, "entries": entries},
                              ensure_ascii=False, separators=(",", ":"))
        self.execute("INSERT INTO test_data_version(id,problem_id,manifest,manifest_sha256,created_time) "
                     "VALUES(%s,%s,%s,%s,NOW())", (version, PROBLEM, manifest, hashlib.sha256(manifest.encode()).hexdigest()))

    def write_configs(self, base_web, base_worker):
        with socket.socket() as temporary:
            temporary.bind(("127.0.0.1", 0))
            self.port = temporary.getsockname()[1]
        topology = {"exchange": self.namespace + ".topic", "task-queue": self.namespace + ".task",
                    "result-queue": self.namespace + ".result", "task-routing-key": "judge", "result-routing-key": "judge.result"}
        web = copy.deepcopy(base_web)
        web["server"]["port"] = self.port
        web["server"]["address"] = "127.0.0.1"
        web["spring"]["profiles"] = {"active": "e2e"}
        web["spring"]["datasource"] = {"url": "jdbc:mysql://127.0.0.1:" + str(self.mysql_options["port"]) + "/" + self.schema
                                                  + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                                          "username": self.mysql_options["user"], "password": self.mysql_options["password"],
                                          "driver-class-name": "com.mysql.cj.jdbc.Driver",
                                          "druid": {"initial-size": 1, "min-idle": 1, "max-active": 5,
                                                    "connection-properties": "openTelemetry=DISABLED"}}
        web["spring"]["data"] = {"redis": self.redis_options}
        web["spring"]["rabbitmq"].update(self.rabbit_options)
        web["spring"]["rabbitmq"]["publisher-confirm-type"] = "correlated"
        web["spring"]["rabbitmq"]["publisher-returns"] = True
        web["ymcloud"]["file"]["base-dir"] = str(self.web_files)
        web["ymcloud"]["judge"].update(topology)
        web["ymcloud"]["judge"]["worker-api"] = {"token": self.service_token}
        web["ymcloud"]["judge"]["dispatch"].update({"poll-interval-ms": 200, "result-wait": "5m", "total-timeout": "20m"})
        web["token"]["secret"] = secrets.token_hex(64)
        web["logging"]["level"] = {"root": "INFO", "com.baomidou.mybatis-plus": "WARN"}
        worker = copy.deepcopy(base_worker)
        worker["spring"]["data"]["redis"] = self.redis_options
        worker["spring"]["rabbitmq"].update(self.rabbit_options)
        worker["ymcloud"]["judge"].update(topology)
        worker["ymcloud"]["worker"].update({"id": "e2e-" + self.identifier, "redis-prefix": self.worker_prefix,
                                           "work-dir": str(self.work), "journal-dir": str(self.journal), "concurrency": 1,
                                           "go-judge": {"base-url": self.go_judge_url, "token": self.go_judge_token},
                                           "cache": {"directory": str(self.cache)},
                                           "data": {"base-url": "http://127.0.0.1:" + str(self.port), "token": self.service_token,
                                                    "connect-timeout": "5s", "read-timeout": "30s"}})
        self.web_config = self.runtime / "web.yml"
        self.worker_config = self.runtime / "worker.yml"
        private_write(self.web_config, self.yaml.safe_dump(web, allow_unicode=True, sort_keys=False))
        private_write(self.worker_config, self.yaml.safe_dump(worker, allow_unicode=True, sort_keys=False))

    def start(self, kind):
        jar = PROJECT_ROOT / ("ymcloud-web" if kind == "web" else "ymcloud-judge") / "target" / (
            "ymcloud-web.jar" if kind == "web" else "ymcloud-judge.jar")
        require(jar.is_file(), "Build both current Boot jars before running E2E")
        configuration = self.web_config if kind == "web" else self.worker_config
        log_path = self.folder / (kind + "-" + str(len(self.processes)) + ".log")
        log_file = log_path.open("wb")
        if os.name != "nt":
            log_path.chmod(0o600)
        self.log_files.append(log_file)
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith(("SPRING_", "YMCLOUD_", "TOKEN_", "RABBITMQ_", "REDIS_", "MAIL_"))
                       and key not in ["JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"]}
        # 用小服务器的 JVM 预算验证真实链路，不让开发机默认堆与 CPU 数掩盖开销。
        heap = ["-Xms32m", "-Xmx128m"] if kind == "worker" else ["-Xms64m", "-Xmx256m"]
        process = subprocess.Popen([self.args.java, *heap, "-XX:ActiveProcessorCount=2",
                                    "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", str(jar),
                                    "--spring.config.location=" + configuration.as_uri(), "--spring.profiles.active=e2e"],
                                   cwd=PROJECT_ROOT, env=environment, stdout=log_file, stderr=subprocess.STDOUT,
                                   **hidden_process_options())
        self.processes.append(process)
        return process

    def stop(self, process):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)

    def http(self, method, path, payload=None, token=None, service=False):
        require(path.startswith("/") and "\r" not in path and "\n" not in path, "Unsafe test REST route")
        headers = {"Accept": "application/json", "User-Agent": "YMOJ-Worker-E2E"}
        if token:
            headers["Authorization"] = "Bearer " + token
        if service:
            headers["X-Judge-Worker-Token"] = self.service_token
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        if body is not None:
            headers["Content-Type"] = "application/json"
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=15)
        try:
            connection.request(method, path, body=body, headers=headers)
            response = connection.getresponse()
            raw = response.read(4 * 1024 * 1024 + 1)
            require(len(raw) <= 4 * 1024 * 1024, "Unexpected oversized E2E response")
            try:
                value = json.loads(raw)
            except (json.JSONDecodeError, UnicodeDecodeError):
                value = None
            return response.status, value, raw
        finally:
            connection.close()

    def wait(self, predicate, label, timeout=None):
        deadline = time.monotonic() + (timeout or self.args.timeout)
        while time.monotonic() < deadline:
            if self.mq and self.mq.is_open:
                self.mq.process_data_events(time_limit=0)
            try:
                if predicate():
                    return
            except (OSError, http.client.HTTPException):
                pass
            require(not any(process.poll() is not None for process in self.processes if process not in getattr(self, "stopped", [])),
                    "An owned Boot process exited unexpectedly")
            time.sleep(0.2)
        raise TimeoutError(label)

    def login(self, username):
        identifier = self.identifier + "-" + username
        key = "captcha_codes:" + identifier
        self.redis_keys.add(key)
        self.redis.set(key, json.dumps("2468"), ex=60)
        status, response, _ = self.http("POST", "/login", {"username": username, "password": INITIAL_PASSWORD,
                                                           "code": "2468", "uuid": identifier})
        require(status == 200 and response and response.get("code") == 200, "Initial fixture login failed")
        token = response["data"]
        require(isinstance(token, str) and len(token.split(".")) == 3, "Login did not return a JWT")
        encoded = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
        session = claims.get("login_user_key", "")
        require(re.fullmatch(r"[0-9a-f]{32}", session), "Unexpected login session identifier")
        self.redis_keys.add("login_tokens:" + session)
        self.tokens.append(token)
        return token

    def submit(self, token, code, contest=False, self_test=False):
        if self_test:
            path = "/judge/test"
            payload = {"language": "Python", "code": code, "input": INPUTS[0].decode(), "expectedOutput": INPUTS[0].decode()}
        else:
            path = "/judge/submit"
            payload = {"isContest": contest, "problemId": "A" if contest else "E2E-10", "language": "Python", "code": code}
            if contest:
                payload["contestId"] = 20
        self.redis_keys.add("repeat_submit:" + path + "Bearer " + token)
        status, response, _ = self.http("POST", path, payload, token=token)
        require(status == 200 and response and response.get("code") == 200, "Fixture judge submission failed")
        submission = int(response["data"])
        self.submissions.append(submission)
        return submission

    def completed(self, submission, expected_status, expected_cases=None):
        started = time.monotonic()
        self.wait(lambda: self.scalar("SELECT dispatch_state FROM judge_dispatch WHERE submission_id=%s", (submission,)) == 2,
                  "Submission did not reach durable terminal state")
        dispatch = self.row("SELECT * FROM judge_dispatch WHERE submission_id=%s", (submission,))
        self.redis_keys.add("judge:" + str(submission) + ":" + dispatch["execution_id"])
        require(dispatch["status"] == expected_status, "Unexpected terminal verdict")
        if expected_cases is not None:
            require(self.scalar("SELECT COUNT(*) FROM judge_case WHERE judge_id=%s", (submission,)) == expected_cases,
                    "Unexpected test point count")
        status, response, _ = self.http("GET", "/judge/status?submissionId=" + str(submission), token=self.tokens[0])
        require(status == 200 and response and response.get("code") == 200, "Status polling failed")
        expected_names = {0: "Accepted", -1: "Wrong Answer", -2: "Compile Error",
                          1: "Time Limit Exceeded", 3: "Runtime Error", 8: "Partial Accepted"}
        require(response["data"]["status"] == expected_names[expected_status], "Polled verdict differs from durable result")
        return dispatch, round((time.monotonic() - started) * 1000, 3)

    def publish(self, route, payload, execution):
        self.channel.basic_publish(exchange=self.namespace + ".topic", routing_key=route, body=payload.encode("utf-8"),
                                   properties=self.pika.BasicProperties(content_type="application/json", content_encoding="UTF-8",
                                                                       delivery_mode=2, message_id=execution), mandatory=True)

    def counts(self, submission):
        return (self.scalar("SELECT COUNT(*) FROM judge_case WHERE judge_id=%s", (submission,)),
                self.scalar("SELECT COUNT(*) FROM user_acproblem WHERE user_id=1 AND problem_id=%s", (PROBLEM,)))

    def verify_oi_scoring(self, token):
        self.stage = "oi-scoring"
        self.execute("UPDATE contest SET mode=1 WHERE id=20")
        try:
            code = ("import sys\ndata = sys.stdin.buffer.read()\n"
                    + "print('EXACT' if data == " + repr(INPUTS[0]) + " else 'WRONG')\n")
            submission = self.submit(token, code + "# OI one accepted point\n", contest=True)
            dispatch, _ = self.completed(submission, 8, 2)
            result = json.loads(dispatch["result_payload"])
            case_statuses = [case["status"] for case in result.get("testCaseResults", [])]
            self.check("OI executes both points and persists Partial Accepted with score 50",
                       self.scalar("SELECT score FROM judge WHERE id=%s", (submission,)) == 50
                       and result.get("score") == 50 and case_statuses == [0, -1])
        finally:
            self.execute("UPDATE contest SET mode=0 WHERE id=20")

    def run(self):
        self.prepare()
        self.stage = "start-web"
        self.start("web")
        route = "/internal/judge-worker/problems/10/versions/101/manifest"
        expected = self.scalar("SELECT manifest_sha256 FROM test_data_version WHERE id=101")
        self.wait(lambda: hashlib.sha256(self.http("GET", route, service=True)[2]).hexdigest() == expected,
                  "Web service identity endpoint did not start")
        admin = self.login("admin")
        other = self.login("e2e-user")
        self.check("Fixture admin and ordinary-user login")
        denied, _, _ = self.http("GET", route, token=admin)
        self.check("User login token cannot read Worker-only data", denied == 401)
        self.stage = "version-pin"
        exact = "import sys\ndata = sys.stdin.buffer.read()\nprint('EXACT' if data in " + repr(INPUTS) + " else 'BROKEN')\n"
        pinned = self.submit(admin, exact + "# queued version pin\n")
        self.wait(lambda: self.scalar("SELECT dispatch_state FROM judge_dispatch WHERE submission_id=%s", (pinned,)) == 1,
                  "Web outbox did not publish the queued task")
        self.execute("UPDATE problem SET test_data_version_id=%s WHERE id=%s", (NEW_VERSION, PROBLEM))
        self.worker = self.start("worker")
        dispatch, elapsed = self.completed(pinned, 0, 2)
        self.check("Queued task pins old version and preserves leading/trailing input bytes", dispatch["test_data_version_id"] == VERSION)
        self.report["timingsMs"]["coldFormalIncludingWorkerStartup"] = elapsed
        self.execute("UPDATE problem SET test_data_version_id=%s WHERE id=%s", (VERSION, PROBLEM))
        self.stage = "warm-cache"
        cached_file = self.cache / "p10-v101" / "10102.data"
        before = cached_file.stat().st_mtime_ns
        warm = self.submit(admin, exact + "# warm cache\n")
        _, elapsed = self.completed(warm, 0, 2)
        self.check("Warm version cache reused without rewriting files", cached_file.stat().st_mtime_ns == before)
        self.report["timingsMs"]["warmFormal"] = elapsed
        self.stage = "cache-repair"
        content = cached_file.read_bytes()
        cached_file.write_bytes(b"x" * len(content))
        repaired = self.submit(admin, exact + "# corrupt cache repair\n")
        self.completed(repaired, 0, 2)
        self.check("Corrupted idle cache redownloaded and verified", cached_file.read_bytes() == content)
        wrong = self.submit(admin, "print('WRONG')\n# wrong answer\n")
        self.completed(wrong, -1, 2)
        self.check("Formal ACM Wrong Answer retains both executed test points")
        self.verify_oi_scoring(admin)
        self.stage = "self-test-and-contest"
        self_test = self.submit(admin, "import sys\nsys.stdout.buffer.write(sys.stdin.buffer.read())\n# inline input\n", self_test=True)
        self.completed(self_test, 0)
        self.check("Self-test uses inline original input without a formal judge row",
                   self.scalar("SELECT COUNT(*) FROM judge WHERE id=%s", (self_test,)) == 0)
        contest = self.submit(admin, exact + "# contest task\n", contest=True)
        self.completed(contest, 0, 2)
        self.check("Contest submission executes in independent Worker",
                   self.scalar("SELECT contest_id FROM judge WHERE id=%s", (contest,)) == 20)
        _, forbidden, _ = self.http("GET", "/judge/status?submissionId=" + str(pinned), token=other)
        self.check("Ordinary user cannot poll another user's task", forbidden and forbidden.get("code") == 403)
        self.stage = "duplicates"
        original_counts = self.counts(pinned)
        dispatch = self.row("SELECT * FROM judge_dispatch WHERE submission_id=%s", (pinned,))
        observe = self.namespace + ".observe"
        self.channel.queue_declare(queue=observe, exclusive=True, auto_delete=True)
        self.channel.queue_bind(queue=observe, exchange=self.namespace + ".topic", routing_key="judge.result")
        self.publish("judge.result", dispatch["result_payload"], dispatch["execution_id"])
        self.publish("judge", dispatch["task_payload"], dispatch["execution_id"])
        self.wait(lambda: self.channel.queue_declare(queue=observe, passive=True).method.message_count >= 2,
                  "Duplicate task did not publish its saved result")
        self.wait(lambda: self.channel.queue_declare(queue=self.namespace + ".result", passive=True).method.message_count == 0,
                  "Duplicate results were not consumed")
        self.check("Duplicate task/result messages do not duplicate case rows or AC statistics", self.counts(pinned) == original_counts)
        self.stage = "worker-restart"
        journal = self.journal / (dispatch["execution_id"] + ".json")
        before = journal.stat().st_mtime_ns
        self.stop(self.worker)
        self.stopped = [self.worker]
        self.redis.delete(self.worker_prefix + "{" + dispatch["execution_id"] + "}:state",
                          self.worker_prefix + "{" + dispatch["execution_id"] + "}:lease")
        self.worker = self.start("worker")
        self.wait(lambda: self.channel.queue_declare(queue=self.namespace + ".task", passive=True).method.consumer_count == 1,
                  "Restarted Worker did not attach its isolated task queue")
        self.channel.queue_purge(queue=observe)
        self.publish("judge", dispatch["task_payload"], dispatch["execution_id"])
        self.wait(lambda: self.channel.queue_declare(queue=observe, passive=True).method.message_count >= 1,
                  "Restarted Worker did not recover the journal result")
        self.check("Worker restart recovers persisted result after losing only test Redis state",
                   journal.stat().st_mtime_ns == before and self.counts(pinned) == original_counts)
        self.check("Worker retained verified cache across restart", cached_file.read_bytes() == content)
        self.report["ok"] = True

    def cleanup(self):
        operations = [("Owned Boot processes", self.cleanup_processes),
                      ("Isolated RabbitMQ topology", self.cleanup_mq), ("Owned Redis keys", self.cleanup_redis),
                      ("Random local MySQL schema", self.cleanup_database), ("Temporary credentials and fixture files", self.cleanup_files)]
        for label, operation in operations:
            try:
                operation()
                self.report["cleanup"].append({"operation": label, "ok": True})
            except Exception as error:
                self.report["cleanup"].append({"operation": label, "ok": False, "errorType": type(error).__name__})
                self.report["ok"] = False

    def cleanup_processes(self):
        for process in reversed(self.processes):
            self.stop(process)
        for output in self.log_files:
            output.close()

    def cleanup_mq(self):
        if self.mq and self.mq.is_open:
            self.mq.close()
        if self.created_vhost:
            require(self.rabbit_vhost == "ymcloud-worker-e2e-" + self.identifier
                    and re.fullmatch(r"[0-9a-f]{32}", self.identifier), "Unsafe RabbitMQ vhost cleanup scope")
            self.rabbit_management("DELETE", "vhosts/" + self.rabbit_vhost)

    def cleanup_redis(self):
        if not self.redis_claimed:
            return
        require(self.redis.get(REDIS_CLAIM) == self.identifier.encode(), "E2E Redis ownership changed")
        for row in self.all_dispatches():
            self.redis_keys.add("judge:" + str(row["submission_id"]) + ":" + row["execution_id"])
        owned = set(self.redis.scan_iter(match=self.worker_prefix + "*"))
        owned.update(key.encode() for key in self.redis_keys)
        for key in owned - {REDIS_CLAIM.encode()}:
            self.redis.delete(key)
        leftovers = set(self.redis.scan_iter()) - {REDIS_CLAIM.encode()}
        require(not leftovers, "Unknown Redis keys retained; no broad deletion was attempted")
        self.redis.delete(REDIS_CLAIM)
        self.redis.close()

    def all_dispatches(self):
        if not self.database or not self.created_schema:
            return []
        with self.database.cursor() as cursor:
            cursor.execute("SHOW TABLES LIKE 'judge_dispatch'")
            if not cursor.fetchone():
                return []
            cursor.execute("SELECT submission_id,execution_id FROM judge_dispatch")
            return cursor.fetchall()

    def cleanup_database(self):
        if self.database:
            self.database.close()
        if self.created_schema:
            require(SCHEMA_PATTERN.fullmatch(self.schema), "Unsafe schema cleanup scope")
            require(self.scalar("SELECT @@server_uuid", admin=True) == self.server_uuid, "MySQL identity changed before cleanup")
            self.execute("DROP DATABASE `" + self.schema + "`", admin=True)
        if self.mysql_admin:
            self.mysql_admin.close()

    def cleanup_files(self):
        target = confined_test_path(PROJECT_ROOT / "target" / "phase3-verification/business", PROJECT_ROOT / "target" / "phase3-verification/business")
        runtime = confined_test_path(self.runtime, target)
        require(runtime == target / self.identifier / "runtime"
                and re.fullmatch(r"[0-9a-f]{32}", self.identifier),
                "Unsafe temporary-file cleanup scope")
        if runtime.exists():
            shutil.rmtree(runtime)


def verify_configuration_helpers():
    """Regression for unquoted numeric credentials; uses only in-memory fake clients."""
    for value, expected in [("  value  ", "  value  "), (0, "0"), (123, "123"),
                            (1.5, "1.5"), (True, "true"), (False, "false"), (None, None), ("", "")]:
        require(configuration_string(value) == expected, "Scalar connection binding changed")
    require(configuration_string(None, "") == "", "Empty MySQL/MQ password semantics changed")
    for invalid in [[], {}, (), set(), b"unsafe"]:
        try:
            configuration_string(invalid)
        except AssertionError:
            continue
        raise AssertionError("Container or binary credentials must be rejected")

    regression = Regression.__new__(Regression)
    for password in [None, ""]:
        options = regression.redis_client_options({"database": 15, "password": password})
        require(options["password"] is None, "Empty Redis password must disable password authentication")
    options = regression.redis_client_options({"database": 15, "username": 123, "password": 0})
    require(options["username"] == "123" and options["password"] == "0",
            "Numeric Redis credentials must remain strings, including zero")

    class FakePika:
        @staticmethod
        def PlainCredentials(username, password):
            return username, password

        @staticmethod
        def ConnectionParameters(**options):
            return options

    regression.pika = FakePika
    regression.rabbit_options = {"username": 123, "password": False, "virtual-host": 456}
    parameters = regression.mq_parameters()
    require(parameters["credentials"] == ("123", "false") and parameters["virtual_host"] == "456",
            "MQ scalar connection binding changed")
    regression.rabbit_options = {"username": None, "password": None}
    require(regression.mq_parameters()["credentials"] == ("", ""), "Explicitly empty MQ credentials changed")
    regression.rabbit_options = {}
    require(regression.mq_parameters()["credentials"] == ("guest", "guest"), "Missing MQ credential defaults changed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="Verify configuration helpers without contacting services")
    parser.add_argument("--java", default=str(pathlib.Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
                        if os.environ.get("JAVA_HOME") else shutil.which("java"))
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    if args.self_test:
        verify_configuration_helpers()
        print("PASS Worker E2E connection scalar binding, nullable credentials and unsafe value rejection")
        return 0
    require(os.environ.get("YMCLOUD_WORKER_E2E", "").lower() == "true", "E2E must be explicitly enabled")
    require(args.java and 30 <= args.timeout <= 300, "Java runtime or E2E timeout is invalid")
    import yaml
    import pymysql
    import redis
    import pika
    regression = Regression(args, yaml, pymysql, redis, pika)
    try:
        regression.run()
    except Exception as error:
        regression.report["error"] = {"stage": regression.stage, "errorType": type(error).__name__}
        if isinstance(error, (AssertionError, TimeoutError)):
            # Only our fixed assertion labels are reported; service/database exception bodies stay private.
            regression.report["error"]["checkLabel"] = str(error)
    finally:
        regression.cleanup()
        private_directory(regression.folder)
        private_write(regression.folder / "report.json", json.dumps(regression.report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"ok": regression.report["ok"], "checks": len(regression.report["checks"]),
                      "report": str((regression.folder / "report.json").relative_to(PROJECT_ROOT))}, ensure_ascii=False))
    return 0 if regression.report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())

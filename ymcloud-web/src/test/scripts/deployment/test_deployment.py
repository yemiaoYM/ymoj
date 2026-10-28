#!/usr/bin/env python3
"""Deployment regression using Compose config only; no running Docker daemon is needed."""

import copy
import importlib.util
import io
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[5]


def module_from_file(name, path):
    specification = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


PREFLIGHT = module_from_file("deployment_preflight", ROOT / "docker" / "web" / "preflight.py")
VERIFY = module_from_file("deployment_verification", pathlib.Path(__file__).with_name("verify_deployment.py"))
IMAGE_IDS = {name: "sha256:" + format(index + 1, "064x")
             for index, name in enumerate(sorted(VERIFY.SERVICES - {"preflight"}))}
IMAGE_REPOSITORIES = ("ccr.ccs.tencentyun.com/ymoj/ymcloud", "docker.io/yemiao1/ymoj")
DEFAULT_CREDENTIALS = {
    "MYSQL_ROOT_PASSWORD": "ymcloudRoot123456",
    "MYSQL_PASSWORD": "ymcloudMysql123456",
    "REDIS_PASSWORD": "ymcloudRedis123456",
    "RABBITMQ_PASSWORD": "ymcloudRabbit123456",
    "TOKEN_SECRET": "ymcloud_test_login_secret_0123456789_abcdefghijklmnopqrstuvwxyz_0123456789",
    "WORKER_TOKEN": "ymcloud_test_worker_token_0123456789abcdef",
    "GO_JUDGE_TOKEN": "ymcloud_test_go_judge_token_0123456789abcdef",
}


def valid_environment():
    return {
        "MYSQL_ROOT_PASSWORD": "TestRootPassword-0123456789",
        "MYSQL_PASSWORD": "TestAppPassword-0123456789",
        "REDIS_PASSWORD": "TestRedisPassword-0123456789",
        "RABBITMQ_PASSWORD": "TestQueuePassword-0123456789",
        "TOKEN_SECRET": "TestLoginSecret-0123456789" * 3,
        "WORKER_TOKEN": "TestWorkerSecret-0123456789" * 2,
        "GO_JUDGE_TOKEN": "TestSandboxSecret-0123456789" * 2,
        "YMCLOUD_WORKER_CONCURRENCY": "1",
        "YMCLOUD_GO_JUDGE_CONCURRENCY": "1",
        "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES": "1073741824",
        "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB": "524288",
        "YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES": "536870912",
        "YMCLOUD_GO_JUDGE_COMPILE_WALL_MS": "10000",
        "YMCLOUD_GO_JUDGE_PROCESS_LIMIT": "64",
        "YMCLOUD_GO_JUDGE_CPU_RATE": "1000",
        "JUDGE_CPUS": "1.0",
        "JUDGE_PIDS": "256",
        "WORKER_HEAP_MIB": "128",
        "WORKER_MEMORY_MIB": "384",
        "WORKER_PIDS": "128",
        "WORKER_CPUS": "0.5",
    }


def parallel_environment():
    env = valid_environment()
    env.update({"YMCLOUD_WORKER_CONCURRENCY": "2", "YMCLOUD_GO_JUDGE_CONCURRENCY": "2",
                "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES": "2147483648", "JUDGE_CPUS": "2",
                "WORKER_HEAP_MIB": "192", "WORKER_MEMORY_MIB": "448",
                "WORKER_PIDS": "192", "WORKER_CPUS": "1"})
    return env


def valid_contract(local=False, repository=IMAGE_REPOSITORIES[0]):
    name = "ymcloud-e2e-unit-test"
    services = {}
    for service in VERIFY.SERVICES:
        image = "web" if service == "preflight" else service
        services[service] = {
            "image": IMAGE_IDS[image] if local else repository + ":" + VERIFY.release_image_tag(image),
            "pull_policy": "never" if local else "missing", "platform": "linux/amd64",
            "mem_limit": 64 * 1024 * 1024, "pids_limit": 32,
            "logging": {"driver": "json-file", "options": {"max-size": "10m", "max-file": "3"}},
            "healthcheck": {"test": ["CMD", "true"]},
        }
    services["go-judge"].update({"privileged": True, "cgroup": "private", "memswap_limit": 64 * 1024 * 1024})
    for service in ("web", "worker"):
        services[service].update({"user": "10001:10001", "read_only": True,
                                  "environment": {"SPRING_DATA_REDIS_HOST": "redis", "SPRING_RABBITMQ_HOST": "rabbitmq"}})
    services["web"]["environment"]["SPRING_DATASOURCE_URL"] = "jdbc:mysql://mysql:3306/ymcloud?characterEncoding=UTF-8"
    services["worker"]["environment"].update({"YMCLOUD_WORKER_API_URL": "http://web:9091",
                                               "YMCLOUD_GO_JUDGE_URL": "http://go-judge:5050"})
    services["frontend"]["ports"] = [{"published": "18080", "target": 8080, "host_ip": "127.0.0.1"}]
    services["preflight"].update({"entrypoint": ["python3", "/opt/ymcloud/deployment-preflight.py"],
                                  "environment": valid_environment()})
    services["mysql"]["environment"] = {"MYSQL_ROOT_PASSWORD": "TestRootPassword-0123456789",
                                          "MYSQL_PASSWORD": "TestAppPassword-0123456789"}
    services["redis"]["environment"] = {"REDIS_PASSWORD": "TestRedisPassword-0123456789"}
    services["rabbitmq"]["environment"] = {"RABBITMQ_DEFAULT_PASS": "TestQueuePassword-0123456789"}
    for service in ("web", "worker"):
        services[service]["environment"].update({"SPRING_DATA_REDIS_PASSWORD": "TestRedisPassword-0123456789",
                                                "SPRING_RABBITMQ_PASSWORD": "TestQueuePassword-0123456789",
                                                "YMCLOUD_WORKER_SERVICE_TOKEN": "TestWorkerSecret-0123456789" * 2})
    services["web"]["environment"].update({"SPRING_DATASOURCE_PASSWORD": "TestAppPassword-0123456789",
                                           "YMCLOUD_TOKEN_SECRET": "TestLoginSecret-0123456789" * 3})
    services["worker"]["environment"]["YMCLOUD_GO_JUDGE_TOKEN"] = "TestSandboxSecret-0123456789" * 2
    services["go-judge"]["environment"] = {"YMCLOUD_GO_JUDGE_TOKEN": "TestSandboxSecret-0123456789" * 2}
    return {"name": name, "services": services,
            "volumes": {"data": {"name": name + "_data"}},
            "networks": {"data": {"internal": True, "name": name + "_data"},
                         "judge": {"internal": True, "name": name + "_judge"}}}


class PreflightTest(unittest.TestCase):
    def test_default_capacity_is_supported(self):
        PREFLIGHT.validate(valid_environment())

    def test_matching_parallel_capacity_is_supported(self):
        PREFLIGHT.validate(parallel_environment())

    def test_each_worker_budget_must_grow_with_parallelism(self):
        for key, value in (("WORKER_HEAP_MIB", "128"), ("WORKER_MEMORY_MIB", "384"),
                           ("WORKER_PIDS", "128"), ("WORKER_CPUS", "0.5")):
            with self.subTest(key=key):
                env = parallel_environment()
                env[key] = value
                with self.assertRaises(ValueError) as result:
                    PREFLIGHT.validate(env)
                self.assertIn(key, str(result.exception))

    def test_worker_cpu_budget_rejects_invalid_numbers(self):
        for value in ("", "invalid", "nan", "inf", "-1", "0"):
            with self.subTest(value=value):
                env = valid_environment()
                env["WORKER_CPUS"] = value
                with self.assertRaises(ValueError) as result:
                    PREFLIGHT.validate(env)
                self.assertIn("WORKER_CPUS", str(result.exception))

    def test_missing_short_example_and_invalid_secret_values_are_rejected_without_disclosure(self):
        for key in ("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "REDIS_PASSWORD", "RABBITMQ_PASSWORD",
                    "TOKEN_SECRET", "WORKER_TOKEN", "GO_JUDGE_TOKEN"):
            for value in ("", "short", "changeme" * 12, "A" * 20 + " secret", "A" * 64 + "中文", "A" * 513):
                with self.subTest(key=key, kind=len(value)):
                    env = valid_environment()
                    env[key] = value
                    with self.assertRaises(ValueError) as result:
                        PREFLIGHT.validate(env)
                    self.assertIn(key, str(result.exception))
                    if value:
                        self.assertNotIn(value, str(result.exception))

    def test_reused_service_secrets_are_rejected(self):
        env = valid_environment()
        env["GO_JUDGE_TOKEN"] = env["WORKER_TOKEN"]
        with self.assertRaises(ValueError):
            PREFLIGHT.validate(env)

    def test_mismatched_parallelism_and_insufficient_resources_are_rejected(self):
        invalid = [
            {"YMCLOUD_WORKER_CONCURRENCY": "2"},
            {"YMCLOUD_WORKER_CONCURRENCY": "2", "YMCLOUD_GO_JUDGE_CONCURRENCY": "2"},
            {"YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES": "536870912"},
            {"JUDGE_CPUS": "0.5"},
            {"JUDGE_CPUS": "nan"},
            {"JUDGE_CPUS": "inf"},
            {"JUDGE_CPUS": "invalid"},
            {"JUDGE_PIDS": "100"},
            {"YMCLOUD_GO_JUDGE_PROCESS_LIMIT": "256"},
            {"YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB": "1048576"},
            {"YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES": "1073741824"},
        ]
        for update in invalid:
            with self.subTest(fields=list(update)):
                env = valid_environment()
                env.update(update)
                with self.assertRaises(ValueError):
                    PREFLIGHT.validate(env)

    def test_integer_limits_reject_bad_and_out_of_range_inputs(self):
        for key in ("YMCLOUD_WORKER_CONCURRENCY", "YMCLOUD_GO_JUDGE_CONCURRENCY",
                    "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES", "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB",
                    "YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES", "YMCLOUD_GO_JUDGE_COMPILE_WALL_MS",
                    "YMCLOUD_GO_JUDGE_PROCESS_LIMIT", "YMCLOUD_GO_JUDGE_CPU_RATE", "JUDGE_PIDS",
                    "WORKER_HEAP_MIB", "WORKER_MEMORY_MIB", "WORKER_PIDS"):
            for value in ("", "1.2", "-1", "0", "999999999999999999"):
                with self.subTest(key=key, value=value):
                    env = valid_environment()
                    env[key] = value
                    with self.assertRaises(ValueError):
                        PREFLIGHT.validate(env)


class IsolationTest(unittest.TestCase):
    def setUp(self):
        self.directory = VERIFY.TEST_ROOT / "unit-release"

    def test_complete_isolated_stack_is_accepted(self):
        VERIFY.check_contract(valid_contract(local=True), self.directory, live=True)
        for repository in IMAGE_REPOSITORIES:
            with self.subTest(repository=repository):
                VERIFY.check_contract(valid_contract(repository=repository), self.directory, live=True)

    def test_pull_policy_must_match_local_or_repository_reference(self):
        for local in (True, False):
            for policy in (None, "always", "build", "missing" if local else "never"):
                with self.subTest(local=local, policy=policy):
                    model = valid_contract(local=local)
                    model["services"]["worker"]["pull_policy"] = policy
                    with self.assertRaises(AssertionError):
                        VERIFY.check_contract(model, self.directory)

    def test_source_and_local_seed_mounts_are_readonly_and_confined(self):
        for source in (self.directory / "initdb/ymcloud.sql", self.directory.parent / "sql/ymcloud.sql"):
            mount = {"type": "bind", "source": str(source), "read_only": True,
                     "target": "/docker-entrypoint-initdb.d/01-ymcloud.sql",
                     "bind": {"create_host_path": False}}
            model = valid_contract()
            model["services"]["mysql"]["volumes"] = [mount]
            VERIFY.check_contract(model, self.directory, live=True)
            for update in ({"read_only": False}, {"bind": {"create_host_path": True}},
                           {"source": str(ROOT / "sql/ymcloud.sql")}):
                with self.subTest(source=source.name, fields=list(update)):
                    changed = copy.deepcopy(model)
                    changed["services"]["mysql"]["volumes"][0].update(update)
                    with self.assertRaises(AssertionError):
                        VERIFY.check_contract(changed, self.directory, live=True)

    def test_preflight_script_must_be_built_into_matching_web_image(self):
        for update in ({"entrypoint": ["python3", "/opt/deploy/preflight.py"]},
                       {"image": "sha256:" + "b" * 64},
                       {"volumes": [{"type": "bind", "source": "preflight.py"}]}):
            with self.subTest(fields=list(update)):
                model = valid_contract()
                model["services"]["preflight"].update(update)
                with self.assertRaises(AssertionError):
                    VERIFY.check_contract(model, self.directory)

    def test_inconsistent_shared_credentials_are_rejected(self):
        targets = (("mysql", "MYSQL_PASSWORD"), ("web", "SPRING_DATASOURCE_PASSWORD"),
                   ("redis", "REDIS_PASSWORD"), ("web", "SPRING_DATA_REDIS_PASSWORD"),
                   ("worker", "SPRING_DATA_REDIS_PASSWORD"), ("rabbitmq", "RABBITMQ_DEFAULT_PASS"),
                   ("web", "SPRING_RABBITMQ_PASSWORD"), ("worker", "SPRING_RABBITMQ_PASSWORD"),
                   ("web", "YMCLOUD_WORKER_SERVICE_TOKEN"), ("worker", "YMCLOUD_WORKER_SERVICE_TOKEN"),
                   ("web", "YMCLOUD_TOKEN_SECRET"), ("worker", "YMCLOUD_GO_JUDGE_TOKEN"),
                   ("go-judge", "YMCLOUD_GO_JUDGE_TOKEN"))
        for service, key in targets:
            with self.subTest(service=service, key=key):
                model = valid_contract()
                model["services"][service]["environment"][key] = "different-synthetic-test-value"
                with self.assertRaises(AssertionError) as result:
                    VERIFY.check_contract(model, self.directory)
                self.assertNotIn("different-synthetic-test-value", str(result.exception))

    def test_compose_string_and_integer_resource_limits_are_equivalent(self):
        model = valid_contract()
        for service in model["services"].values():
            service["mem_limit"] = str(service["mem_limit"])
            service["pids_limit"] = str(service["pids_limit"])
        VERIFY.check_contract(model, self.directory, live=True)

    def test_missing_null_and_invalid_resource_limits_are_rejected(self):
        for field in ("mem_limit", "pids_limit"):
            for value in (None, "", 0, "0", -1, "unlimited", True):
                with self.subTest(field=field, value=value):
                    model = valid_contract()
                    model["services"]["worker"][field] = value
                    with self.assertRaises(AssertionError):
                        VERIFY.check_contract(model, self.directory, live=True)

    def test_external_and_host_resources_are_rejected(self):
        mutations = [
            lambda model: model["services"]["mysql"].update({"ports": [{"published": "3306"}]}),
            lambda model: model["services"]["worker"].update({"privileged": True}),
            lambda model: model["services"]["worker"].update({"network_mode": "host"}),
            lambda model: model["services"]["worker"].update({"volumes": [{"source": "/var/run/docker.sock"}]}),
            lambda model: model["services"]["worker"]["environment"].update({"YMCLOUD_WORKER_API_URL": "http://127.0.0.1:9091"}),
            lambda model: model["services"]["web"]["environment"].update({"SPRING_DATASOURCE_URL": "jdbc:mysql://host.docker.internal:3306/ymcloud"}),
            lambda model: model["services"]["web"]["environment"].update({"SPRING_DATA_REDIS_HOST": "host.docker.internal"}),
            lambda model: model["volumes"]["data"].update({"external": True}),
            lambda model: model["volumes"]["data"].update({"name": "production_data"}),
            lambda model: model["networks"]["judge"].update({"internal": False}),
            lambda model: model["services"]["frontend"]["ports"][0].update({"host_ip": "0.0.0.0"}),
            lambda model: model.update({"name": "ymcloud"}),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(case=index):
                model = copy.deepcopy(valid_contract())
                mutate(model)
                with self.assertRaises(AssertionError):
                    VERIFY.check_contract(model, self.directory, live=True)

    def test_release_rejects_builds_floating_images_and_missing_placeholders(self):
        for update in ({"build": "."}, {"pull_policy": "always"}, {"image": "__YMCLOUD_WEB_IMAGE__"},
                       {"image": "ymcloud-web:latest"}, {"image": "docker.io/unit-test/web:4.0.0"},
                       {"image": IMAGE_REPOSITORIES[0] + ":web-3.0.0"},
                       {"image": IMAGE_REPOSITORIES[0] + ":worker-" + VERIFY.project_version(ROOT)},
                       {"image": IMAGE_REPOSITORIES[0] + "@" + IMAGE_IDS["web"]},
                       {"image": "docker.io/unit-test/web@sha256:short"},
                       {"container_name": "ymcloud-web"}, {"user": "0:0"}):
            with self.subTest(fields=list(update)):
                model = valid_contract()
                model["services"]["web"].update(update)
                with self.assertRaises(AssertionError):
                    VERIFY.check_contract(model, self.directory)

    def test_release_tags_follow_parent_version_and_keep_exact_upstream_versions(self):
        with mock.patch.object(VERIFY, "project_version", return_value="4.1.0-rc.1"):
            model = valid_contract()
            VERIFY.check_contract(model, self.directory)
            for service in VERIFY.SERVICES:
                image = "web" if service == "preflight" else service
                version = VERIFY.UPSTREAM_VERSIONS.get(image, "4.1.0-rc.1")
                self.assertEqual(IMAGE_REPOSITORIES[0] + ":" + image + "-" + version,
                                 model["services"][service]["image"])
        for service in VERIFY.UPSTREAM_VERSIONS:
            model = valid_contract()
            model["services"][service]["image"] = IMAGE_REPOSITORIES[0] + ":" + service + "-latest"
            with self.assertRaisesRegex(AssertionError, "exact service version tag"):
                VERIFY.check_contract(model, self.directory)

    def test_live_paths_cannot_escape_test_root(self):
        for path in (ROOT, ROOT / "deploy", VERIFY.TEST_ROOT / ".." / "not-a-test"):
            with self.subTest(path=path.name):
                with self.assertRaises(AssertionError):
                    VERIFY.confined_path(path)

    def test_bind_mounts_cannot_write_or_reference_host_files(self):
        for mount in ({"type": "bind", "source": str(ROOT / "sql" / "ymcloud.sql"), "read_only": True},
                      {"type": "bind", "source": str(self.directory / "preflight.py"), "read_only": False}):
            with self.subTest(readonly=mount["read_only"]):
                model = valid_contract()
                model["services"]["web"]["volumes"] = [mount]
                with self.assertRaises(AssertionError):
                    VERIFY.check_contract(model, self.directory, live=True)


class RunningImageTest(unittest.TestCase):
    def fixture(self, local=False, repository=IMAGE_REPOSITORIES[0]):
        verification = object.__new__(VERIFY.Verification)
        verification.config = valid_contract(local=local, repository=repository)
        verification.directory = VERIFY.TEST_ROOT / "unit-runtime"
        verification.port = 18080
        verification.check = mock.Mock()
        states = {}
        containers = []
        images = {}
        for index, name in enumerate(sorted(VERIFY.SERVICES)):
            service = "web" if name == "preflight" else name
            identifier = format(index + 20, "064x")
            states[name] = {"ID": identifier}
            model = verification.config["services"][name]
            containers.append({
                "Image": IMAGE_IDS[service],
                "Config": {
                    "Labels": {"com.docker.compose.project": verification.config["name"],
                               "com.docker.compose.project.working_dir": str(verification.directory),
                               "com.docker.compose.service": name},
                    "Env": [key + "=" + str(value) for key, value in model.get("environment", {}).items()],
                },
                "HostConfig": {"NetworkMode": "bridge", "Privileged": name == "go-judge",
                               "PortBindings": {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "18080"}]}
                               if name == "frontend" else {}},
                "Mounts": [],
            })
            images[model["image"]] = {
                "Id": IMAGE_IDS[service], "Os": "linux", "Architecture": "amd64",
                "RepoTags": [] if local else [model["image"]],
                "Config": {"Labels": {"org.opencontainers.image.version": VERIFY.project_version(ROOT)}}
                if service in VERIFY.PROJECT_SERVICES else {},
            }
        return verification, states, containers, images

    def run_fixture(self, verification, states, containers, images):
        def inspect(arguments, label):
            if arguments[:2] == ["docker", "inspect"]:
                return json.dumps(containers)
            self.assertEqual(["docker", "image", "inspect"], arguments[:3])
            return json.dumps([images[arguments[3]]])

        with mock.patch.object(VERIFY, "command", side_effect=inspect) as command:
            verification.verify_container_ownership(states)
        self.assertEqual(1 + len(images), command.call_count)
        verification.check.assert_called_once()

    def test_local_and_both_registry_images_match_container_content(self):
        self.run_fixture(*self.fixture(local=True))
        for repository in IMAGE_REPOSITORIES:
            with self.subTest(repository=repository):
                self.run_fixture(*self.fixture(repository=repository))

    def test_docker_hub_canonical_alias_preserves_exact_tag_matching(self):
        fixture = self.fixture(repository="docker.io/yemiao1/ymoj")
        for image in fixture[3].values():
            image["RepoTags"] = ["ymcloud-local:4.0.0", image["RepoTags"][0].removeprefix("docker.io/")]
        self.run_fixture(*fixture)
        self.assertEqual("docker.io/library/ymcloud-local:4.0.0",
                         VERIFY.canonical_repository_tag("ymcloud-local:4.0.0"))
        self.assertEqual("docker.io/yemiao1/ymoj:web-4.0.0",
                         VERIFY.canonical_repository_tag("index.docker.io/yemiao1/ymoj:web-4.0.0"))
        self.assertEqual("registry.example:5000/team/ymcloud:web-4.0.0",
                         VERIFY.canonical_repository_tag("registry.example:5000/team/ymcloud:web-4.0.0"))

    def test_mismatched_running_content_tag_platform_and_oci_version_are_rejected(self):
        for mutation in ("container", "repository", "tag", "digest-only", "platform", "variant", "version",
                         "missing-label", "local-id", "local-version"):
            with self.subTest(mutation=mutation):
                verification, states, containers, images = self.fixture(local=mutation.startswith("local-"))
                reference = verification.config["services"]["worker"]["image"]
                if mutation == "container":
                    for container in containers:
                        if container["Config"]["Labels"]["com.docker.compose.service"] == "worker":
                            container["Image"] = IMAGE_IDS["web"]
                elif mutation == "repository":
                    images[reference]["RepoTags"] = ["registry.example/other:worker-" + VERIFY.project_version(ROOT)]
                elif mutation == "tag":
                    images[reference]["RepoTags"] = [reference.rsplit(":", 1)[0] + ":worker-3.0.0"]
                elif mutation == "digest-only":
                    images[reference]["RepoTags"] = []
                    images[reference]["RepoDigests"] = [reference.rsplit(":", 1)[0] + "@" + IMAGE_IDS["worker"]]
                elif mutation == "platform":
                    images[reference]["Architecture"] = "arm64"
                elif mutation == "variant":
                    images[reference]["Variant"] = "v3"
                elif mutation in ("version", "local-version"):
                    images[reference]["Config"]["Labels"]["org.opencontainers.image.version"] = "3.0.0"
                elif mutation == "missing-label":
                    images[reference]["Config"] = {}
                else:
                    images[reference]["Id"] = IMAGE_IDS["web"]
                with self.assertRaises(AssertionError):
                    self.run_fixture(verification, states, containers, images)


class LifecycleTest(unittest.TestCase):
    def fixture(self):
        verification = object.__new__(VERIFY.Verification)
        verification.args = types.SimpleNamespace(live=True, recreate=True, restart=False, timeout=120)
        verification.config = valid_contract(local=True)
        verification.directory = VERIFY.TEST_ROOT / "unit-recreate"
        verification.compose = ["docker", "compose", "--project-directory", str(verification.directory)]
        verification.check = mock.Mock()
        verification.token = "synthetic-original-session"
        verification.completed_results = [
            {"identifier": 1, "selfTest": True,
             "result": {"status": "Accepted", "time": 10, "memory": 2048, "output": "5\n"}},
            {"identifier": 2, "selfTest": False,
             "result": {"status": "Accepted", "time": 12, "memory": 2048}},
        ]
        before = {name: {"ID": format(index + 1, "064x")} for index, name in enumerate(sorted(VERIFY.SERVICES))}
        after = {name: {"ID": format(index + 101, "064x")} for index, name in enumerate(sorted(VERIFY.SERVICES))}
        verification.wait_healthy = mock.Mock(side_effect=[before, after])
        verification.verify_container_ownership = mock.Mock()
        verification.containers = mock.Mock(return_value={})
        volume = {"Name": verification.config["name"] + "_data", "CreatedAt": "2026-10-15T00:00:00Z",
                  "Mountpoint": "/var/lib/docker/volumes/unit-recreate/_data", "Driver": "local", "Options": None,
                  "Labels": {"com.docker.compose.project": verification.config["name"],
                             "com.docker.compose.volume": "data"}}
        return verification, before, after, volume

    def commands(self, verification, volumes, calls):
        snapshots = iter(volumes)

        def command(arguments, label, input_text=None, timeout=60):
            calls.append((arguments, timeout))
            if arguments[:3] == ["docker", "volume", "inspect"]:
                return json.dumps([next(snapshots)])
            self.assertEqual(verification.compose, arguments[:len(verification.compose)])
            operation = arguments[len(verification.compose)]
            self.assertIn(operation, ("config", "down", "up"))
            return json.dumps(verification.config) if operation == "config" else ""

        return command

    def test_cold_recreation_preserves_volumes_replaces_every_container_and_keeps_deadline(self):
        verification, before, after, volume = self.fixture()
        calls = []
        with (mock.patch.object(VERIFY, "command", side_effect=self.commands(verification, [volume] * 3, calls)),
              mock.patch.object(VERIFY.time, "monotonic", return_value=1000)):
            verification.recreate_stack()
        operations = [arguments[len(verification.compose):] for arguments, _ in calls
                      if arguments[:len(verification.compose)] == verification.compose]
        self.assertEqual([["config", "--format", "json"], ["down", "--timeout", "45"],
                          ["up", "-d", "--no-build", "--wait", "--wait-timeout", "120"]], operations)
        self.assertFalse(any("-v" in arguments or "--volumes" in arguments or "rm" in arguments
                             for arguments, _ in calls))
        self.assertEqual([mock.call(before), mock.call(after)], verification.verify_container_ownership.call_args_list)
        self.assertEqual([mock.call(), mock.call(deadline=1120)], verification.wait_healthy.call_args_list)
        self.assertTrue(all(timeout <= verification.args.timeout for _, timeout in calls))
        self.assertEqual(3, sum(arguments[:3] == ["docker", "volume", "inspect"] for arguments, _ in calls))

    def test_cold_recreation_refuses_changed_configuration_or_failed_ownership_before_down(self):
        for failure in ("configuration", "container", "volume"):
            verification, _, _, volume = self.fixture()
            calls = []
            if failure == "container":
                verification.verify_container_ownership.side_effect = AssertionError("Foreign container")
            elif failure == "volume":
                volume["Labels"]["com.docker.compose.project"] = "production"
            execute = self.commands(verification, [volume] * 3, calls)

            def changed(arguments, label, **kwargs):
                result = execute(arguments, label, **kwargs)
                return json.dumps({"name": "production"}) if failure == "configuration" else result

            with mock.patch.object(VERIFY, "command", side_effect=changed):
                with self.assertRaises(AssertionError):
                    verification.recreate_stack()
            self.assertFalse(any("down" in arguments or "up" in arguments for arguments, _ in calls))

    def test_cold_recreation_detects_removed_volumes_remaining_containers_and_reused_ids(self):
        for failure in ("volume-after-down", "volume-after-up", "remaining-container", "same-container"):
            verification, before, after, volume = self.fixture()
            replaced = {**volume, "CreatedAt": "2026-10-16T00:00:00Z"}
            volumes = [volume, replaced if failure == "volume-after-down" else volume,
                       replaced if failure == "volume-after-up" else volume]
            if failure == "remaining-container":
                verification.containers.return_value = before
            elif failure == "same-container":
                after["mysql"]["ID"] = before["mysql"]["ID"]
            calls = []
            with mock.patch.object(VERIFY, "command", side_effect=self.commands(verification, volumes, calls)):
                with self.assertRaises(AssertionError):
                    verification.recreate_stack()
            if failure in ("volume-after-down", "remaining-container"):
                self.assertFalse(any("up" in arguments for arguments, _ in calls))

    def test_persistence_verifies_old_results_and_session_before_new_formal_submission(self):
        verification, _, _, _ = self.fixture()
        events = []
        results = {str(record["identifier"]): record["result"] for record in verification.completed_results}

        def api(method, route):
            events.append(route)
            if route == "/user/profile":
                return {"id": 1, "username": "admin"}
            return results[route.rsplit("=", 1)[1]].copy()

        verification.api = mock.Mock(side_effect=api)
        verification.recreate_stack = mock.Mock(side_effect=lambda: events.append("recreate"))
        verification.verify_uploaded_fixture = mock.Mock(side_effect=lambda: events.append("test-data"))
        verification.submit = mock.Mock(side_effect=lambda *args: events.append("new-submission"))
        verification.verify_logs_and_cleanup = mock.Mock(side_effect=lambda value: events.append("logs-cleanup"))
        logs = {"records": {"retained.json": "digest"}}
        verification.verify_lifecycle_persistence(logs)
        self.assertEqual(["recreate", "/user/profile", "/judge/status?submissionId=1",
                          "/judge/status?submissionId=2", "test-data", "new-submission", "logs-cleanup"], events)
        verification.verify_logs_and_cleanup.assert_called_once_with(logs)
        self.assertNotIn("self_test", verification.submit.call_args.kwargs)
        self.assertEqual("synthetic-original-session", verification.token)

    def test_persistence_blocks_self_test_as_last_task_or_changed_historical_result(self):
        for failure in ("last-self-test", "session", "verdict", "resource", "output"):
            verification, _, _, _ = self.fixture()
            verification.recreate_stack = mock.Mock()
            verification.submit = mock.Mock()
            verification.verify_uploaded_fixture = mock.Mock()
            verification.verify_logs_and_cleanup = mock.Mock()
            if failure == "last-self-test":
                verification.completed_results[-1]["selfTest"] = True
            result = verification.completed_results[0]["result"].copy()
            if failure == "verdict":
                result["status"] = "Wrong Answer"
            elif failure == "resource":
                result["time"] += 1
            elif failure == "output":
                result["output"] = "changed"
            profile = {"id": 2 if failure == "session" else 1, "username": "admin"}
            verification.api = mock.Mock(side_effect=[profile, result])
            with self.assertRaises(AssertionError):
                verification.verify_lifecycle_persistence({})
            verification.submit.assert_not_called()
            if failure == "last-self-test":
                verification.recreate_stack.assert_not_called()

    def test_uploaded_test_files_require_original_metadata_and_exact_bytes(self):
        verification, _, _, _ = self.fixture()
        verification.fixture_route = "/admin/problem/1/testcases"
        verification.fixture_cases = [{"id": 1, "input": "/in/01.in", "output": "/out/01.out"},
                                      {"id": 2, "input": "/in/02.in", "output": "/out/02.out"}]
        verification.api = mock.Mock(return_value=verification.fixture_cases)
        verification.request = mock.Mock(side_effect=[(200, None, data) for _, data in VERIFY.TEST_CASE_FILES])
        verification.verify_uploaded_fixture()
        self.assertEqual([mock.call("GET", "/api/admin/problem/1/testcases/download?fileName=" + path)
                          for path in ("%2Fin%2F01.in", "%2Fout%2F01.out", "%2Fin%2F02.in", "%2Fout%2F02.out")],
                         verification.request.call_args_list)
        for status, data in ((404, b""), (200, b"changed")):
            verification.request = mock.Mock(return_value=(status, None, data))
            with self.assertRaises(AssertionError):
                verification.verify_uploaded_fixture()
        verification.api.return_value = []
        with self.assertRaisesRegex(AssertionError, "metadata"):
            verification.verify_uploaded_fixture()

    def test_uploaded_test_file_paths_reject_missing_duplicate_extra_and_bare_names(self):
        for failure in ("missing", "duplicate", "extra", "bare-name", "same-basename", "traversal"):
            with self.subTest(failure=failure):
                verification, _, _, _ = self.fixture()
                verification.fixture_route = "/admin/problem/1/testcases"
                cases = [{"id": 1, "input": "/in/01.in", "output": "/out/01.out"},
                         {"id": 2, "input": "/in/02.in", "output": "/out/02.out"}]
                if failure == "missing":
                    del cases[1]["output"]
                elif failure == "duplicate":
                    cases[1]["input"] = cases[0]["input"]
                elif failure == "extra":
                    cases.append({"id": 3, "input": "/in/03.in", "output": "/out/03.out"})
                elif failure == "bare-name":
                    cases[0]["input"] = "01.in"
                elif failure == "same-basename":
                    cases[1]["input"] = "/another/01.in"
                else:
                    cases[0]["input"] = "/in/../01.in"
                verification.fixture_cases = cases
                verification.api = mock.Mock(return_value=cases)
                verification.request = mock.Mock()
                with self.assertRaises(AssertionError):
                    verification.verify_uploaded_fixture()
                verification.request.assert_not_called()

    def test_new_submission_cannot_reuse_a_completed_identifier(self):
        verification, _, _, _ = self.fixture()
        verification.fixture_id = "YMOJ-1"
        verification.api = mock.Mock(return_value=2)
        verification.wait_verdict = mock.Mock(return_value={"status": "Accepted"})
        with self.assertRaisesRegex(AssertionError, "ID was reused"):
            verification.submit("Python", VERIFY.SOURCES["Python"])
        self.assertEqual(2, len(verification.completed_results))

    def test_live_run_ends_pre_shutdown_work_with_formal_submission_for_both_modes(self):
        for mode in ("restart", "recreate"):
            verification, _, _, _ = self.fixture()
            verification.args.restart = mode == "restart"
            verification.args.recreate = mode == "recreate"
            verification.report = {"ok": False}

            def request(method, route):
                if route == "/":
                    return 200, None, b'<div id="app"></div>'
                if route == "/api/captchaImage":
                    return 200, {"code": 200}, b""
                return 403, None, b""

            verification.request = mock.Mock(side_effect=request)
            for name in ("verify_internal_authentication", "verify_bootstrap", "login", "create_fixture",
                         "submit", "verify_lifecycle_persistence"):
                setattr(verification, name, mock.Mock())
            verification.verify_logs_and_cleanup = mock.Mock(return_value={"records": {}})
            verification.run()
            submissions = verification.submit.call_args_list
            self.assertTrue(submissions[-2].kwargs["self_test"])
            self.assertFalse(submissions[-1].kwargs.get("self_test", False))
            self.assertIn("before lifecycle check", submissions[-1].args[1])
            verification.verify_lifecycle_persistence.assert_called_once_with({"records": {}})

    def test_cli_rejects_nonlive_recreation_mutually_exclusive_modes_and_extended_timeout(self):
        for mode in ("--restart", "--recreate"):
            with self.assertRaisesRegex(AssertionError, "require --live"):
                VERIFY.main(["--project-dir", "unused", mode])
        with mock.patch.object(VERIFY.sys, "stderr", new_callable=io.StringIO):
            with self.assertRaises(SystemExit) as result:
                VERIFY.main(["--project-dir", "unused", "--live", "--restart", "--recreate"])
        self.assertEqual(2, result.exception.code)
        with self.assertRaisesRegex(AssertionError, "between 10 and 900"):
            VERIFY.main(["--project-dir", "unused", "--live", "--recreate", "--timeout", "901"])


@unittest.skipUnless(shutil.which("docker"), "Compose interpolation checks require the Docker CLI")
class ComposeConfigurationTest(unittest.TestCase):
    def setUp(self):
        test_root = VERIFY.confined_path(VERIFY.TEST_ROOT)
        test_root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="compose-unit-", dir=test_root)
        self.addCleanup(self.temporary.cleanup)
        self.directory = VERIFY.confined_path(pathlib.Path(self.temporary.name) / "deploy")
        self.directory.mkdir()
        seed = self.directory.parent / "sql/ymcloud.sql"
        seed.parent.mkdir()
        seed.write_text("-- public test seed\n", encoding="utf-8")
        source = (ROOT / "deploy" / "compose.yaml").read_text(encoding="utf-8")
        self.source = source
        self.environment_keys = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", source))
        (self.directory / "compose.yaml").write_text(source, encoding="utf-8")

    def resolve(self, values=None, environment=None):
        # Use only an explicitly generated file, never a repository or developer .env.
        content = "".join(key + "='" + value + "'\n" for key, value in (values or {}).items())
        env_file = self.directory / "fixture.env"
        env_file.write_text(content, encoding="utf-8")
        process_env = {key: value for key, value in os.environ.items()
                       if key not in self.environment_keys and not key.startswith("COMPOSE_")}
        process_env.update(environment or {})
        options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
        result = subprocess.run(
            [shutil.which("docker"), "compose", "--project-directory", str(self.directory),
             "--env-file", str(env_file), "-f", str(self.directory / "compose.yaml"),
             "config", "--format", "json"],
            cwd=self.directory, env=process_env, capture_output=True, text=True,
            encoding="utf-8", errors="strict", timeout=30, **options,
        )
        self.assertEqual(0, result.returncode, "Compose could not resolve the isolated test configuration")
        return VERIFY.parse_compose_config(result.stdout)

    def assert_resolved_credentials(self, model, expected):
        environments = {name: service.get("environment", {}) for name, service in model["services"].items()}
        for key, value in expected.items():
            self.assertTrue(environments["preflight"].get(key) == value, "Preflight did not receive " + key)
        self.assertTrue(environments["mysql"].get("MYSQL_ROOT_PASSWORD") == expected["MYSQL_ROOT_PASSWORD"])
        self.assertTrue(environments["mysql"].get("MYSQL_PASSWORD") == expected["MYSQL_PASSWORD"])
        self.assertTrue(environments["web"].get("SPRING_DATASOURCE_PASSWORD") == expected["MYSQL_PASSWORD"])
        self.assertTrue(environments["redis"].get("REDIS_PASSWORD") == expected["REDIS_PASSWORD"])
        self.assertTrue(environments["rabbitmq"].get("RABBITMQ_DEFAULT_PASS") == expected["RABBITMQ_PASSWORD"])
        for name in ("web", "worker"):
            self.assertTrue(environments[name].get("SPRING_DATA_REDIS_PASSWORD") == expected["REDIS_PASSWORD"])
            self.assertTrue(environments[name].get("SPRING_RABBITMQ_PASSWORD") == expected["RABBITMQ_PASSWORD"])
            self.assertTrue(environments[name].get("YMCLOUD_WORKER_SERVICE_TOKEN") == expected["WORKER_TOKEN"])
        self.assertTrue(environments["web"].get("YMCLOUD_TOKEN_SECRET") == expected["TOKEN_SECRET"])
        for name in ("worker", "go-judge"):
            self.assertTrue(environments[name].get("YMCLOUD_GO_JUDGE_TOKEN") == expected["GO_JUDGE_TOKEN"])
        PREFLIGHT.validate(environments["preflight"])
        VERIFY.check_contract(model, self.directory)

    def test_missing_variables_resolve_to_usable_defaults(self):
        self.assert_resolved_credentials(self.resolve(), DEFAULT_CREDENTIALS)

    def test_default_sandbox_capacity_supports_1024_mib(self):
        model = self.resolve()
        for service in ("preflight", "worker"):
            env = model["services"][service]["environment"]
            self.assertEqual("1048576", env["YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB"])
            self.assertEqual("1610612736", env["YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES"])
        sandbox = model["services"]["go-judge"]
        self.assertEqual("1610612736", sandbox["mem_limit"])
        self.assertEqual(sandbox["mem_limit"], sandbox["memswap_limit"])
        PREFLIGHT.validate(model["services"]["preflight"]["environment"])

    def test_web_native_libraries_use_private_executable_tmpfs(self):
        web = self.resolve()["services"]["web"]
        self.assertEqual("-Djna.tmpdir=/jna-tmp", web["environment"]["JAVA_TOOL_OPTIONS"])
        mounts = {mount.split(":", 1)[0]: set(mount.split(":", 1)[1].split(","))
                  for mount in web["tmpfs"]}
        self.assertIn("noexec", mounts["/tmp"])
        self.assertNotIn("exec", mounts["/tmp"])
        self.assertTrue({"rw", "exec", "nosuid", "nodev", "mode=0700", "uid=10001", "gid=10001"}
                        <= mounts["/jna-tmp"])
        self.assertNotIn("noexec", mounts["/jna-tmp"])
        self.assertTrue(web["read_only"])
        self.assertEqual("10001:10001", web["user"])

    def test_empty_dotenv_values_resolve_to_the_same_defaults(self):
        values = {key: "" for key in DEFAULT_CREDENTIALS}
        self.assert_resolved_credentials(self.resolve(values), DEFAULT_CREDENTIALS)

    def test_explicit_dotenv_values_reach_every_service_unchanged(self):
        values = {key: valid_environment()[key] for key in DEFAULT_CREDENTIALS}
        values["MYSQL_ROOT_PASSWORD"] += "$#literal"
        values["WORKER_TOKEN"] += "$$#literal"
        self.assert_resolved_credentials(self.resolve(values), values)

    def test_process_overrides_take_precedence_consistently(self):
        values = {key: valid_environment()[key] for key in DEFAULT_CREDENTIALS}
        overridden = {key: value + "-process" for key, value in values.items()}
        self.assert_resolved_credentials(self.resolve(values, overridden), overridden)

    def test_empty_process_values_use_fallback_instead_of_dotenv(self):
        values = {key: valid_environment()[key] for key in DEFAULT_CREDENTIALS}
        self.assert_resolved_credentials(self.resolve(values, {key: "" for key in values}), DEFAULT_CREDENTIALS)

    def test_image_repository_switch_changes_only_repositories_and_keeps_service_version_tags(self):
        baseline = self.resolve()
        for repository in IMAGE_REPOSITORIES:
            with self.subTest(repository=repository):
                model = self.resolve({"IMAGE_REPOSITORY": repository})
                for name, service in model["services"].items():
                    self.assertEqual(repository + ":" + VERIFY.release_image_tag(name), service["image"])
                    self.assertEqual("missing", service["pull_policy"])
                VERIFY.check_contract(model, self.directory)
                unchanged = copy.deepcopy(model)
                for name, service in unchanged["services"].items():
                    service["image"] = baseline["services"][name]["image"]
                self.assertEqual(baseline, unchanged)

    def test_image_repository_defaults_empty_values_and_process_override(self):
        baseline = self.resolve()
        self.assertTrue(all(service["image"].startswith(IMAGE_REPOSITORIES[0] + ":")
                            for service in baseline["services"].values()))
        self.assertEqual(baseline, self.resolve({"IMAGE_REPOSITORY": ""}))
        self.assertEqual(baseline, self.resolve({"IMAGE_REPOSITORY": IMAGE_REPOSITORIES[1]}, {"IMAGE_REPOSITORY": ""}))
        expected = self.resolve({"IMAGE_REPOSITORY": IMAGE_REPOSITORIES[1]})
        self.assertEqual(expected, self.resolve({"IMAGE_REPOSITORY": IMAGE_REPOSITORIES[0]},
                                               {"IMAGE_REPOSITORY": IMAGE_REPOSITORIES[1]}))

    def test_local_render_retains_never_policy_and_relocates_seed_inside_package(self):
        release = module_from_file("deployment_release", ROOT / "docker/release.py")
        records = {name: {"imageId": identifier} for name, identifier in IMAGE_IDS.items()}
        rendered = release.render_compose(self.source, records)
        (self.directory / "compose.yaml").write_text(rendered, encoding="utf-8")
        seed = self.directory / "initdb/ymcloud.sql"
        seed.parent.mkdir()
        seed.write_text("-- public local seed\n", encoding="utf-8")
        model = self.resolve()
        for name, service in model["services"].items():
            image = "web" if name == "preflight" else name
            self.assertEqual(IMAGE_IDS[image], service["image"])
            self.assertEqual("never", service["pull_policy"])
        mounts = [mount for mount in model["services"]["mysql"]["volumes"] if mount["type"] == "bind"]
        self.assertEqual(1, len(mounts))
        self.assertEqual(seed, pathlib.Path(mounts[0]["source"]))
        self.assertTrue(mounts[0]["read_only"])
        self.assertFalse(mounts[0].get("bind", {}).get("create_host_path", False))
        VERIFY.check_contract(model, self.directory)

    def test_release_cannot_reintroduce_external_python_or_example_env(self):
        model = self.resolve()
        for name in ("preflight.py", "nested/helper.py", ".env.example"):
            with self.subTest(name=name):
                path = self.directory / name
                path.parent.mkdir(exist_ok=True)
                path.write_text("# public test fixture\n", encoding="utf-8")
                with self.assertRaises(AssertionError):
                    VERIFY.check_contract(model, self.directory)
                path.unlink()


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""Registry verification boundary tests; all Docker and network calls are mocked."""

import copy
import importlib.util
import io
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("verify_registry", pathlib.Path(__file__).with_name("verify_registry.py"))
registry = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(registry)
VERSION = registry.project_version(registry.ROOT)
REPOSITORIES = ["ccr.ccs.tencentyun.com/ymoj/ymcloud", "docker.io/yemiao1/ymoj"]
UPSTREAMS = {"mysql": "8.4", "redis": "7.4-alpine", "rabbitmq": "4.1-alpine"}


def digest(number):
    return "sha256:" + format(number, "064x")


def image_lock():
    images = {}
    for index, service in enumerate(registry.SERVICES, 1):
        record = {"imageId": digest(index)}
        if service in registry.PROJECT_SERVICES:
            record.update(version=VERSION, versionTag=f"ymcloud-{service}:{VERSION}",
                          exporterDigest=digest(index), digestKind="docker-config")
        else:
            record["source"] = f"{service}:{UPSTREAMS[service]}@{digest(100 + index)}"
        images[service] = record
    return {"version": VERSION, "platform": "linux/amd64", "images": images}


def manifest(image_id, manifest_digest=digest(1000), kind="SchemaV2Manifest", architecture="amd64"):
    media_type = registry.MANIFEST_TYPES[kind]
    return {"Descriptor": {"digest": manifest_digest, "mediaType": media_type,
                           "platform": {"os": "linux", "architecture": architecture}},
            kind: {"schemaVersion": 2, "mediaType": media_type, "config": {"digest": image_id}, "layers": []}}


class RegistryTests(unittest.TestCase):
    def setUp(self):
        registry.ARTIFACTS.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="registry-tests-", dir=registry.ARTIFACTS)
        self.addCleanup(self.temporary.cleanup)
        self.directory = pathlib.Path(self.temporary.name)
        self.lock = self.directory / "images.lock.json"
        self.values = image_lock()
        self.lock.write_text(json.dumps(self.values), encoding="utf-8")

    def test_seven_tags_preserve_project_and_middleware_versions(self):
        version, images = registry.load_lock(self.lock)
        self.assertEqual(VERSION, version)
        self.assertEqual(set(registry.SERVICES), set(images))
        for service, record in images.items():
            self.assertEqual(service + "-" + UPSTREAMS.get(service, VERSION), record["tag"])
            self.assertEqual(self.values["images"][service]["imageId"], record["imageId"])

    def test_lock_rejects_missing_service_platform_version_and_floating_middleware(self):
        changes = [lambda lock: lock["images"].pop("worker"),
                   lambda lock: lock.update(platform="linux/arm64"),
                   lambda lock: lock.update(version="v4.0.0"),
                   lambda lock: lock.update(version="3.0.0"),
                   lambda lock: lock["images"]["web"].update(version="3.0.0"),
                   lambda lock: lock["images"]["web"].update(imageId="ymcloud-web:4.0.0"),
                   lambda lock: lock["images"]["redis"].update(source="redis:7.4-alpine"),
                   lambda lock: lock["images"]["mysql"].update(source="mysql:latest@" + digest(99)),
                   lambda lock: lock["images"]["mysql"].update(source="mysql:8.0@" + digest(99))]
        for change in changes:
            values = copy.deepcopy(self.values)
            change(values)
            self.lock.write_text(json.dumps(values), encoding="utf-8")
            with self.subTest(change=change):
                with self.assertRaises(ValueError):
                    registry.load_lock(self.lock)

    def test_repository_is_full_untagged_and_duplicate_hub_aliases_are_rejected(self):
        self.assertEqual(REPOSITORIES, registry.repositories(REPOSITORIES))
        self.assertEqual("localhost:5000/team/ymcloud", registry.repository_name("localhost:5000/team/ymcloud"))
        for value in ("yemiao1/ymoj", "https://docker.io/yemiao1/ymoj", "user:password@host/repo",
                      "docker.io/yemiao1/ymoj:4.0.0", "docker.io/yemiao1/ymoj@" + digest(1),
                      "docker.io/team/../ymoj", "docker.io/team/repo/", "localhost:99999/team/repo"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    registry.repository_name(value)
        with self.assertRaisesRegex(ValueError, "different"):
            registry.repositories([REPOSITORIES[1], "index.docker.io/yemiao1/ymoj"])
        with self.assertRaisesRegex(ValueError, "Exactly two"):
            registry.repositories(REPOSITORIES[:1])

    def test_report_boundary_rejects_escape_wrong_extension_and_symlinks(self):
        self.assertEqual(self.directory / "report.json", registry.confined_report(self.directory / "report.json"))
        for path in (registry.ROOT / "report.json", registry.ARTIFACTS / "../report.json",
                     self.directory / "report.txt", self.directory):
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    registry.confined_report(path)
        with mock.patch.object(pathlib.Path, "is_symlink", return_value=True):
            with self.assertRaisesRegex(ValueError, "symlinks"):
                registry.confined_report(self.directory / "report.json")

    def test_anonymous_config_prevents_native_helper_and_environment_auth_fallback(self):
        inherited = {"DOCKER_CONFIG": "private-config", "DOCKER_AUTH_CONFIG": "private-auth",
                     "DOCKER_CONTEXT": "private-context", "DOCKER_HOST": "private-host",
                     "DOCKER_TLS_VERIFY": "1", "DOCKER_CERT_PATH": "private-cert",
                     "DOCKER_CUSTOM_HEADERS": "Authorization=private-token",
                     "HTTP_PROXY": "http://127.0.0.1:7897", "HTTPS_PROXY": "http://127.0.0.1:7897",
                     "NO_PROXY": "localhost", "PATH": "existing-path"}
        with registry.anonymous_client(REPOSITORIES, inherited) as (docker, environment):
            directory = pathlib.Path(docker[2])
            self.assertEqual(["docker", "--config"], docker[:2])
            self.assertTrue(directory.is_relative_to(registry.ARTIFACTS))
            config = json.loads((directory / "config.json").read_text(encoding="utf-8"))
            self.assertEqual({"auths"}, set(config))
            self.assertTrue(config["auths"])
            self.assertTrue(all(value == {} for value in config["auths"].values()))
            self.assertIn("https://index.docker.io/v1/", config["auths"])
            self.assertEqual(str(directory), environment.pop("DOCKER_CONFIG"))
            self.assertFalse(registry.DOCKER_ENV & environment.keys())
            self.assertEqual({key: inherited[key] for key in ("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "PATH")}, environment)
            self.assertNotIn("private-", (directory / "config.json").read_text(encoding="utf-8"))
        self.assertFalse(directory.exists())
        self.assertEqual("private-config", inherited["DOCKER_CONFIG"])

    def test_context_endpoint_uses_existing_context_and_host_precedence(self):
        endpoint = "npipe:////./pipe/dockerDesktopLinuxEngine"
        environment = {"DOCKER_CONTEXT": "desktop-linux", "DOCKER_HOST": "unix:///ignored.sock"}
        with mock.patch.object(registry, "command", return_value=json.dumps({"Host": endpoint})) as command:
            self.assertEqual(endpoint, registry.current_engine_endpoint(environment))
        self.assertEqual(environment, command.call_args.args[1])
        with mock.patch.object(registry, "command") as command:
            self.assertEqual("unix:///var/run/docker.sock",
                             registry.current_engine_endpoint({"DOCKER_HOST": "unix:///var/run/docker.sock"}))
        command.assert_not_called()
        with self.assertRaisesRegex(ValueError, "local Unix socket"):
            registry.current_engine_endpoint({"DOCKER_HOST": "ssh://remote-private-host"})

    def test_schema2_oci_and_multi_platform_manifest_use_descriptor_digest(self):
        image_id = digest(1)
        for kind in registry.MANIFEST_TYPES:
            with self.subTest(kind=kind):
                value = manifest(image_id, kind=kind)
                self.assertEqual(digest(1000), registry.manifest_record(json.dumps(value), image_id))
                values = [manifest(digest(9), digest(99), kind, "arm64"), value]
                self.assertEqual(digest(1000), registry.manifest_record(json.dumps(values), image_id))

    def test_wrong_config_platform_ambiguous_or_fake_descriptor_are_rejected(self):
        valid = manifest(digest(1))
        invalid = [manifest(digest(2)), manifest(digest(1), digest(1)),
                   manifest(digest(1), architecture="arm64"), [valid, valid], {}, [],
                   {**valid, "OCIManifest": valid["SchemaV2Manifest"]}]
        missing = copy.deepcopy(valid)
        missing["Descriptor"].pop("digest")
        invalid.append(missing)
        for value in invalid:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    registry.manifest_record(json.dumps(value), digest(1))

    def test_pulled_image_checks_config_platform_and_project_label(self):
        image = {"Id": digest(1), "Os": "linux", "Architecture": "amd64",
                 "Config": {"Labels": {"org.opencontainers.image.version": VERSION}}}
        registry.verify_local_image(json.dumps([image]), digest(1), VERSION)
        for change in ({"Id": digest(2)}, {"Os": "windows"}, {"Architecture": "arm64"}, {"Config": {}}):
            with self.subTest(change=change):
                with self.assertRaises(ValueError):
                    registry.verify_local_image(json.dumps([{**image, **change}]), digest(1), VERSION)

    def fake_command(self, arguments, environment, label, timeout=120):
        self.assertIn("--config", arguments)
        self.assertNotIn("DOCKER_AUTH_CONFIG", environment)
        self.assertNotIn("@", arguments[-1])
        tag = arguments[-1].rsplit(":", 1)[1]
        service = next(name for name in registry.SERVICES if tag.startswith(name + "-"))
        index = registry.SERVICES.index(service) + 1
        if "manifest" in arguments:
            # Equivalent image content may have different registry manifests.
            offset = 2000 if arguments[-1].startswith(REPOSITORIES[1]) else 1000
            return json.dumps(manifest(digest(index), digest(offset + index)))
        self.assertIn("--host", arguments)
        self.assertEqual("unix:///var/run/docker.sock", arguments[arguments.index("--host") + 1])
        if "pull" in arguments:
            self.assertEqual(["--platform", "linux/amd64"], arguments[-3:-1])
            return "Pulled successfully"
        return json.dumps([{"Id": digest(index), "Os": "linux", "Architecture": "amd64",
                            "Config": {"Labels": {"org.opencontainers.image.version": VERSION}}}])

    def test_anonymous_two_repository_pulls_start_after_all_manifest_checks(self):
        environment = {"DOCKER_HOST": "unix:///var/run/docker.sock", "DOCKER_AUTH_CONFIG": "private"}
        with mock.patch.object(registry, "command", side_effect=self.fake_command) as command:
            report = registry.verify(REPOSITORIES, self.lock, pull=True, environment=environment)
        self.assertTrue(report["ok"] and report["repositorySwitchSafe"] and report["anonymousPullVerified"])
        calls = [call.args[0] for call in command.call_args_list]
        self.assertEqual(42, len(calls))
        self.assertTrue(all("manifest" in arguments for arguments in calls[:14]))
        self.assertEqual(1, len({arguments[2] for arguments in calls}))
        self.assertFalse(any("login" in arguments or "rm" in arguments for arguments in calls))
        for service, record in report["images"].items():
            self.assertNotIn("manifestDigest", record)
            self.assertEqual(set(REPOSITORIES), set(record["repositories"]))
            self.assertEqual(2, len({check["manifestDigest"] for check in record["repositories"].values()}))
            for repository, check in record["repositories"].items():
                self.assertEqual(repository + ":" + record["tag"], check["tagReference"])
                self.assertEqual(repository + "@" + check["manifestDigest"], check["reference"])
                self.assertNotEqual(record["imageId"], check["manifestDigest"])
                self.assertEqual(record["imageId"], check["configId"])
                self.assertTrue(check["anonymousManifest"] and check["anonymousPull"])

    def test_tag_pull_refreshes_same_version_old_local_content_before_inspection(self):
        _, expected = registry.load_lock(self.lock)
        wanted = {repository + ":" + record["tag"]: record["imageId"]
                  for repository in REPOSITORIES for record in expected.values()}
        local = {reference: digest(9999) for reference in wanted}
        inspected = []

        def existing_tags(arguments, environment, label, timeout=120):
            raw = self.fake_command(arguments, environment, label, timeout)
            reference = arguments[-1]
            if "pull" in arguments:
                self.assertEqual(digest(9999), local[reference])
                local[reference] = wanted[reference]
            elif "image" in arguments:
                inspected.append(reference)
                value = json.loads(raw)
                value[0]["Id"] = local[reference]
                return json.dumps(value)
            return raw

        with mock.patch.object(registry, "command", side_effect=existing_tags):
            report = registry.verify(REPOSITORIES, self.lock, pull=True,
                                     environment={"DOCKER_HOST": "unix:///var/run/docker.sock"})
        self.assertTrue(report["anonymousPullVerified"])
        self.assertEqual(wanted, local)
        self.assertEqual(set(wanted), set(inspected))
        self.assertEqual(14, len(inspected))

    def test_tag_drift_between_manifest_check_and_pull_fails_with_unchanged_oci_version(self):
        for service in ("web", "mysql"):
            with self.subTest(service=service):
                selected_tag = service + "-" + UPSTREAMS.get(service, VERSION)
                changed_reference = REPOSITORIES[1] + ":" + selected_tag
                pulled = set()

                def moved_tag(arguments, environment, label, timeout=120):
                    raw = self.fake_command(arguments, environment, label, timeout)
                    reference = arguments[-1]
                    if "pull" in arguments:
                        pulled.add(reference)
                    elif "image" in arguments and reference == changed_reference:
                        self.assertIn(reference, pulled)
                        value = json.loads(raw)
                        value[0]["Id"] = digest(9999)
                        # Matching the version label alone must never hide content drift.
                        self.assertEqual(VERSION, value[0]["Config"]["Labels"]["org.opencontainers.image.version"])
                        return json.dumps(value)
                    return raw

                with mock.patch.object(registry, "command", side_effect=moved_tag):
                    with self.assertRaisesRegex(ValueError, "Pulled image differs"):
                        registry.verify(REPOSITORIES, self.lock, pull=True,
                                        environment={"DOCKER_HOST": "unix:///var/run/docker.sock"})

    def test_manifest_only_does_not_access_engine_or_claim_pull_success(self):
        with (mock.patch.object(registry, "command", side_effect=self.fake_command) as command,
              mock.patch.object(registry, "current_engine_endpoint") as engine):
            report = registry.verify(REPOSITORIES, self.lock, environment={})
        engine.assert_not_called()
        self.assertEqual(14, command.call_count)
        self.assertTrue(report["anonymousManifestVerified"])
        self.assertFalse(report["anonymousPullVerified"])
        self.assertTrue(all(check["anonymousPull"] is None for record in report["images"].values()
                            for check in record["repositories"].values()))

    def test_repository_config_mismatch_prevents_all_pulls(self):
        calls = []

        def divergent(arguments, environment, label, timeout=120):
            calls.append(arguments)
            value = json.loads(self.fake_command(arguments, environment, label, timeout))
            if arguments[-1].startswith(REPOSITORIES[1]):
                value["SchemaV2Manifest"]["config"]["digest"] = digest(9999)
            return json.dumps(value)

        with mock.patch.object(registry, "command", side_effect=divergent):
            with self.assertRaisesRegex(ValueError, "config digest differs"):
                registry.verify(REPOSITORIES, self.lock, pull=True,
                                environment={"DOCKER_HOST": "unix:///var/run/docker.sock"})
        self.assertTrue(all("manifest" in arguments for arguments in calls))

    def test_each_repository_pull_must_preserve_config_and_project_oci_version(self):
        for repository in REPOSITORIES:
            for mutation in ("config", "version"):
                with self.subTest(repository=repository, mutation=mutation):
                    def invalid(arguments, environment, label, timeout=120):
                        raw = self.fake_command(arguments, environment, label, timeout)
                        if "image" in arguments and arguments[-1].startswith(repository):
                            value = json.loads(raw)
                            if mutation == "config":
                                value[0]["Id"] = digest(9999)
                            else:
                                value[0]["Config"]["Labels"]["org.opencontainers.image.version"] = "3.0.0"
                            return json.dumps(value)
                        return raw

                    with mock.patch.object(registry, "command", side_effect=invalid):
                        with self.assertRaises(ValueError):
                            registry.verify(REPOSITORIES, self.lock, pull=True,
                                            environment={"DOCKER_HOST": "unix:///var/run/docker.sock"})

    def test_command_errors_and_invalid_json_never_expose_raw_credentials(self):
        secret = "proxy-user:private-password"
        failures = [subprocess.CompletedProcess([], 1, secret, secret),
                    subprocess.TimeoutExpired([], 1, output=secret, stderr=secret), OSError(secret)]
        for failure in failures:
            options = {"side_effect": failure} if isinstance(failure, Exception) else {"return_value": failure}
            with mock.patch.object(registry.subprocess, "run", **options):
                with self.assertRaises(ValueError) as captured:
                    registry.command(["docker"], {}, "Anonymous check")
            self.assertNotIn(secret, str(captured.exception))
        with self.assertRaises(ValueError) as captured:
            registry.parse_json(secret, "Manifest")
        self.assertNotIn(secret, str(captured.exception))

    def test_main_replaces_stale_success_with_sanitized_failure_and_protects_lock(self):
        report_path = self.directory / "report.json"
        report_path.write_text('{"ok":true}', encoding="utf-8")
        arguments = ["--local-lock", str(self.lock), "--report", str(report_path)]
        for repository in REPOSITORIES:
            arguments.extend(["--repository", repository])
        with mock.patch.object(registry, "verify", side_effect=ValueError("Digest mismatch")):
            with self.assertRaisesRegex(ValueError, "Digest mismatch"):
                registry.main(arguments)
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertFalse(report["ok"])
        self.assertEqual("verification-failed", report["status"])
        self.assertEqual(self.values, json.loads(self.lock.read_text(encoding="utf-8")))
        arguments[3] = str(self.lock)
        with self.assertRaisesRegex(ValueError, "must not replace"):
            registry.main(arguments)
        self.assertEqual(self.values, json.loads(self.lock.read_text(encoding="utf-8")))

    def test_main_writes_successful_report_and_short_summary(self):
        report_path = self.directory / "report.json"
        arguments = ["--local-lock", str(self.lock), "--report", str(report_path)]
        for repository in REPOSITORIES:
            arguments.extend(["--repository", repository])
        with (mock.patch.object(registry, "command", side_effect=self.fake_command),
              mock.patch.object(registry.sys, "stdout", new_callable=io.StringIO) as output):
            registry.main(arguments)
        self.assertTrue(json.loads(report_path.read_text(encoding="utf-8"))["ok"])
        self.assertEqual(7, json.loads(output.getvalue())["services"])
        self.assertFalse(list(self.directory.glob(".registry-report-*.tmp")))


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""本地部署目录的内容边界与不可变镜像替换回归，不依赖 Docker 或外部服务。"""

import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
from unittest import mock
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[5]
SPEC = importlib.util.spec_from_file_location("ymcloud_release", ROOT / "docker/release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
IMAGE_ID = "sha256:" + "a" * 64
MANIFEST_DIGEST = "sha256:" + "b" * 64
DEFAULT_REPOSITORY = "docker.io/yemiao1/ymoj"
ALTERNATIVE_REPOSITORY = "ccr.ccs.tencentyun.com/ymoj/ymcloud"
VERSION = release.project_version()


def local_records():
    return {name: {"imageId": "sha256:" + format(index + 1, "064x")}
            for index, name in enumerate(release.SERVICES)}


def compose_template(repository=DEFAULT_REPOSITORY, version=VERSION):
    tags = {name: f"{name}-{version}" for name in ("web", "worker", "go-judge", "frontend")}
    tags.update({"mysql": "mysql-8.4", "redis": "redis-7.4-alpine", "rabbitmq": "rabbitmq-4.1-alpine"})
    lines = ["services:"]
    for name in release.COMPOSE_SERVICES:
        service = "web" if name == "preflight" else name
        lines.extend([f"  {name}:",
                      f"    image: ${{IMAGE_REPOSITORY:-{repository}}}:{tags[service]}",
                      "    pull_policy: missing"])
        if name == "mysql":
            lines.extend(["    volumes:", "      - mysql-data:/var/lib/mysql", "      - type: bind",
                          "        source: ../sql/ymcloud.sql",
                          "        target: /docker-entrypoint-initdb.d/01-ymcloud.sql",
                          "        read_only: true", "        bind:", "          create_host_path: false"])
    lines.extend(["x-public-defaults:", "  password: ${PASSWORD:-public-test-default}", "  mail: ${MAIL_PASS:-}"])
    return "\n".join(lines) + "\n"


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        artifacts = ROOT / "target/phase3-verification"
        artifacts.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="release-test-", dir=artifacts)
        self.directory = pathlib.Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def write_jar(self, entries, version=VERSION, include_metadata=True):
        target = self.directory / "ymcloud-web.jar"
        with zipfile.ZipFile(target, "w") as archive:
            for entry in entries:
                archive.writestr(entry, "公开测试配置")
            if include_metadata:
                archive.writestr("META-INF/maven/com.ymcloud.web/ymcloud-web/pom.properties",
                                 f"artifactId=ymcloud-web\ngroupId=com.ymcloud.web\nversion={version}\n")
        return target

    def test_private_profiles_are_rejected_in_every_supported_format(self):
        for profile in ("dev", "prod"):
            for extension in ("yml", "yaml", "properties"):
                with self.subTest(profile=profile, extension=extension):
                    jar = self.write_jar([
                        "BOOT-INF/classes/application.yml",
                        f"BOOT-INF/classes/application-{profile}.{extension}",
                    ])
                    with self.assertRaisesRegex(ValueError, "私有环境配置"):
                        release.validate_jar(jar)

    def test_public_executable_jar_is_accepted(self):
        release.validate_jar(self.write_jar(["BOOT-INF/classes/application.yml"]))

    def test_old_or_unversioned_jar_cannot_be_retagged_for_current_release(self):
        with self.assertRaisesRegex(ValueError, "版本与项目版本"):
            release.validate_jar(self.write_jar(["BOOT-INF/classes/application.yml"], version="0.0.0"), VERSION)
        with self.assertRaisesRegex(ValueError, "版本元数据"):
            release.validate_jar(self.write_jar(["BOOT-INF/classes/application.yml"], include_metadata=False), VERSION)

    def test_jar_version_defaults_to_single_project_version_helper(self):
        jar = self.write_jar(["BOOT-INF/classes/application.yml"], version="8.7.6-rc.1")
        with mock.patch.object(release, "project_version", return_value="8.7.6-rc.1") as version:
            release.validate_jar(jar)
        version.assert_called_once_with()

    def test_frontend_private_files_and_source_maps_are_rejected(self):
        (self.directory / "index.html").write_text("<html></html>", encoding="utf-8")
        (self.directory / "version.json").write_text(json.dumps({"version": VERSION}), encoding="utf-8")
        for name in (".env.production", "app.js.map"):
            with self.subTest(name=name):
                path = self.directory / name
                path.write_text("{}", encoding="utf-8")
                with self.assertRaises(ValueError):
                    release.validate_frontend(self.directory)
                path.unlink()
        release.validate_frontend(self.directory)

    def test_frontend_requires_matching_build_version(self):
        (self.directory / "index.html").write_text("<html></html>", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "缺少 version.json"):
            release.validate_frontend(self.directory, VERSION)
        marker = self.directory / "version.json"
        marker.write_text(json.dumps({"version": "0.0.0"}), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "制品版本与项目版本"):
            release.validate_frontend(self.directory, VERSION)
        marker.write_text(json.dumps({"version": "8.7.6-rc.1"}), encoding="utf-8")
        with mock.patch.object(release, "project_version", return_value="8.7.6-rc.1") as version:
            release.validate_frontend(self.directory)
        version.assert_called_once_with()

    def test_project_image_build_rejects_incorrect_oci_version_label(self):
        (self.directory / "web.iid").write_text(IMAGE_ID, encoding="utf-8")
        (self.directory / "web.metadata.json").write_text(json.dumps({
            "containerimage.config.digest": IMAGE_ID,
            "containerimage.digest": IMAGE_ID,
        }), encoding="utf-8")
        with mock.patch.object(release, "execute") as execute:
            with mock.patch.object(release, "inspect_image", return_value={"imageId": IMAGE_ID, "version": "0.0.0"}):
                with self.assertRaisesRegex(ValueError, "OCI 版本标签"):
                    release.build_image("web", "web", self.directory, version=VERSION)
        command = execute.call_args.args[0]
        self.assertIn(f"ymcloud-web:{VERSION}", command)
        self.assertIn(f"YMCLOUD_VERSION={VERSION}", command)

    def test_floating_base_tags_are_rejected(self):
        path = self.directory / "bases.json"
        values = {name: "example/" + name + "@" + IMAGE_ID for name in release.BASE_SERVICES}
        path.write_text(json.dumps(values), encoding="utf-8")
        self.assertEqual(values, release.load_base_images(path, {}))
        with self.assertRaisesRegex(ValueError, "mysql"):
            release.load_base_images(path, {"mysql": "mysql:latest"})

    def test_cached_digest_lookup_removes_tag_and_preserves_registry_port(self):
        repositories = (
            ("mysql:8.4", "mysql"),
            ("registry.example:5000/team/mysql:8.4", "registry.example:5000/team/mysql"),
            ("registry.example:5000/team/mysql", "registry.example:5000/team/mysql"),
        )
        for source, repository in repositories:
            with self.subTest(source=source):
                reference = source + "@" + IMAGE_ID
                local_reference = repository + "@" + IMAGE_ID
                inspected = {"imageId": IMAGE_ID, "repoDigests": [local_reference]}
                with mock.patch.object(release, "inspect_image", return_value=inspected) as inspect:
                    with mock.patch.object(release, "execute") as execute:
                        record = release.verify_base_image(reference)
                inspect.assert_called_once_with(local_reference)
                execute.assert_not_called()
                self.assertEqual(reference, record["source"])

    def test_both_registry_tag_templates_render_distinct_local_ids(self):
        records = local_records()
        for repository in (DEFAULT_REPOSITORY, ALTERNATIVE_REPOSITORY):
            with self.subTest(repository=repository):
                rendered = release.render_compose(compose_template(repository), records)
                for name in release.COMPOSE_SERVICES:
                    service = "web" if name == "preflight" else name
                    self.assertIn(f"  {name}:\n    image: {records[service]['imageId']}\n    pull_policy: never", rendered)
                self.assertNotIn("IMAGE_REPOSITORY", rendered)
                self.assertNotIn("@sha256:", rendered)
                self.assertNotIn("pull_policy: missing", rendered)
                self.assertIn("source: ./initdb/ymcloud.sql", rendered)
                self.assertIn("create_host_path: false", rendered)
                self.assertNotIn("source: ../sql/ymcloud.sql", rendered)

    def test_local_renderer_rejects_missing_duplicate_or_mismatched_services(self):
        records = local_records()
        template = compose_template()
        first_image = next(line for line in template.splitlines() if line.startswith("    image:"))
        with self.assertRaisesRegex(ValueError, "services"):
            release.render_compose("services: {}", records)
        for changed in (template.replace("  frontend:\n", "  web:\n"),
                        template.replace("  frontend:\n", ""),
                        template.replace("  frontend:\n", "  unknown:\n"),
                        template.replace(":worker-" + VERSION, ":web-" + VERSION),
                        template.replace(first_image + "\n", "", 1),
                        template.replace(first_image, first_image + "\n" + first_image, 1),
                        template.replace("    pull_policy: missing\n", "", 1),
                        template.replace("    pull_policy: missing", "    pull_policy: missing\n    pull_policy: missing", 1),
                        template.replace("pull_policy: missing", "pull_policy: never", 1),
                        template.replace(DEFAULT_REPOSITORY, DEFAULT_REPOSITORY + ":4.0.0"),
                        template.replace(DEFAULT_REPOSITORY, IMAGE_ID),
                        template.replace(DEFAULT_REPOSITORY, DEFAULT_REPOSITORY + "/"),
                        template.replace(DEFAULT_REPOSITORY, DEFAULT_REPOSITORY + "//extra"),
                        template.replace(DEFAULT_REPOSITORY, ALTERNATIVE_REPOSITORY, 1)):
            with self.subTest(template=changed[:30]):
                with self.assertRaises(ValueError):
                    release.render_compose(changed, records)
        mismatch = template.replace(":web-" + VERSION, ":worker-" + VERSION, 1)
        with self.assertRaisesRegex(ValueError, "preflight"):
            release.render_compose(mismatch, records)
        for value in ("ymcloud-web:latest", "docker.io/unit-test/web@" + MANIFEST_DIGEST, None):
            invalid = local_records()
            invalid["web"]["imageId"] = value
            with self.assertRaisesRegex(ValueError, "完整镜像 ID"):
                release.render_compose(template, invalid)
        missing = local_records()
        missing.pop("worker")
        with self.assertRaisesRegex(ValueError, "七个发布镜像"):
            release.render_compose(template, missing)

    def test_source_template_rejects_floating_wrong_version_and_non_tag_images(self):
        template = compose_template()
        records = local_records()
        for service in release.COMPOSE_SERVICES:
            variant = "web" if service == "preflight" else service
            tag = (f"{variant}-{VERSION}" if variant in ("web", "worker", "go-judge", "frontend")
                   else {"mysql": "mysql-8.4", "redis": "redis-7.4-alpine",
                         "rabbitmq": "rabbitmq-4.1-alpine"}[variant])
            reference = f"${{IMAGE_REPOSITORY:-{DEFAULT_REPOSITORY}}}:{tag}"
            original = f"  {service}:\n    image: {reference}"
            for replacement in (f"${{IMAGE_REPOSITORY:-{DEFAULT_REPOSITORY}}}:latest",
                                f"${{IMAGE_REPOSITORY:-{DEFAULT_REPOSITORY}}}:{variant}-0.0.0",
                                f"${{IMAGE_REPOSITORY:-{DEFAULT_REPOSITORY}}}@{MANIFEST_DIGEST}",
                                reference + "@" + MANIFEST_DIGEST, IMAGE_ID,
                                f"{DEFAULT_REPOSITORY}:{tag}"):
                with self.subTest(service=service, image=replacement):
                    changed = template.replace(original, f"  {service}:\n    image: {replacement}", 1)
                    with self.assertRaisesRegex(ValueError, "版本标签"):
                        release.render_compose(changed, records)

    def test_renderer_uses_single_project_version_and_keeps_base_tags_fixed(self):
        next_version = "8.7.6-rc.1"
        records = local_records()
        with mock.patch.object(release, "project_version", return_value=next_version) as version:
            with self.assertRaisesRegex(ValueError, "构建版本一致"):
                release.render_compose(compose_template(), records)
            release.render_compose(compose_template(version=next_version), records)
        self.assertEqual([mock.call(), mock.call()], version.call_args_list)
        with mock.patch.object(release, "project_version", side_effect=AssertionError("must use captured version")):
            release.render_compose(compose_template(version=next_version), records, next_version)
        for old, new in (("mysql-8.4", "mysql-8.4.1"), ("redis-7.4-alpine", "redis-7.4"),
                         ("rabbitmq-4.1-alpine", "rabbitmq-4.1")):
            with self.subTest(service=old):
                with self.assertRaisesRegex(ValueError, "版本标签"):
                    release.render_compose(compose_template().replace(old, new), records)

    def test_local_renderer_preserves_only_the_declared_readonly_seed_mount(self):
        template = compose_template()
        for old, new in (("source: ../sql/ymcloud.sql", "source: /private/ymcloud.sql"),
                         ("source: ../sql/ymcloud.sql", "source: ../sql/ymcloud.sql\n        source: ../sql/ymcloud.sql"),
                         ("read_only: true", "read_only: false"),
                         ("create_host_path: false", "create_host_path: true"),
                         ("target: /docker-entrypoint-initdb.d/01-ymcloud.sql", "target: /private"),
                         ("      - type: bind", "      - type: volume")):
            with self.subTest(field=old.split(":")[0]):
                with self.assertRaises(ValueError):
                    release.render_compose(template.replace(old, new), local_records())

    def deployment_source(self):
        source = self.directory / "source"
        deployment = source / "deploy"
        deployment.mkdir(parents=True)
        (source / "sql").mkdir()
        (source / "sql/ymcloud.sql").write_text("-- 空库初始化\n", encoding="utf-8")
        (deployment / "compose.yaml").write_text(compose_template(), encoding="utf-8")
        (deployment / "README.md").write_text("# 默认密码仅用于快速测试\n", encoding="utf-8")
        (deployment / ".env").write_text(
            "IMAGE_REPOSITORY=" + DEFAULT_REPOSITORY + "\nPASSWORD=public-test-default\nMAIL_PASS=\n", encoding="utf-8")
        return source

    def test_deployment_copy_contains_only_public_templates_and_unique_seed(self):
        source = self.deployment_source()
        deployment = source / "deploy"
        (deployment / ".env.example").write_text("PASSWORD=obsolete-value\n", encoding="utf-8")
        (deployment / "preflight.py").write_text("# 启动配置检查\n", encoding="utf-8")
        (deployment / "private.yaml").write_text("password: private-value\n", encoding="utf-8")
        records = {name: {"imageId": IMAGE_ID} for name in release.SERVICES}
        destination = self.directory / "output"
        with mock.patch.object(release, "ROOT", source):
            release.copy_deployment(destination, records)
            self.assertEqual("IMAGE_REPOSITORY=" + DEFAULT_REPOSITORY + "\nPASSWORD=public-test-default\nMAIL_PASS=\n",
                             (destination / ".env").read_text(encoding="utf-8"))
            compose = (destination / "compose.yaml").read_text(encoding="utf-8")
            self.assertIn("source: ./initdb/ymcloud.sql", compose)
            self.assertNotIn("pull_policy: missing", compose)
            self.assertEqual({".env", "compose.yaml", "README.md", "initdb/ymcloud.sql"}, {
                path.relative_to(destination).as_posix() for path in destination.rglob("*") if path.is_file()
            })
            with self.assertRaisesRegex(ValueError, "非空"):
                release.copy_deployment(destination, records)

    def test_private_source_environment_is_rejected_before_creating_deployment(self):
        source = self.deployment_source()
        secret = "private-deployment-secret-that-must-stay-local"
        (source / "deploy/.env").write_text("PASSWORD=" + secret + "\nMAIL_PASS=\n", encoding="utf-8")
        destination = self.directory / "output"
        records = {name: {"imageId": IMAGE_ID} for name in release.SERVICES}
        with mock.patch.object(release, "ROOT", source):
            with self.assertRaisesRegex(ValueError, "拒绝复制私有配置") as captured:
                release.copy_deployment(destination, records)
        self.assertNotIn(secret, str(captured.exception))
        self.assertFalse(destination.exists())

    def test_source_tag_version_mismatch_never_creates_a_local_deployment(self):
        source = self.deployment_source()
        destination = self.directory / "output"
        with mock.patch.object(release, "ROOT", source):
            with self.assertRaisesRegex(ValueError, "构建版本一致"):
                release.copy_deployment(destination, local_records(), "0.0.0")
        self.assertFalse(destination.exists())

    def test_environment_defaults_reject_missing_unknown_duplicate_or_ambiguous_values(self):
        compose = "password: ${PASSWORD:-public-test-default}\nmail: ${MAIL_PASS:-}\n"
        environments = (
            "PASSWORD=public-test-default\n",
            "PASSWORD=public-test-default\nMAIL_PASS=\nEXTRA=private\n",
            "PASSWORD=public-test-default\nMAIL_PASS=\nMAIL_PASS=\n",
            "PASSWORD='public-test-default'\nMAIL_PASS=\n",
        )
        for environment in environments:
            with self.subTest(environment=environment):
                with self.assertRaises(ValueError):
                    release.validate_deployment_environment(environment, compose)
        with self.assertRaisesRegex(ValueError, "默认值不一致"):
            release.validate_deployment_environment(
                "PASSWORD=public-test-default\nMAIL_PASS=\n",
                compose + "another: ${PASSWORD:-different-default}\n",
            )

    def write_image_metadata(self, service, config_digest=IMAGE_ID):
        (self.directory / f"{service}.iid").write_text(IMAGE_ID, encoding="utf-8")
        (self.directory / f"{service}.metadata.json").write_text(json.dumps({
            "containerimage.config.digest": config_digest,
            "containerimage.digest": MANIFEST_DIGEST,
        }), encoding="utf-8")

    def test_project_build_verifies_version_tag_and_runtime_before_recording_lock(self):
        for service in release.PROJECT_SERVICES:
            with self.subTest(service=service):
                self.write_image_metadata(service)
                inspected = {"imageId": IMAGE_ID, "version": VERSION}
                with mock.patch.object(release, "execute") as execute:
                    with mock.patch.object(release, "inspect_image", return_value=inspected) as inspect:
                        target = "runtime" if service == "go-judge" else service
                        record = release.build_image(service, target, self.directory,
                                                     "nginx:1.28-alpine@" + IMAGE_ID, VERSION)
                self.assertEqual(IMAGE_ID, record["imageId"])
                self.assertEqual(VERSION, record["version"])
                self.assertEqual(f"ymcloud-{service}:{VERSION}", record["versionTag"])
                self.assertEqual(MANIFEST_DIGEST, record["exporterDigest"])
                self.assertEqual("image-manifest", record["digestKind"])
                self.assertEqual([mock.call(IMAGE_ID), mock.call(record["versionTag"])], inspect.call_args_list)
                commands = [call.args[0] for call in execute.call_args_list]
                self.assertEqual(2, len(commands))
                self.assertEqual(["docker", "buildx", "build"], commands[0][:3])
                self.assertIn(record["versionTag"], commands[0])
                self.assertEqual(["docker", "run", "--rm", "--network", "none"], commands[1][:5])
                self.assertIn(IMAGE_ID, commands[1])
                if service == "frontend":
                    self.assertEqual([IMAGE_ID, "-t"], commands[1][-2:])
                else:
                    self.assertIn("/opt/ymcloud/verify_versions.py", commands[1])

    def test_build_stops_when_version_tag_resolves_to_other_content(self):
        self.write_image_metadata("web")
        inspected = [
            {"imageId": IMAGE_ID, "version": VERSION},
            {"imageId": "sha256:" + "b" * 64, "version": VERSION},
        ]
        with mock.patch.object(release, "execute") as execute:
            with mock.patch.object(release, "inspect_image", side_effect=inspected):
                with self.assertRaisesRegex(ValueError, "版本标签.*内容不一致"):
                    release.build_image("web", "web", self.directory, version=VERSION)
        execute.assert_called_once()

    def test_build_stops_when_config_digest_differs_from_actual_image(self):
        self.write_image_metadata("web", config_digest="sha256:" + "b" * 64)
        with mock.patch.object(release, "execute") as execute:
            with mock.patch.object(release, "inspect_image", return_value={"imageId": IMAGE_ID, "version": VERSION}):
                with self.assertRaisesRegex(ValueError, "构建摘要与实际镜像不一致"):
                    release.build_image("web", "web", self.directory, version=VERSION)
        execute.assert_called_once()

    def test_main_creates_only_local_deployment_files_and_keeps_upstream_references(self):
        source = self.deployment_source()
        artifacts = self.directory / "artifacts"
        upstreams = {"mysql": "8.4", "redis": "7.4-alpine", "rabbitmq": "4.1-alpine", "nginx": "1.28-alpine"}
        references = {name: f"{name}:{version}@{IMAGE_ID}" for name, version in upstreams.items()}

        def built_image(service, target, directory, nginx_image, version):
            self.assertEqual({"go-judge": "runtime", "worker": "worker", "web": "web", "frontend": None}[service], target)
            self.assertEqual(artifacts / "full-stack-build", directory)
            self.assertEqual(references["nginx"], nginx_image)
            self.assertEqual(VERSION, version)
            return {"imageId": IMAGE_ID, "exporterDigest": IMAGE_ID, "version": version,
                    "versionTag": f"ymcloud-{service}:{version}", "digestKind": "docker-config"}

        with mock.patch.multiple(release, ROOT=source, ARTIFACTS=artifacts):
            with (mock.patch.object(release.sys, "argv", ["release.py", "--skip-build"]),
                  mock.patch.object(release, "project_version", return_value=VERSION) as version,
                  mock.patch.object(release, "load_base_images", return_value=references),
                  mock.patch.object(release, "execute", return_value='{"OSType":"linux"}') as execute,
                  mock.patch.object(release, "build_sources") as sources,
                  mock.patch.object(release, "validate_jar") as jar,
                  mock.patch.object(release, "validate_frontend") as frontend,
                  mock.patch.object(release, "verify_base_image", side_effect=lambda reference: {
                      "imageId": IMAGE_ID, "source": reference, "digestKind": "docker-config"}),
                  mock.patch.object(release, "build_image", side_effect=built_image) as build,
                  mock.patch.object(release.sys, "stdout", new_callable=io.StringIO) as output):
                release.main()
        version.assert_called_once_with()
        sources.assert_not_called()
        self.assertEqual([
            mock.call(source / "ymcloud-web/target/ymcloud-web.jar", VERSION),
            mock.call(source / "ymcloud-judge/target/ymcloud-judge.jar", VERSION),
        ], jar.call_args_list)
        frontend.assert_called_once_with(source / "ymcloud-vue/dist", VERSION)
        self.assertEqual({"web", "worker", "go-judge", "frontend"}, {call.args[0] for call in build.call_args_list})
        self.assertEqual(4, build.call_count)
        execute.assert_called_once_with(["docker", "info", "--format", "{{json .}}"], capture=True)
        destination = artifacts / "full-stack-deployment"
        self.assertEqual({".env", "compose.yaml", "README.md", "initdb/ymcloud.sql", "images.lock.json"}, {
            path.relative_to(destination).as_posix() for path in destination.rglob("*") if path.is_file()
        })
        lock = json.loads((destination / "images.lock.json").read_text(encoding="utf-8"))
        self.assertEqual(VERSION, lock["version"])
        self.assertEqual(references, lock["buildBases"])
        self.assertEqual(set(release.SERVICES), set(lock["images"]))
        for service in ("mysql", "redis", "rabbitmq"):
            self.assertEqual({"imageId": IMAGE_ID, "source": references[service], "digestKind": "docker-config"},
                             lock["images"][service])
        self.assertIn("本地部署目录已生成", output.getvalue())
        self.assertNotIn("images.tar", output.getvalue())


if __name__ == "__main__":
    unittest.main()

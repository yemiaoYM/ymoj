#!/usr/bin/env python3
"""Web 与 Worker 的持久日志目录回归，不启动 Java 或 Docker。"""

import importlib.util
import os
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[5]


def load_entrypoint(name, path):
    specification = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(specification)
    with mock.patch.dict(sys.modules, {"fcntl": mock.Mock(LOCK_EX=2, LOCK_NB=4)}):
        specification.loader.exec_module(module)
    return module


ENTRYPOINTS = {
    "web": load_entrypoint("web_log_entrypoint", "docker/web/entrypoint.py"),
    "worker": load_entrypoint("worker_log_entrypoint", "docker/go-judge/worker-entrypoint.py"),
}


class EntrypointLogsTest(unittest.TestCase):
    def setUp(self):
        artifacts = ROOT / "target/phase3-verification"
        artifacts.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="entrypoint-logs-", dir=artifacts)
        self.directory = pathlib.Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def test_log_directory_is_created_without_deleting_previous_logs(self):
        for name, entrypoint in ENTRYPOINTS.items():
            with self.subTest(service=name):
                logs = self.directory / name
                with mock.patch.dict(os.environ, {"LOGGING_FILE_PATH": str(logs)}):
                    entrypoint.prepare_log_directory()
                    previous = logs / "application.log"
                    previous.write_text("保留已写入的日志\n", encoding="utf-8")
                    entrypoint.prepare_log_directory()
                self.assertEqual("保留已写入的日志\n", previous.read_text(encoding="utf-8"))

    def test_default_container_directory_is_prepared_with_private_permissions(self):
        for name, entrypoint in ENTRYPOINTS.items():
            with self.subTest(service=name):
                logs = mock.Mock()
                logs.is_absolute.return_value = True
                logs.is_symlink.return_value = False
                logs.resolve.return_value = logs
                with (mock.patch.dict(os.environ, {}, clear=True),
                      mock.patch.object(entrypoint.pathlib, "Path", return_value=logs) as path,
                      mock.patch.object(entrypoint.os, "access", return_value=True) as access):
                    entrypoint.prepare_log_directory()
                path.assert_called_once_with("/data/logs")
                logs.mkdir.assert_called_once_with(mode=0o700, parents=True, exist_ok=True)
                access.assert_called_once_with(logs, os.W_OK | os.X_OK)

    def test_relative_empty_or_linked_directories_are_rejected_before_creation(self):
        for name, entrypoint in ENTRYPOINTS.items():
            for value in ("", "logs", "../logs"):
                with self.subTest(service=name, value=value):
                    with (mock.patch.dict(os.environ, {"LOGGING_FILE_PATH": value}),
                          mock.patch.object(pathlib.Path, "mkdir") as mkdir):
                        with self.assertRaisesRegex(SystemExit, "绝对路径"):
                            entrypoint.prepare_log_directory()
                        mkdir.assert_not_called()
            for direct_link in (True, False):
                with self.subTest(service=name, direct_link=direct_link):
                    logs = mock.Mock()
                    logs.is_absolute.return_value = True
                    logs.is_symlink.return_value = direct_link
                    logs.resolve.return_value = mock.Mock()
                    with mock.patch.object(entrypoint.pathlib, "Path", return_value=logs):
                        with self.assertRaisesRegex(SystemExit, "符号链接"):
                            entrypoint.prepare_log_directory()
                    logs.mkdir.assert_not_called()

    def test_unwritable_directory_reports_required_container_uid(self):
        for name, entrypoint in ENTRYPOINTS.items():
            with self.subTest(service=name):
                with (mock.patch.dict(os.environ, {"LOGGING_FILE_PATH": str(self.directory / name)}),
                      mock.patch.object(entrypoint.os, "access", return_value=False)):
                    with self.assertRaisesRegex(SystemExit, "UID 10001"):
                        entrypoint.prepare_log_directory()

    def test_web_prepares_logs_before_starting_java(self):
        entrypoint = ENTRYPOINTS["web"]
        files = mock.Mock()
        files.is_symlink.return_value = False
        files.resolve.return_value = files
        for unavailable in (False, True):
            with self.subTest(unavailable=unavailable):
                with (mock.patch.dict(os.environ, {"YMCLOUD_WEB_HEAP_MIB": "384"}),
                      mock.patch.object(entrypoint.pathlib, "Path", return_value=files),
                      mock.patch.object(entrypoint.os, "access", return_value=True),
                      mock.patch.object(entrypoint, "prepare_log_directory") as logs,
                      mock.patch.object(entrypoint.os, "execv") as execution):
                    if unavailable:
                        logs.side_effect = SystemExit("日志目录不可写")
                        with self.assertRaises(SystemExit):
                            entrypoint.main()
                        execution.assert_not_called()
                    else:
                        entrypoint.main()
                        self.assertEqual("/usr/bin/java", execution.call_args.args[0])
                    logs.assert_called_once_with()

    def test_worker_log_failure_stops_before_work_cleanup_or_java_start(self):
        entrypoint = ENTRYPOINTS["worker"]
        with (mock.patch.dict(os.environ, {"YMCLOUD_WORKER_HEAP_MIB": "128"}),
              mock.patch.object(entrypoint.os, "O_NOFOLLOW", 0, create=True),
              mock.patch.object(entrypoint.os, "open", return_value=7),
              mock.patch.object(entrypoint.os, "set_inheritable"),
              mock.patch.object(entrypoint, "prepare_log_directory", side_effect=SystemExit("日志目录不可写")),
              mock.patch.object(entrypoint.pathlib, "Path") as path,
              mock.patch.object(entrypoint.os, "execv") as execution):
            with self.assertRaises(SystemExit):
                entrypoint.main()
            path.assert_not_called()
            execution.assert_not_called()


if __name__ == "__main__":
    unittest.main()

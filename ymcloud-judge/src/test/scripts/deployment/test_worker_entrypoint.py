#!/usr/bin/env python3
"""Worker JVM 堆参数回归；不访问容器数据目录，不启动 Java。"""

import importlib.util
import os
import pathlib
import sys
import unittest
from unittest import mock


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[5]
SPEC = importlib.util.spec_from_file_location("worker_entrypoint", ROOT / "docker/go-judge/worker-entrypoint.py")
ENTRYPOINT = importlib.util.module_from_spec(SPEC)
with mock.patch.dict(sys.modules, {"fcntl": mock.Mock(LOCK_EX=2, LOCK_NB=4)}), \
        mock.patch.object(sys, "path", [str(ROOT / "docker/go-judge"), *sys.path]):
    SPEC.loader.exec_module(ENTRYPOINT)


class WorkerEntrypointTest(unittest.TestCase):
    def test_default_heap_retains_existing_runtime_parameters(self):
        with mock.patch.dict(os.environ, {}, clear=True), \
                mock.patch.object(ENTRYPOINT, "available_cpus", return_value=3):
            command = ENTRYPOINT.java_command()
        self.assertEqual("java", command[0])
        self.assertIn("-Xms32m", command)
        self.assertIn("-Xmx128m", command)
        self.assertIn("-Dfile.encoding=UTF-8", command)
        self.assertIn("-XX:ActiveProcessorCount=3", command)
        self.assertEqual(["-jar", "/app/worker.jar", "--spring.profiles.active=prod"], command[-3:])

    def test_configured_heap_accepts_both_boundaries_and_parallel_worker_budget(self):
        for heap in ("128", "192", "512", "4096"):
            with self.subTest(heap=heap), mock.patch.dict(os.environ, {"YMCLOUD_WORKER_HEAP_MIB": heap}), \
                    mock.patch.object(ENTRYPOINT, "available_cpus", return_value=1):
                command = ENTRYPOINT.java_command()
                self.assertEqual([f"-Xmx{heap}m"], [argument for argument in command if argument.startswith("-Xmx")])

    def test_invalid_heap_fails_before_lock_or_work_directory_cleanup(self):
        for heap in ("", "127", "4097", "-128", "128.5", "128m", " 128", "128\n", "１２８", "256 -Dkey=private"):
            with self.subTest(heap=heap), mock.patch.dict(os.environ, {"YMCLOUD_WORKER_HEAP_MIB": heap}), \
                    mock.patch.object(ENTRYPOINT.os, "open") as opening:
                with self.assertRaises(SystemExit) as failure:
                    ENTRYPOINT.main()
                self.assertIn("YMCLOUD_WORKER_HEAP_MIB", str(failure.exception))
                self.assertNotIn("private", str(failure.exception))
                opening.assert_not_called()

    def test_main_passes_validated_heap_to_java_without_shell(self):
        work = mock.MagicMock()
        work.is_symlink.return_value = False
        work.resolve.return_value = work
        work.iterdir.return_value = []
        with mock.patch.dict(os.environ, {"YMCLOUD_WORKER_HEAP_MIB": "256"}), \
                mock.patch.object(ENTRYPOINT, "available_cpus", return_value=1), \
                mock.patch.object(ENTRYPOINT.os, "O_NOFOLLOW", 0, create=True), \
                mock.patch.object(ENTRYPOINT.os, "open", return_value=7), \
                mock.patch.object(ENTRYPOINT.os, "set_inheritable"), \
                mock.patch.object(ENTRYPOINT, "prepare_log_directory") as logs, \
                mock.patch.object(ENTRYPOINT.pathlib, "Path", return_value=work), \
                mock.patch.object(ENTRYPOINT.os, "execv") as execution:
            ENTRYPOINT.main()
            executable, command = execution.call_args.args
            self.assertEqual("/usr/bin/java", executable)
            self.assertIn("-Xmx256m", command)
            logs.assert_called_once_with()
            work.mkdir.assert_called_once_with(mode=0o700, parents=True, exist_ok=True)

    def test_cpu_parameter_respects_parent_quota_and_affinity(self):
        files = {
            "/proc/self/cgroup": "0::/worker\n",
            "/sys/fs/cgroup/worker/cpu.max": "400000 100000",
            "/sys/fs/cgroup/cpu.max": "200000 100000",
        }
        for affinity, expected in ((8, 2), (1, 1)):
            with self.subTest(affinity=affinity), \
                    mock.patch.dict(os.environ, {}, clear=True), \
                    mock.patch.object(ENTRYPOINT.os, "sched_getaffinity", return_value=set(range(affinity)),
                                      create=True), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "is_file", autospec=True,
                                      side_effect=lambda path: path.as_posix() in files), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "read_text", autospec=True,
                                      side_effect=lambda path, **_: files[path.as_posix()]):
                command = ENTRYPOINT.java_command()
                self.assertIn(f"-XX:ActiveProcessorCount={expected}", command)


if __name__ == "__main__":
    unittest.main()

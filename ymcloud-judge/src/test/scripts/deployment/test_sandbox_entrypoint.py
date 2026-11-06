#!/usr/bin/env python3
"""
沙箱并发按实际 CPU 配额计算，不启动容器或选手程序
"""

import importlib.util
import os
import pathlib
import sys
import unittest
from unittest import mock


sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[5]
SPEC = importlib.util.spec_from_file_location("sandbox_entrypoint", ROOT / "docker/go-judge/entrypoint.py")
ENTRYPOINT = importlib.util.module_from_spec(SPEC)
with mock.patch.dict(sys.modules, {"fcntl": mock.Mock()}), \
        mock.patch.object(sys, "path", [str(ROOT / "docker/go-judge"), *sys.path]):
    SPEC.loader.exec_module(ENTRYPOINT)


class SandboxEntrypointTest(unittest.TestCase):
    def test_default_respects_cpu_quota_and_affinity(self):
        for affinity, quota, expected in ((8, "200000 100000", 2), (2, "400000 100000", 2),
                                          (8, "150000 100000", 1), (8, "50000 100000", 1),
                                          (3, "max 100000", 3)):
            with self.subTest(affinity=affinity, quota=quota), \
                    mock.patch.dict(os.environ, {}, clear=True), \
                    mock.patch.object(ENTRYPOINT.os, "sched_getaffinity", return_value=set(range(affinity)),
                                      create=True), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "is_file", return_value=True), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "read_text", side_effect=["0::/\n", quota]):
                self.assertEqual(expected, ENTRYPOINT.sandbox_parallelism())

    def test_default_respects_visible_parent_cpu_quota(self):
        for leaf_quota in ("max 100000", "400000 100000"):
            files = {
                "/proc/self/cgroup": "0::/judge/worker\n",
                "/sys/fs/cgroup/judge/worker/cpu.max": leaf_quota,
                "/sys/fs/cgroup/judge/cpu.max": "200000 100000",
                "/sys/fs/cgroup/cpu.max": "max 100000",
            }
            with self.subTest(leaf_quota=leaf_quota), \
                    mock.patch.dict(os.environ, {}, clear=True), \
                    mock.patch.object(ENTRYPOINT.os, "sched_getaffinity", return_value=set(range(8)),
                                      create=True), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "is_file", autospec=True,
                                      side_effect=lambda path: path.as_posix() in files), \
                    mock.patch.object(ENTRYPOINT.pathlib.Path, "read_text", autospec=True,
                                      side_effect=lambda path, **_: files[path.as_posix()]):
                self.assertEqual(2, ENTRYPOINT.sandbox_parallelism())

    def test_explicit_concurrency_does_not_depend_on_host_cpu_count(self):
        with mock.patch.dict(os.environ, {"YMCLOUD_GO_JUDGE_PARALLELISM": "64"}), \
                mock.patch.object(ENTRYPOINT.pathlib.Path, "read_text") as reading:
            self.assertEqual(64, ENTRYPOINT.sandbox_parallelism())
            reading.assert_not_called()

    def test_negative_concurrency_is_rejected(self):
        with mock.patch.dict(os.environ, {"YMCLOUD_GO_JUDGE_PARALLELISM": "-1"}):
            with self.assertRaises(SystemExit):
                ENTRYPOINT.sandbox_parallelism()


if __name__ == "__main__":
    unittest.main()

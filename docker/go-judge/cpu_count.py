"""
读取 Linux 进程实际可用的 CPU 整核数
"""

import os
import pathlib


def available_cpus():
    """
    取 CPU 亲和性及当前、可见父 cgroup 配额的最小值，至少保留一个执行槽位
    """
    count = len(os.sched_getaffinity(0))
    root = pathlib.Path("/sys/fs/cgroup")
    membership = pathlib.Path("/proc/self/cgroup").read_text(encoding="utf-8").splitlines()
    relative = next(line[3:] for line in membership if line.startswith("0::"))
    group = root / relative.lstrip("/")
    while True:
        limit = group / "cpu.max"
        if limit.is_file():
            quota, period = limit.read_text(encoding="utf-8").split()
            if quota != "max":
                count = min(count, max(1, int(quota) // int(period)))
        if group == root:
            break
        group = group.parent
    return count

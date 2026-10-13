#!/usr/bin/env python3
"""验证 Worker 消费者心跳与 PID 1；不以 go-judge 健康代替消费者健康。"""

import pathlib
import sys
import time


def main():
    try:
        command = pathlib.Path("/proc/1/cmdline").read_bytes().split(b"\0")
        if not command or pathlib.PurePosixPath(command[0].decode()).name != "java":
            return 1
        path = pathlib.Path("/data/work/health")
        if path.is_symlink():
            return 1
        timestamp = int(path.read_text(encoding="utf-8").strip())
        age = time.time() * 1000 - timestamp
        return 0 if 0 <= age <= 30000 else 1
    except (OSError, ValueError, UnicodeError):
        return 1


if __name__ == "__main__":
    sys.exit(main())

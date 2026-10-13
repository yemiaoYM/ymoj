#!/usr/bin/env python3
"""Worker 仅清理独占临时目录，保留数据缓存与结果恢复日志。"""

import fcntl
import os
import pathlib
import shutil


def main():
    lease = os.open("/data/.worker.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
    os.set_inheritable(lease, True)
    work = pathlib.Path("/data/work")
    if work.is_symlink() or work.resolve() != work:
        raise SystemExit("Worker 临时目录不能是符号链接")
    work.mkdir(mode=0o700, parents=True, exist_ok=True)
    for entry in work.iterdir():
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
    os.execv("/usr/bin/java", [
        "java", "-Xms32m", "-Xmx128m", "-XX:ActiveProcessorCount=2",
        "-Dfile.encoding=UTF-8", "-jar", "/app/worker.jar", "--spring.profiles.active=prod",
    ])


if __name__ == "__main__":
    main()

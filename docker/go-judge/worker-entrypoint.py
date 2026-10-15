#!/usr/bin/env python3
"""Worker 仅清理独占临时目录，保留数据缓存与结果恢复日志。"""

import fcntl
import os
import pathlib
import shutil


def prepare_log_directory():
    logs = pathlib.Path(os.environ.get("LOGGING_FILE_PATH", "/data/logs"))
    if not logs.is_absolute() or logs.is_symlink() or logs.resolve() != logs:
        raise SystemExit("Worker 日志目录必须为真实的绝对路径，不能包含符号链接")
    logs.mkdir(mode=0o700, parents=True, exist_ok=True)
    if not os.access(logs, os.W_OK | os.X_OK):
        raise SystemExit("Worker 日志目录不可写，请检查数据卷权限，运行用户为 UID 10001")


def java_command():
    heap = os.environ.get("YMCLOUD_WORKER_HEAP_MIB", "128")
    if not heap.isascii() or not heap.isdecimal() or not 128 <= int(heap) <= 4096:
        raise SystemExit("YMCLOUD_WORKER_HEAP_MIB 必须是 128 至 4096 的整数，单位 MiB")
    return [
        "java", "-Xms32m", f"-Xmx{int(heap)}m", "-XX:ActiveProcessorCount=2",
        "-Dfile.encoding=UTF-8", "-jar", "/app/worker.jar", "--spring.profiles.active=prod",
    ]


def main():
    command = java_command()
    lease = os.open("/data/.worker.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
    os.set_inheritable(lease, True)
    prepare_log_directory()
    work = pathlib.Path("/data/work")
    if work.is_symlink() or work.resolve() != work:
        raise SystemExit("Worker 临时目录不能是符号链接")
    work.mkdir(mode=0o700, parents=True, exist_ok=True)
    for entry in work.iterdir():
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
    os.execv("/usr/bin/java", command)


if __name__ == "__main__":
    main()

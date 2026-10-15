#!/usr/bin/env python3
"""以普通用户启动 Web；持久化文件由独立 Docker 数据卷承载。"""

import os
import pathlib


def prepare_log_directory():
    logs = pathlib.Path(os.environ.get("LOGGING_FILE_PATH", "/data/logs"))
    if not logs.is_absolute() or logs.is_symlink() or logs.resolve() != logs:
        raise SystemExit("Web 日志目录必须为真实的绝对路径，不能包含符号链接")
    logs.mkdir(mode=0o700, parents=True, exist_ok=True)
    if not os.access(logs, os.W_OK | os.X_OK):
        raise SystemExit("Web 日志目录不可写，请检查数据卷权限，运行用户为 UID 10001")


def main():
    try:
        heap_mib = int(os.environ.get("YMCLOUD_WEB_HEAP_MIB", "384"))
    except ValueError:
        raise SystemExit("Web 堆内存必须填写整数 MiB") from None
    if not 128 <= heap_mib <= 4096:
        raise SystemExit("Web 堆内存必须为 128 至 4096 MiB，并小于容器总内存")
    files = pathlib.Path("/data/files")
    if files.is_symlink() or files.resolve() != files:
        raise SystemExit("Web 文件目录不能是符号链接")
    files.mkdir(mode=0o700, parents=True, exist_ok=True)
    if not os.access(files, os.W_OK | os.X_OK):
        raise SystemExit("Web 文件目录不可写，请检查数据卷权限，运行用户为 UID 10001")
    prepare_log_directory()
    os.execv("/usr/bin/java", [
        "java", "-Xms64m", f"-Xmx{heap_mib}m", "-XX:ActiveProcessorCount=2",
        "-Dfile.encoding=UTF-8", "-jar", "/app/web.jar", "--spring.profiles.active=prod",
    ])


if __name__ == "__main__":
    main()

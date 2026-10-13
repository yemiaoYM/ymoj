#!/usr/bin/env python3
"""启动前清理独占缓存；默认 seccomp 与 cgroup 失败关闭，不输出鉴权值。"""

import fcntl
import os
import pathlib
import shutil
import sys


def main():
    if len(sys.argv) != 1:
        raise SystemExit("不接受额外启动参数，请使用已校验环境变量")
    token = os.environ.get("YMCLOUD_GO_JUDGE_TOKEN", "")
    if not 32 <= len(token) <= 512 or any(ord(c) <= 32 or ord(c) >= 127 for c in token):
        raise SystemExit("go-judge 独立令牌必须为 32 至 512 个可打印 ASCII 字符")
    if not pathlib.Path("/sys/fs/cgroup/cgroup.controllers").is_file():
        raise SystemExit("部署需要 cgroup v2")
    parallelism = int(os.environ.get("YMCLOUD_GO_JUDGE_PARALLELISM", "1"))
    ttl = int(os.environ.get("YMCLOUD_GO_JUDGE_FILE_TTL_SECONDS", "3600"))
    max_file = int(os.environ.get("YMCLOUD_GO_JUDGE_MAX_FILE_BYTES", "16777216"))
    if parallelism < 1 or ttl < 3600 or max_file < 1:
        raise SystemExit("并发和文件 TTL 配置无效")

    root = pathlib.Path("/var/lib/go-judge")
    if root.is_symlink() or root.resolve() != root:
        raise SystemExit("go-judge 缓存根目录必须为独占真实目录")
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    lease = os.open(root / ".lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
    os.set_inheritable(lease, True)
    # 锁文件保留到 exec 后，禁止两个服务实例清理同一目录。
    for name in ("files", "run"):
        path = root / name
        if path.is_symlink():
            raise SystemExit("缓存子目录不能是符号链接")
        path.mkdir(mode=0o700, exist_ok=True)
        for entry in path.iterdir():
            if entry.is_dir() and not entry.is_symlink():
                shutil.rmtree(entry)
            else:
                entry.unlink()

    env = {key: value for key, value in os.environ.items() if not key.startswith("ES_")}
    env["ES_AUTH_TOKEN"] = token
    env["TMPDIR"] = str(root / "run")
    os.execve("/opt/go-judge", [
        "/opt/go-judge", "-silent=true", "-http-addr=:5050",
        "-no-fallback=true", "-no-seccomp=false", "-enable-cpu-rate=true",
        "-mount-conf=/opt/mount.yaml", "-dir=/var/lib/go-judge/files",
        "-src-prefix=/var/lib/go-judge/files", f"-parallelism={parallelism}",
        "-pre-fork=1", f"-output-limit={max_file}", f"-copy-out-limit={max_file}",
        "-time-limit-checker-interval=10ms", f"-file-timeout={ttl}s",
    ], env)


if __name__ == "__main__":
    main()

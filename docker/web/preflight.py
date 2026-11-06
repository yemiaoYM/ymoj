#!/usr/bin/env python3
"""镜像内的一次性部署检查：只输出问题字段，不输出密码或完整环境变量。"""

import math
import os
import pathlib
import sys


MIB = 1024 * 1024


def validate(env):
    """在任何数据库初始化前拒绝缺失密钥和不匹配的执行容量。"""
    minimum_lengths = {
        "MYSQL_ROOT_PASSWORD": 16,
        "MYSQL_PASSWORD": 16,
        "REDIS_PASSWORD": 16,
        "RABBITMQ_PASSWORD": 16,
        "TOKEN_SECRET": 64,
        "WORKER_TOKEN": 32,
        "GO_JUDGE_TOKEN": 32,
    }
    secrets = []
    for key, minimum in minimum_lengths.items():
        value = env.get(key, "")
        if len(value) < minimum or len(value) > 512 or any(ord(char) <= 32 or ord(char) >= 127 for char in value):
            raise ValueError(f"{key} 必须为 {minimum} 至 512 位不含空白的 ASCII 字符，请编辑 .env")
        if any(marker in value.lower() for marker in ("changeme", "change-me", "replace-me", "your-password")):
            raise ValueError(f"{key} 仍是示例值，请填写自己的随机值")
        secrets.append(value)
    if len(set(secrets)) != len(secrets):
        raise ValueError("数据库密码、缓存密码和各服务密钥不能复用同一个值")

    bounds = {
        "YMCLOUD_WORKER_CONCURRENCY": (0, 2147483647),
        "YMCLOUD_GO_JUDGE_CONCURRENCY": (0, 2147483647),
        "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES": (256 * MIB, 1024 * 1024 * MIB),
        "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB": (1024, 64 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES": (32 * MIB, 64 * 1024 * MIB),
        "YMCLOUD_GO_JUDGE_COMPILE_WALL_MS": (100, 300000),
        "YMCLOUD_GO_JUDGE_PROCESS_LIMIT": (4, 1024),
        "YMCLOUD_GO_JUDGE_CPU_RATE": (1, 64000),
        "JUDGE_PIDS": (68, 65536),
        "WORKER_HEAP_MIB": (128, 4096),
        "WORKER_MEMORY_MIB": (384, 8192),
        "WORKER_PIDS": (128, 65536),
    }
    values = {}
    for key, (minimum, maximum) in bounds.items():
        try:
            value = int(env.get(key, ""))
        except ValueError:
            raise ValueError(f"{key} 必须为整数") from None
        if not minimum <= value <= maximum:
            raise ValueError(f"{key} 超出支持范围 {minimum} 至 {maximum}")
        values[key] = value
    try:
        cpus = float(env.get("JUDGE_CPUS", ""))
    except ValueError:
        raise ValueError("JUDGE_CPUS 必须为数字") from None
    if not math.isfinite(cpus) or cpus <= 0:
        raise ValueError("JUDGE_CPUS 必须为正数")
    count = values["YMCLOUD_GO_JUDGE_CONCURRENCY"] or max(1, math.floor(cpus))
    largest = max(values["YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB"] * 1024,
                  values["YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES"])
    required = max(count * (largest + 128 * MIB) + 128 * MIB,
                   count * (largest + 64 * MIB) + 256 * MIB)
    if values["YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES"] < required:
        raise ValueError("JUDGE_MEMORY_BYTES 不足以覆盖并发、单题/编译上限及服务开销")
    if cpus < count * values["YMCLOUD_GO_JUDGE_CPU_RATE"] / 1000:
        raise ValueError("JUDGE_CPUS 不足以覆盖并发与每任务 CPU 预算")
    if values["JUDGE_PIDS"] < count * values["YMCLOUD_GO_JUDGE_PROCESS_LIMIT"] + 64:
        raise ValueError("JUDGE_PIDS 不足以覆盖并发进程和服务开销")
    if values["WORKER_MEMORY_MIB"] < values["WORKER_HEAP_MIB"] + 256:
        raise ValueError("WORKER_MEMORY_MIB 必须至少比 Java 堆多 256MiB")
    try:
        worker_cpus = float(env.get("WORKER_CPUS", ""))
    except ValueError:
        raise ValueError("WORKER_CPUS 必须为数字") from None
    if not math.isfinite(worker_cpus) or worker_cpus <= 0:
        raise ValueError("WORKER_CPUS 必须为正数")


def main():
    try:
        validate(os.environ)
        if not pathlib.Path("/sys/fs/cgroup/cgroup.controllers").is_file():
            raise ValueError("Docker 需要使用 Linux 容器及 cgroup v2")
    except ValueError as error:
        print(f"部署检查失败：{error}", file=sys.stderr)
        return 1
    print("部署配置检查通过，开始启动数据库和应用。")
    return 0


if __name__ == "__main__":
    sys.exit(main())

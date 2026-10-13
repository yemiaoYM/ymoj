#!/usr/bin/env python3
"""Linux 部署前校验不可变镜像、容量配套和独占目录，再启动两项服务。"""

import argparse
import json
import pathlib
import re
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", type=pathlib.Path, default=HERE / ".env")
    parser.add_argument("--artifacts", type=pathlib.Path, default=ROOT / "target/phase3-verification")
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    if sys.platform != "linux":
        raise SystemExit("生产 Compose 使用 Linux 宿主网络；请在目标 Linux 主机执行")
    engine = json.loads(subprocess.check_output(["docker", "info", "--format", "{{json .}}"], text=True))
    if engine.get("OSType") != "linux" or str(engine.get("CgroupVersion")) != "2":
        raise SystemExit("需要 Linux Docker 和 cgroup v2")
    compose = ["docker", "compose", "--env-file", str(args.env.resolve()),
               "--env-file", str((args.artifacts / "images.env").resolve()),
               "-f", str(HERE / "compose.yaml")]
    # config 含凭据，仅在内存读取，不写入诊断报告或输出到控制台。
    config = json.loads(subprocess.check_output([*compose, "config", "--format", "json"], text=True))
    services = config["services"]
    lock = json.loads((args.artifacts / "images.lock.json").read_text(encoding="utf-8"))
    for service, target in (("go-judge", "runtime"), ("worker", "worker")):
        image_id = services[service]["image"]
        if image_id != lock[target]["imageId"] or not re.fullmatch(r"sha256:[0-9a-f]{64}", image_id):
            raise SystemExit("镜像不符合构建锁，拒绝按 tag 启动")
        inspected = json.loads(subprocess.check_output(["docker", "image", "inspect", image_id], text=True))[0]
        if inspected["Id"] != image_id or inspected["Os"] != "linux" or inspected["Architecture"] != "amd64":
            raise SystemExit("镜像内容或架构不符合构建锁")
        metadata = json.loads((args.artifacts / f"{target}.metadata.json").read_text(encoding="utf-8"))
        if metadata.get("containerimage.config.digest") != image_id or (
                metadata.get("containerimage.digest") != lock[target]["exporterDigest"]):
            raise SystemExit("构建导出摘要与实际运行镜像的 config digest 不符合构建锁")
    sandbox = services["go-judge"]
    worker = services["worker"]
    env = worker["environment"]
    concurrency = int(env["YMCLOUD_GO_JUDGE_CONCURRENCY"])
    if not 1 <= concurrency <= 32:
        raise SystemExit("沙箱并发必须为 1 至 32")
    if concurrency != int(sandbox["environment"]["YMCLOUD_GO_JUDGE_PARALLELISM"]):
        raise SystemExit("Worker 与 go-judge 并发配置不一致")
    if int(env["YMCLOUD_WORKER_CONCURRENCY"]) != concurrency:
        raise SystemExit("Worker 与 go-judge 并发必须相等")
    for key in ("YMCLOUD_GO_JUDGE_TOKEN", "YMCLOUD_WORKER_SERVICE_TOKEN"):
        value = env[key]
        if not 32 <= len(value) <= 512 or any(ord(char) <= 32 or ord(char) >= 127 for char in value):
            raise SystemExit("服务令牌必须为 32 至 512 个可打印 ASCII 字符")
    if env["YMCLOUD_GO_JUDGE_TOKEN"] == env["YMCLOUD_WORKER_SERVICE_TOKEN"]:
        raise SystemExit("go-judge 和 Web 必须使用不同的服务令牌")
    bounds = {
        "YMCLOUD_GO_JUDGE_MAX_FILE_BYTES": (1024, 256 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_MAX_OUTPUT_BYTES": (1024, 16 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_MAX_REQUEST_BYTES": (1024, 256 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_MAX_RESPONSE_BYTES": (1024, 64 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES": (32 * 1024 * 1024, 64 * 1024 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB": (1024, 64 * 1024 * 1024),
        "YMCLOUD_GO_JUDGE_COMPILE_WALL_MS": (100, 300000),
        "YMCLOUD_GO_JUDGE_CPU_RATE": (1, 64000),
        "YMCLOUD_GO_JUDGE_PROCESS_LIMIT": (4, 1024),
    }
    for key, (minimum, maximum_value) in bounds.items():
        if not minimum <= int(env[key]) <= maximum_value:
            raise SystemExit(f"部署参数超出支持范围: {key}")
    if int(env["YMCLOUD_GO_JUDGE_MAX_RESPONSE_BYTES"]) < 6 * int(env["YMCLOUD_GO_JUDGE_MAX_OUTPUT_BYTES"]) + 65536:
        raise SystemExit("响应预算不足以容纳最坏 JSON 转义后的 stdout/stderr")
    duration = re.fullmatch(r"([0-9]+)(ms|s|m)", env["YMCLOUD_GO_JUDGE_REQUEST_TIMEOUT"])
    if not duration:
        raise SystemExit("部署传输超时请使用正整数和 ms、s 或 m")
    timeout_ms = int(duration[1]) * {"ms": 1, "s": 1000, "m": 60000}[duration[2]]
    if not 3000 <= timeout_ms <= 600000 or timeout_ms <= int(env["YMCLOUD_GO_JUDGE_COMPILE_WALL_MS"]):
        raise SystemExit("传输超时必须覆盖连接/编译且不超过 10 分钟")
    if int(sandbox["environment"]["YMCLOUD_GO_JUDGE_FILE_TTL_SECONDS"]) < 3600:
        raise SystemExit("文件 TTL 不能短于最大一小时任务截止窗口")
    maximum = max(int(env["YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB"]) * 1024,
                  int(env["YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES"]))
    budget = int(env["YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES"])
    if budget != int(sandbox["mem_limit"]) or budget != int(sandbox["memswap_limit"]):
        raise SystemExit("服务内存预算必须匹配 Worker，并禁用 swap")
    required = max(concurrency * (maximum + 134217728) + 134217728,
                   concurrency * (maximum + 67108864) + 268435456)
    if budget < required:
        raise SystemExit("服务内存不足以容纳沙箱、临时文件系统和服务开销")
    if float(sandbox["cpus"]) < concurrency * int(env["YMCLOUD_GO_JUDGE_CPU_RATE"]) / 1000:
        raise SystemExit("服务 CPU 总预算必须与每任务 CPU 限额及并发配套")
    if int(sandbox["pids_limit"]) < concurrency * int(env["YMCLOUD_GO_JUDGE_PROCESS_LIMIT"]) + 64:
        raise SystemExit("服务进程预算不足")
    if int(env["YMCLOUD_GO_JUDGE_MAX_FILE_BYTES"]) != int(sandbox["environment"]["YMCLOUD_GO_JUDGE_MAX_FILE_BYTES"]):
        raise SystemExit("单文件上限必须在 Worker 和服务保持一致")
    if worker.get("privileged") or worker["network_mode"] != "host":
        raise SystemExit("Worker 必须非特权并使用 Linux 宿主网络")
    directories = []
    for service in (sandbox, worker):
        for mount in service["volumes"]:
            if mount["type"] != "bind":
                continue
            path = pathlib.Path(mount["source"])
            if not path.is_absolute() or not path.is_dir() or path.is_symlink() or path.resolve() != path:
                raise SystemExit("请先创建独占的真实部署目录")
            directories.append(path)
    if len(directories) != 2 or directories[0] == directories[1] or any(
            parent in child.parents for parent, child in (directories, list(reversed(directories)))):
        raise SystemExit("go-judge 与 Worker 数据目录必须独立，不能相互包含")
    worker_dir = pathlib.Path(worker["volumes"][0]["source"])
    if worker_dir.stat().st_uid != 10001:
        raise SystemExit("Worker 数据目录应属于 UID 10001")
    print("版本锁、cgroup v2、CPU/内存/进程预算和独占目录验证通过")
    if not args.check_only:
        subprocess.run([*compose, "up", "-d", "--wait", "--wait-timeout", "120"], check=True)


if __name__ == "__main__":
    main()

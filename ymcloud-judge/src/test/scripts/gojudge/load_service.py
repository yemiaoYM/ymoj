#!/usr/bin/env python3
"""专属开发验收实例的固定执行负载；不连接 Web、队列或业务数据。"""

import argparse
import concurrent.futures
import json
import os
import signal
import threading
import time

from verify_runtime import command, request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", default="http://127.0.0.1:5050")
    parser.add_argument("--parallelism", type=int, choices=(1, 2), default=1)
    parser.add_argument("--pause-ms", type=int, default=200)
    parser.add_argument("--duration-seconds", type=int, default=900)
    args = parser.parse_args()
    if os.environ.get("YMCLOUD_RUN_GO_JUDGE_TESTS") != "true":
        raise SystemExit("负载仅可用于显式启用的专属验收实例")
    if not 0 <= args.pause_ms <= 10000 or not 1 <= args.duration_seconds <= 1800:
        raise SystemExit("负载间隔或持续时间超出支持范围")
    token = os.environ["YMCLOUD_GO_JUDGE_TOKEN"]
    stopped = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    deadline = time.monotonic() + args.duration_seconds
    source = "import time\nstart=time.process_time()\nwhile time.process_time()-start<0.2:\n pass\n"
    body = command([
        "/usr/local/bin/ymcloud-output-limit", "1048576", "16777216",
        "/usr/bin/python3", "-I", "-c", source,
    ], wall=2_000_000_000)

    def send_load():
        counts = {"accepted": 0, "failed": 0}
        while not stopped.is_set() and time.monotonic() < deadline:
            try:
                result = request(args.endpoint, token, "/run", body)[0]
                if result["status"] == "Accepted" and result["exitStatus"] == 0 and not result.get("files", {}).get("launcher"):
                    counts["accepted"] += 1
                else:
                    counts["failed"] += 1
            except Exception:
                counts["failed"] += 1
                stopped.wait(0.1)
            stopped.wait(args.pause_ms / 1000)
        return counts

    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.parallelism) as executor:
        results = list(executor.map(lambda _: send_load(), range(args.parallelism)))
    print(json.dumps({
        "type": "synthetic-execution-load",
        "parallelism": args.parallelism,
        "pauseMillis": args.pause_ms,
        "cpuMillisPerRequest": 200,
        "elapsedMillis": round((time.monotonic() - started) * 1000),
        "accepted": sum(result["accepted"] for result in results),
        "failed": sum(result["failed"] for result in results),
    }))


if __name__ == "__main__":
    main()

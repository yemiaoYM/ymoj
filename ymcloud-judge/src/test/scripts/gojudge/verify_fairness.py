#!/usr/bin/env python3
"""固定四入口样本：每档预热 5 次、测量至少 30 次；保留原始指标并执行门槛比较。"""

import argparse
import hashlib
import json
import math
import os
import pathlib
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from verify_runtime import command, docker_json, request


ROOT = pathlib.Path(__file__).resolve().parents[5]
OUT = ROOT / "target/phase3-verification/fairness"
HELPER = "/usr/local/bin/ymcloud-output-limit"
# 本脚本只验收默认部署；高配需先扩展采样参数，不能沿用默认样本预算冒充验收。
DEFAULT_DEPLOYMENT = {
    "YMCLOUD_WORKER_CONCURRENCY": 2,
    "YMCLOUD_GO_JUDGE_CONCURRENCY": 1,
    "YMCLOUD_GO_JUDGE_CPUS": 1.0,
    "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES": 1073741824,
    "YMCLOUD_GO_JUDGE_SERVICE_PIDS": 256,
    "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB": 524288,
    "YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES": 536870912,
    "YMCLOUD_GO_JUDGE_COMPILE_WALL_MS": 10000,
    "YMCLOUD_GO_JUDGE_PROCESS_LIMIT": 64,
    "YMCLOUD_GO_JUDGE_CPU_RATE": 1000,
    "YMCLOUD_GO_JUDGE_MAX_FILE_BYTES": 67108864,
    "YMCLOUD_GO_JUDGE_MAX_OUTPUT_BYTES": 33554432,
}
SAMPLES = {
    "C": ("main.c", """#include <stdio.h>
int main(void) {
    volatile unsigned long long sum = 0;
    for (int i = 0; i < 200000000; i++) {
        sum += i;
    }
    puts("ok");
    return 0;
}
""", ["/usr/bin/gcc", "-std=c11", "-O2", "-pipe", "main.c", "-o", "program", "-lm"],
      "program", ["./program"]),
    "C++": ("main.cpp", """#include <cstdio>
int main() {
    volatile unsigned long long sum = 0;
    for (int i = 0; i < 200000000; i++) {
        sum += i;
    }
    std::puts("ok");
    return 0;
}
""", ["/usr/bin/g++", "-std=c++17", "-O2", "-pipe", "main.cpp", "-o", "program"],
      "program", ["./program"]),
    "Java": ("Main.java", """public class Main {
    private static volatile long sum;

    public static void main(String[] args) {
        for (int i = 0; i < 20000000; i++) {
            sum += i;
        }
        System.out.println("ok");
    }
}
""", ["/bin/sh", "-c",
      "mkdir classes && /usr/bin/javac -J-Xmx256m -J-XX:+UseSerialGC -J-Djava.io.tmpdir=/w -encoding UTF-8"
      " -d classes Main.java && /usr/bin/jar -J-Xmx128m -J-Djava.io.tmpdir=/w cf program.jar -C classes ."],
      "program.jar", ["/usr/bin/java", "-Xms16m", "-Xmx524288k", "-XX:+UseSerialGC",
                      "-XX:ActiveProcessorCount=1", "-XX:+ExitOnOutOfMemoryError", "-cp", "program.jar", "Main"]),
    "Python": ("main.py", """total = 0
for i in range(2000000):
    total += i
print('ok')
""", ["/usr/bin/python3", "-I", "-m", "py_compile", "main.py"], "main.py", ["/usr/bin/python3", "-I", "main.py"]),
}
METRICS = """
import json,pathlib
stat=pathlib.Path('/proc/stat').read_text().splitlines()[0].split()[1:]
cpu={k:int(v) for k,v in (line.split() for line in pathlib.Path('/sys/fs/cgroup/cpu.stat').read_text().splitlines())}
limits={k:pathlib.Path('/sys/fs/cgroup',k).read_text().strip() for k in ('cpu.max','memory.max','memory.swap.max','pids.max')}
print(json.dumps({'cpuTicks':sum(map(int,stat[:8])),'stealTicks':int(stat[7]),'throttling':cpu,'limits':limits}))
"""


def percentile(values, percent):
    return sorted(values)[max(0, math.ceil(len(values) * percent) - 1)]


def delete_file(endpoint, token, file_id):
    req = urllib.request.Request(endpoint + "/file/" + urllib.parse.quote(file_id, safe=""), method="DELETE", headers={
        "Authorization": "Bearer " + token,
    })
    try:
        with urllib.request.urlopen(req, timeout=10):
            pass
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise


def track_file_ids(responses, file_ids):
    results = responses if isinstance(responses, list) else [responses]
    for result in results:
        if not isinstance(result, dict) or not isinstance(result.get("fileIds"), dict):
            continue
        file_ids.update(value for value in result["fileIds"].values()
                        if isinstance(value, str) and value)


def task_parameters(args):
    deployment = {}
    for key, default in DEFAULT_DEPLOYMENT.items():
        try:
            value = type(default)(os.environ.get(key, str(default)))
        except ValueError:
            raise SystemExit(f"部署参数不是有效数字: {key}") from None
        if key in ("YMCLOUD_WORKER_CONCURRENCY", "YMCLOUD_GO_JUDGE_CONCURRENCY") and value == 0:
            value = default
        if value != default:
            raise SystemExit(f"公平性脚本仅支持默认部署预算，请先扩展采样参数: {key}")
        deployment[key] = value
    if not 1 <= args.limit_ms <= 60000:
        raise SystemExit("样本限时必须为 1 至 60000 毫秒")
    return {
        "deployment": deployment,
        "compile": command([], deployment["YMCLOUD_GO_JUDGE_COMPILE_MEMORY_BYTES"],
                           deployment["YMCLOUD_GO_JUDGE_COMPILE_WALL_MS"] * 1_000_000)["cmd"][0],
        "run": command([], deployment["YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB"] * 1024,
                       args.limit_ms * 1_000_000)["cmd"][0],
        "commands": {name: {"compile": sample[2], "artifact": sample[3], "run": sample[4]}
                     for name, sample in SAMPLES.items()},
    }


def verify_service_limits(snapshot):
    limits = snapshot["limits"]
    quota, period = limits["cpu.max"].split()
    if quota == "max" or int(quota) != int(period) or int(period) <= 0:
        raise SystemExit("公平性脚本仅支持实际服务 CPU 预算为 1 核的部署")
    if limits["memory.max"] != "1073741824" or limits["memory.swap.max"] != "0" or limits["pids.max"] != "256":
        raise SystemExit("公平性脚本仅支持实际服务内存 1GiB、禁用 swap、进程数 256 的部署")


def collect(args):
    # 开始重跑即废弃旧档位及旧结论；任何失败都不能继续使用上次的通过报告。
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{args.tier}.json").unlink(missing_ok=True)
    (OUT / "acceptance.json").unlink(missing_ok=True)
    token = os.environ["YMCLOUD_GO_JUDGE_TOKEN"]
    if args.samples < 30:
        raise SystemExit("每档至少测量 30 次")
    if args.tier in ("normal", "full") and not args.load_description:
        raise SystemExit("必须记录实际业务负载或满载来源 --load-description")
    parameters = task_parameters(args)
    max_output_bytes = parameters["deployment"]["YMCLOUD_GO_JUDGE_MAX_OUTPUT_BYTES"]
    max_file_bytes = parameters["deployment"]["YMCLOUD_GO_JUDGE_MAX_FILE_BYTES"]
    before = docker_json(args.container, METRICS)
    verify_service_limits(before)
    report = {"tier": args.tier, "samples": args.samples, "warmups": 5,
              "loadDescription": args.load_description or "无外加业务负载", "languages": {},
              "version": request(args.endpoint, token, "/version"), "before": before,
              "taskParameters": parameters}
    for name, (filename, source, compile_args, artifact, run_args) in SAMPLES.items():
        compile_body = command([HELPER, str(max_output_bytes), str(max_file_bytes), *compile_args], 536870912)
        compile_body["cmd"][0].update({
            "copyIn": {filename: {"content": source}}, "copyOutCached": [artifact + "?"],
            "copyOutMax": max_file_bytes,
        })
        file_ids = set()
        rows = []
        try:
            started = time.monotonic()
            responses = request(args.endpoint, token, "/run", compile_body)
            track_file_ids(responses, file_ids)
            compile_ms = (time.monotonic() - started) * 1000
            assert isinstance(responses, list) and len(responses) == 1, "编译响应数量无效"
            compiled = responses[0]
            assert compiled.get("files", {}).get("launcher", "") == "", "编译启动器基础设施异常"
            assert compiled["status"] == "Accepted" and compiled["exitStatus"] == 0, f"{name} 编译失败"
            file_id = compiled["fileIds"][artifact]
            body = command([HELPER, str(max_output_bytes), str(max_file_bytes), *run_args], 536870912,
                           args.limit_ms * 1_000_000)
            body["cmd"][0]["copyIn"] = {artifact: {"fileId": file_id}}
            for index in range(args.samples + 5):
                started = time.monotonic()
                responses = request(args.endpoint, token, "/run", body)
                track_file_ids(responses, file_ids)
                assert isinstance(responses, list) and len(responses) == 1, "执行响应数量无效"
                result = responses[0]
                assert result.get("files", {}).get("launcher", "") == "", "运行启动器基础设施异常"
                elapsed_ms = (time.monotonic() - started) * 1000
                row = {"cpuMs": result["time"] / 1e6, "wallMs": result["runTime"] / 1e6,
                       "memoryKiB": result["memory"] / 1024, "roundTripMs": elapsed_ms,
                       "waitingAndTransportMs": max(0, elapsed_ms - result["runTime"] / 1e6),
                       "status": result["status"], "exitStatus": result["exitStatus"],
                       "correctOutput": result.get("files", {}).get("stdout") == "ok\n"}
                if index >= 5:
                    rows.append(row)
        finally:
            original_error = sys.exc_info()[0] is not None
            cleanup_errors = 0
            for file_id in sorted(file_ids):
                try:
                    delete_file(args.endpoint, token, file_id)
                except Exception:
                    cleanup_errors += 1
            if cleanup_errors:
                message = f"公平性测试有 {cleanup_errors} 个缓存文件清理失败，需检查服务并依赖 TTL 兜底"
                if original_error:
                    print(message, file=sys.stderr)
                else:
                    raise RuntimeError(message)
        summary = {}
        for metric in ("cpuMs", "wallMs", "memoryKiB", "waitingAndTransportMs"):
            values = [row[metric] for row in rows]
            summary[metric] = {"p50": statistics.median(values), "p95": percentile(values, .95)}
        report["languages"][name] = {
            "sourceSha256": hashlib.sha256(source.encode()).hexdigest(), "limitMs": args.limit_ms,
            "compileMs": compile_ms, "summary": summary, "rows": rows,
        }
        print(f"{args.tier} {name}: 完成 {len(rows)} 次测量")
    after = docker_json(args.container, METRICS)
    verify_service_limits(after)
    assert after["limits"] == before["limits"], "采样期间服务资源发生变化"
    report["after"] = after
    total = after["cpuTicks"] - before["cpuTicks"]
    report["cpuStealPercent"] = (after["stealTicks"] - before["stealTicks"]) * 100 / max(1, total)
    report["throttlingDelta"] = {key: value - before["throttling"].get(key, 0)
                                 for key, value in after["throttling"].items()}
    report["complete"] = True
    (OUT / f"{args.tier}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def compare():
    (OUT / "acceptance.json").unlink(missing_ok=True)
    reports = {tier: json.loads((OUT / f"{tier}.json").read_text(encoding="utf-8"))
               for tier in ("idle-a", "idle-b", "normal", "full")}
    for tier, report in reports.items():
        assert report.get("complete") is True and report.get("tier") == tier, "缺少完整采样报告，请重新采集"
        assert report["samples"] >= 30 and report["warmups"] >= 5, "采样次数或预热次数不足"
        verify_service_limits(report["before"])
        verify_service_limits(report["after"])
        assert report["before"]["limits"] == report["after"]["limits"], "采样期间部署资源不一致"
    assert len({json.dumps(report["version"], sort_keys=True) for report in reports.values()}) == 1, "比较服务版本不一致"
    assert len({json.dumps(report["before"]["limits"], sort_keys=True) for report in reports.values()}) == 1, "比较部署资源不一致"
    assert len({json.dumps(report["taskParameters"], sort_keys=True) for report in reports.values()}) == 1, "比较任务执行参数不一致"
    checks = []
    for name in SAMPLES:
        data = {tier: report["languages"][name] for tier, report in reports.items()}
        assert all(len(record["rows"]) == reports[tier]["samples"] for tier, record in data.items()), "实际采样数与报告不一致"
        assert len({record["sourceSha256"] for record in data.values()}) == 1, "比较样本不一致"
        assert len({record["limitMs"] for record in data.values()}) == 1, "比较时限不一致"
        idle_a = data["idle-a"]["summary"]["wallMs"]["p95"]
        idle_b = data["idle-b"]["summary"]["wallMs"]["p95"]
        normal = data["normal"]["summary"]["wallMs"]["p95"]
        checks.append({"language": name, "check": "idleP95Drift", "value": abs(idle_b / idle_a - 1),
                       "limit": .10, "pass": abs(idle_b / idle_a - 1) <= .10})
        checks.append({"language": name, "check": "normalP95Drift", "value": normal / idle_a - 1,
                       "limit": .20, "pass": normal <= idle_a * 1.20})
        for tier in ("idle-a", "idle-b", "normal"):
            rows = data[tier]["rows"]
            checks.append({"language": name, "check": f"{tier}Correctness",
                           "pass": all(row["status"] == "Accepted" and row["exitStatus"] == 0
                                       and row["correctOutput"] and row["wallMs"] <= data[tier]["limitMs"]
                                       and row["memoryKiB"] <= reports[tier]["taskParameters"]["run"]["memoryLimit"] / 1024
                                       for row in rows)})
        headroom = max(row["wallMs"] for row in data["idle-a"]["rows"]) <= data["idle-a"]["limitMs"] * .8
        checks.append({"language": name, "check": "atLeast20PercentHeadroom", "pass": headroom})
    result = {"passed": all(check["pass"] for check in checks), "checks": checks,
              "fullLoadNote": "满载只记录容量边界，不能用自动增加题目时限掩盖漂移"}
    (OUT / "acceptance.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("公平性门槛通过" if result["passed"] else "公平性门槛未通过；调整支持负载或资源配置后重新验收")
    return 0 if result["passed"] else 1


def main():
    if not __debug__:
        raise SystemExit("验收脚本不能使用 Python -O 关闭断言")
    parser = argparse.ArgumentParser(description="仅支持默认部署预算；请导出与部署 .env 相同的环境变量后运行。")
    parser.add_argument("--container")
    parser.add_argument("--endpoint", default="http://127.0.0.1:5050")
    parser.add_argument("--tier", choices=("idle-a", "idle-b", "normal", "full"))
    parser.add_argument("--samples", type=int, default=30)
    parser.add_argument("--limit-ms", type=int, default=1000)
    parser.add_argument("--load-description")
    parser.add_argument("--compare", action="store_true")
    args = parser.parse_args()
    if args.compare:
        raise SystemExit(compare())
    if os.environ.get("YMCLOUD_RUN_GO_JUDGE_TESTS") != "true" or not args.container or not args.tier:
        raise SystemExit("需要专属验收服务、--container、--tier 和 YMCLOUD_RUN_GO_JUDGE_TESTS=true")
    collect(args)


if __name__ == "__main__":
    main()

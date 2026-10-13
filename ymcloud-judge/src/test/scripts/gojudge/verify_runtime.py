#!/usr/bin/env python3
"""专属 go-judge 服务验收：限额继承、鉴权、取消后的子孙进程实际回收。"""

import argparse
import http.client
import json
import os
import pathlib
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def request(endpoint, token, path, body=None):
    headers = {"Authorization": "Bearer " + token}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(endpoint + path, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.load(response)


def command(args, memory=134217728, wall=10_000_000_000):
    return {"cmd": [{
        "args": args,
        "env": ["PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "HOME=/w"],
        "files": [{"content": ""}, {"name": "stdout", "max": 1048576, "pipe": True},
                  {"name": "stderr", "max": 1048576, "pipe": True},
                  {"name": "launcher", "max": 256, "pipe": True}],
        "cpuLimit": (wall + 100_000_000) * 2, "clockLimit": wall, "memoryLimit": memory,
        "procLimit": 64, "cpuRateLimit": 1000, "copyOutMax": 16777216, "copyOutTruncate": False,
    }], "pipeMapping": []}


def docker_json(container, script, *args):
    raw = subprocess.check_output(
        ["docker", "exec", container, "python3", "-c", script, *args], text=True,
    )
    return json.loads(raw)


SNAPSHOT = """
import json,pathlib,sys
root=pathlib.Path('/sys/fs/cgroup')
limits={name:(root/name).read_text().strip() for name in
        ('cpu.max','memory.max','memory.swap.max','pids.max','pids.current')}
marker=sys.argv[1]
matches=[]
for path in pathlib.Path('/proc').glob('[0-9]*/cmdline'):
    try:
        args=path.read_bytes().split(b'\\0')
        if marker.encode() not in args or b'-c' not in args or b'python3' not in args[0]:
            continue
        if path.parent.name == str(__import__('os').getpid()):
            continue
        group=(path.parent/'cgroup').read_text().strip().split(':')[-1]
        current=root/group.lstrip('/')
        ancestors=[]
        while current==root or root in current.parents:
            ancestors.append({name:(current/name).read_text().strip() for name in
                              ('cpu.max','memory.max','pids.max') if (current/name).exists()})
            if current==root: break
            current=current.parent
        matches.append({'pid':int(path.parent.name),'cgroup':group,'ancestors':ancestors})
    except (FileNotFoundError,ProcessLookupError,PermissionError): pass
print(json.dumps({'limits':limits,'processes':matches}))
"""


def verify_restart(args, token):
    body = command(["/bin/sh", "-c", "printf phase3-fixture > fixture"])
    body["cmd"][0]["copyOutCached"] = ["fixture"]
    result = request(args.endpoint, token, "/run", body)[0]
    assert result["status"] == "Accepted" and result["exitStatus"] == 0
    file_id = result["fileIds"]["fixture"]
    file_url = args.endpoint + "/file/" + urllib.parse.quote(file_id, safe="")
    headers = {"Authorization": "Bearer " + token}
    leftover = "phase3-leftover-" + uuid.uuid4().hex
    try:
        with urllib.request.urlopen(urllib.request.Request(file_url, headers=headers), timeout=5) as response:
            assert response.read() == b"phase3-fixture"
        docker_json(args.container, """
import json,pathlib,sys
pathlib.Path('/var/lib/go-judge/run',sys.argv[1]).write_text('fixture',encoding='utf-8')
print(json.dumps(True))
""", leftover)
        subprocess.run(["docker", "restart", "--time", "10", args.container],
                       check=True, stdout=subprocess.DEVNULL)
        ready = False
        for _ in range(50):
            try:
                request(args.endpoint, token, "/file")
                ready = True
                break
            except (OSError, urllib.error.URLError, http.client.HTTPException):
                time.sleep(0.1)
        assert ready, "专属服务重启后未恢复健康"
        try:
            with urllib.request.urlopen(urllib.request.Request(file_url, headers=headers), timeout=5):
                raise AssertionError("服务重启后旧文件 ID 仍然可用")
        except urllib.error.HTTPError as error:
            assert error.code == 404
        remaining = docker_json(args.container, """
import json,pathlib,sys
print(json.dumps(pathlib.Path('/var/lib/go-judge/run',sys.argv[1]).exists()))
""", leftover)
        assert remaining is False, "启动未清理异常退出遗留临时文件"
        assert request(args.endpoint, token, "/run", command(["/bin/true"]))[0]["status"] == "Accepted"
        return {"serviceRestart": "PASS", "lostFile404": "PASS", "startupTemporaryCleanup": "PASS"}
    finally:
        original_error = sys.exc_info()[0] is not None
        try:
            with urllib.request.urlopen(urllib.request.Request(file_url, headers=headers, method="DELETE"), timeout=5):
                pass
        except Exception as error:
            if isinstance(error, urllib.error.HTTPError) and error.code == 404:
                pass
            elif original_error:
                print("重启验收清理未完成，请检查专属服务健康和文件 TTL", file=sys.stderr)
            else:
                raise


def verify(args):
    token = os.environ["YMCLOUD_GO_JUDGE_TOKEN"]
    for bad in ("", "invalid-" + uuid.uuid4().hex):
        try:
            request(args.endpoint, bad, "/file")
            raise AssertionError("无效令牌未被拒绝")
        except urllib.error.HTTPError as error:
            assert error.code in (401, 403)
    request(args.endpoint, token, "/file")
    config = request(args.endpoint, token, "/config")
    runner = config["runnerConfig"]
    assert runner["cgroupType"] == 2
    assert runner["seccomp"] is True
    assert {"cpu", "memory", "pids"}.issubset(runner["cgroupControllers"])
    marker = "ymcloud-cancel-" + uuid.uuid4().hex
    # 预热沙箱，避免 prefork 的初始化影响回收前后进程基线。
    assert request(args.endpoint, token, "/run", command(["/bin/true"]))[0]["status"] == "Accepted"
    baseline = docker_json(args.container, SNAPSHOT, marker)
    assert baseline["limits"]["memory.max"] == str(args.memory_bytes)
    assert baseline["limits"]["memory.swap.max"] == "0"
    assert baseline["limits"]["pids.max"] == str(args.service_pids)
    quota, period = baseline["limits"]["cpu.max"].split()
    assert quota != "max" and abs(int(quota) / int(period) - args.cpus) < 0.001

    program = "import os,time\nfor _ in range(3):\n if os.fork()==0:\n  os.setsid();time.sleep(45);os._exit(0)\ntime.sleep(45)"
    parsed = urllib.parse.urlsplit(args.endpoint)
    assert parsed.scheme == "http" and parsed.hostname in ("127.0.0.1", "localhost")
    connection = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=5)
    body = json.dumps(command([
        "/usr/local/bin/ymcloud-output-limit", "1048576", "16777216",
        "/usr/bin/python3", "-c", program, marker,
    ], wall=60_000_000_000))
    connection.request("POST", "/run", body, {
        "Authorization": "Bearer " + token, "Content-Type": "application/json",
    })
    active = None
    try:
        for _ in range(50):
            active = docker_json(args.container, SNAPSHOT, marker)
            if len(active["processes"]) >= 4:
                break
            time.sleep(0.1)
        assert active and len(active["processes"]) >= 4, "未观察到测试父进程及三个 setsid 子进程"
        for process in active["processes"]:
            ancestors = process["ancestors"]
            memory_caps = [int(a["memory.max"]) for a in ancestors if a.get("memory.max", "max") != "max"]
            pid_caps = [int(a["pids.max"]) for a in ancestors if a.get("pids.max", "max") != "max"]
            cpu_caps = [int(a["cpu.max"].split()[0]) / int(a["cpu.max"].split()[1])
                        for a in ancestors if a.get("cpu.max", "max").split()[0] != "max"]
            # 官方默认额外内存保护缓冲为 16KiB；业务结果仍按原 memoryLimit 判断。
            assert memory_caps and min(memory_caps) <= 134217728 + 16384, memory_caps
            assert pid_caps and min(pid_caps) <= 64
            assert cpu_caps and min(cpu_caps) <= 1.0
            task_quota, task_period = ancestors[0]["cpu.max"].split()
            assert task_quota != "max" and int(task_quota) / int(task_period) == 1.0
        # 关闭已发送完整请求的 TCP 连接，必须在其 60 秒截止前实际回收进程。
        connection.sock.shutdown(socket.SHUT_RDWR)
    finally:
        connection.close()
    started = time.monotonic()
    recovered = None
    while time.monotonic() - started < 5:
        recovered = docker_json(args.container, SNAPSHOT, marker)
        if not recovered["processes"] and int(recovered["limits"]["pids.current"]) <= int(baseline["limits"]["pids.current"]):
            break
        time.sleep(0.1)
    assert recovered and not recovered["processes"], "取消后仍存在子孙进程"
    assert int(recovered["limits"]["pids.current"]) <= int(baseline["limits"]["pids.current"]), "pids.current 未回基线"
    assert request(args.endpoint, token, "/run", command(["/bin/true"]))[0]["status"] == "Accepted"
    return {"auth": "PASS", "cgroupV2": "PASS", "seccomp": "PASS", "limitInheritance": "PASS",
            "cancellation": "PASS", "reclaimMillis": round((time.monotonic() - started) * 1000),
            "baseline": baseline, "active": active, "recovered": recovered}


def main():
    if not __debug__:
        raise SystemExit("验收脚本不能使用 Python -O 关闭断言")
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", required=True)
    parser.add_argument("--endpoint", default="http://127.0.0.1:5050")
    parser.add_argument("--memory-bytes", type=int, default=1073741824)
    parser.add_argument("--service-pids", type=int, default=256)
    parser.add_argument("--cpus", type=float, default=1.0)
    parser.add_argument("--restart", action="store_true",
                        help="仅在专属服务无其他验收任务时重启，验证旧文件404与启动清理")
    args = parser.parse_args()
    if os.environ.get("YMCLOUD_RUN_GO_JUDGE_TESTS") != "true":
        raise SystemExit("请对专属验收服务设置 YMCLOUD_RUN_GO_JUDGE_TESTS=true")
    destination = pathlib.Path(__file__).resolve().parents[5] / "target/phase3-verification"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "runtime-verification.json").unlink(missing_ok=True)
    report = verify(args)
    if args.restart:
        report.update(verify_restart(args, os.environ["YMCLOUD_GO_JUDGE_TOKEN"]))
    (destination / "runtime-verification.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print("鉴权、cgroup 限额继承和取消后子孙进程回收通过")


if __name__ == "__main__":
    main()

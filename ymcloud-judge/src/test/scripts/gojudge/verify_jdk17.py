#!/usr/bin/env python3
"""Run the packaged HTTP client on JDK 17 against a dedicated local sandbox."""

import argparse
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[5]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True, help="Dedicated go-judge test container")
    args = parser.parse_args()
    support_path = ROOT / "ymcloud-web/src/test/scripts/judge/verify_linux.py"
    spec = importlib.util.spec_from_file_location("linux_verification", support_path)
    support = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(support)
    support.require(os.environ.get("YMCLOUD_WORKER_LINUX_TEST") == "true", "Explicit Linux test opt-in required")
    support.require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}", args.container), "Invalid container name")
    support.require(len(os.environ.get("YMCLOUD_GO_JUDGE_TOKEN", "")) >= 32, "Dedicated sandbox token required")
    image = os.environ.get("YMCLOUD_GO_JUDGE_IMAGE", "")
    support.require(re.fullmatch(r"sha256:[0-9a-f]{64}", image)
                    or re.fullmatch(r"[^\s]+@sha256:[0-9a-f]{64}", image), "Immutable runtime image required")
    support.local_daemon()
    identifier = uuid.uuid4().hex
    target = (ROOT / "target/phase3-verification/jdk17").resolve()
    fixture = target / identifier
    container = None
    report = {"ok": False, "java": 17, "cleanup": False}
    try:
        fixture.mkdir(parents=True, exist_ok=False)
        if os.name != "nt":
            fixture.chmod(0o755)
        support.extract_worker(ROOT / "ymcloud-judge/target/ymcloud-judge.jar", fixture)
        shutil.copyfile(ROOT / "ymcloud-judge/src/test/java/com/ymcloud/judge/GoJudgeJdk17Smoke.java",
                        fixture / "GoJudgeJdk17Smoke.java")
        if os.name != "nt":
            (fixture / "GoJudgeJdk17Smoke.java").chmod(0o444)
        script = ('mkdir /state/classes && javac --release 17 -cp "/fixture/classes:/fixture/lib/*" '
                  '-d /state/classes /fixture/GoJudgeJdk17Smoke.java && '
                  'java -Xmx192m -cp "/state/classes:/fixture/classes:/fixture/lib/*" '
                  'com.ymcloud.judge.GoJudgeJdk17Smoke')
        container = support.command([
            "docker", "create", "--rm", "--name", "ymcloud-jdk17-" + identifier,
            "--label", "com.ymcloud.phase3-linux-test=" + identifier,
            "--user", "1000:1000", "--read-only", "--network", "container:" + args.container,
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true", "--pids-limit", "96",
            "--memory", "512m", "--memory-swap", "512m", "--cpus", "1",
            "--tmpfs", "/state:rw,nosuid,nodev,size=128m,uid=1000,gid=1000,mode=0700",
            "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=64m,uid=1000,gid=1000,mode=1777",
            "--mount", "type=bind,source=" + str(fixture) + ",target=/fixture,readonly",
            "--env", "YMCLOUD_GO_JUDGE_URL=http://127.0.0.1:5050",
            "--env", "YMCLOUD_GO_JUDGE_TOKEN",
            "--entrypoint", "/bin/sh", image, "-c", script])
        result = subprocess.run(["docker", "start", "-a", container], capture_output=True,
                                text=True, encoding="utf-8", timeout=180, **support.hidden_process_options())
        (target / "run.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        support.require(result.returncode == 0, "JDK 17 client regression failed")
        support.require("JDK 17 smoke passed: startup, four languages, interruption and bounded recovery"
                        in result.stdout, "JDK 17 assertions did not complete")
        report["ok"] = True
    except Exception as error:
        report["errorType"] = type(error).__name__
    finally:
        try:
            support.cleanup_container(container, identifier)
            support.require(fixture.resolve().parent == target, "Unsafe fixture cleanup path")
            if fixture.exists():
                shutil.rmtree(fixture)
            report["cleanup"] = True
        except Exception as error:
            report["ok"] = False
            report["cleanupErrorType"] = type(error).__name__
        target.mkdir(parents=True, exist_ok=True)
        (target / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

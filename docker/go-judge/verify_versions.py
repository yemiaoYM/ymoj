#!/usr/bin/env python3
"""镜像构建时验证完整包版本与三个工具链，输出不包含环境或凭据。"""

import pathlib
import re
import subprocess
import sys


def output(*args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT, text=True).strip()


def main():
    for spec in pathlib.Path("/opt/ymcloud/packages.lock").read_text(encoding="utf-8").splitlines():
        package, version = spec.split("=", 1)
        actual = output("dpkg-query", "-W", "-f=${Version}", package)
        if actual != version:
            raise SystemExit(f"软件包版本不匹配: {package}")
    checks = [
        (("javac", "-version"), r"javac 17\."),
        (("java", "-version"), r'openjdk version "17\.'),
        (("gcc", "-dumpfullversion"), r"12\."),
        (("g++", "-dumpfullversion"), r"12\."),
        (("python3", "--version"), r"Python 3\.11\."),
        (("/usr/local/bin/ymcloud-output-limit", "--version"), r"^ymcloud-output-limit 2$"),
    ]
    if "--toolchain-only" not in sys.argv:
        checks.append((("/opt/go-judge", "-version"), r"\bv?1\.13\.3\b"))
    for command, expected in checks:
        if not re.search(expected, output(*command)):
            raise SystemExit(f"工具链版本不匹配: {command[0]}")
    print("版本验证通过：OpenJDK 17 / GCC、G++ 12 / Python 3.11")


if __name__ == "__main__":
    main()

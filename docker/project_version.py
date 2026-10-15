#!/usr/bin/env python3
"""读取父 POM 中唯一的项目发布版本，供镜像及发布工具复用。"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")


def project_version(root=None):
    """只接受父 POM 的 revision，避免构建脚本维护第二份版本号。"""
    directory = ROOT if root is None else pathlib.Path(root)
    project = ET.fromstring((directory / "pom.xml").read_text(encoding="utf-8"))
    if project.findtext("m:version", namespaces=NAMESPACE) != "${revision}":
        raise ValueError("父 POM 的项目版本必须引用 ${revision}")
    revisions = project.findall("m:properties/m:revision", NAMESPACE)
    if len(revisions) != 1:
        raise ValueError("父 POM 必须且只能定义一个 revision")
    value = (revisions[0].text or "").strip()
    if len(value) > 100 or not VERSION.fullmatch(value):
        raise ValueError("revision 必须为可用于镜像标签的三段版本号，可附加连字符预发布标识")
    return value


if __name__ == "__main__":
    try:
        print(project_version())
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"无法读取项目版本：{error}", file=sys.stderr)
        sys.exit(1)

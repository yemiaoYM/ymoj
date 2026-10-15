#!/usr/bin/env python3
"""保存实际构建导出摘要与 image ID；不把本地 config 摘要冒充仓库 manifest。"""

import json
import pathlib
import re
import subprocess
import sys


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from project_version import project_version


def main():
    destination = pathlib.Path(sys.argv[1])
    version = project_version()
    records = {}
    for target in ("runtime", "worker"):
        image_id = (destination / f"{target}.iid").read_text(encoding="utf-8").strip()
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", image_id):
            raise SystemExit("镜像 ID 无效，必须先完成构建")
        image = json.loads(subprocess.check_output(
            ["docker", "image", "inspect", image_id], text=True,
        ))[0]
        if image["Id"] != image_id or image["Os"] != "linux" or image["Architecture"] != "amd64":
            raise SystemExit("运行镜像与构建锁不一致")
        labels = image.get("Config", {}).get("Labels") or {}
        if labels.get("org.opencontainers.image.version") != version:
            raise SystemExit("运行镜像版本与父 POM 不一致，必须重新构建")
        metadata = json.loads((destination / f"{target}.metadata.json").read_text(encoding="utf-8"))
        digest = metadata.get("containerimage.digest", "")
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise SystemExit("Buildx 未提供导出摘要，拒绝生成部署锁")
        if metadata.get("containerimage.config.digest") != image_id:
            raise SystemExit("构建 config digest 与实际 image ID 不一致")
        records[target] = {
            "version": version,
            "imageId": image_id,
            "exporterDigest": digest,
            "digestKind": "docker-config" if digest == image_id else "image-manifest",
        }
    records["version"] = version
    (destination / "images.lock.json").write_text(
        json.dumps(records, indent=2) + "\n", encoding="utf-8",
    )
    (destination / "images.env").write_text(
        f"YMCLOUD_GO_JUDGE_IMAGE={records['runtime']['imageId']}\n"
        f"YMCLOUD_WORKER_IMAGE={records['worker']['imageId']}\n", encoding="utf-8",
    )


if __name__ == "__main__":
    main()

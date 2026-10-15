#!/usr/bin/env python3
"""仅在容器内访问无敏感内容的 Web 就绪状态。"""

import os
import sys
import urllib.error
import urllib.request


def main():
    try:
        token = os.environ.get("YMCLOUD_WORKER_SERVICE_TOKEN", "")
        if not token:
            return 1
        request = urllib.request.Request(
            "http://127.0.0.1:9091/internal/judge-worker/health",
            headers={"X-Judge-Worker-Token": token},
        )
        with urllib.request.urlopen(request, timeout=8) as response:
            return 0 if response.status == 200 and not response.read(1) else 1
    except (OSError, ValueError, urllib.error.URLError):
        return 1


if __name__ == "__main__":
    sys.exit(main())

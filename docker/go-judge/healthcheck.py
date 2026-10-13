#!/usr/bin/env python3
"""健康探针只访问鉴权接口，任何失败均不打印响应或凭据。"""

import os
import sys
import urllib.request


def main():
    request = urllib.request.Request("http://127.0.0.1:5050/file", headers={
        "Authorization": "Bearer " + os.environ.get("YMCLOUD_GO_JUDGE_TOKEN", ""),
    })
    try:
        with urllib.request.urlopen(request, timeout=3) as response:
            return 0 if response.status == 200 else 1
    except Exception:
        return 1


if __name__ == "__main__":
    sys.exit(main())

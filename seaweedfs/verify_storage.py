#!/usr/bin/env python3
"""Repeatable local S3 security and throughput checks, using Python's standard library.

AWS SigV4 signing is implemented only for this isolated deployment test harness;
application traffic uses the maintained AWS SDK. Credentials are never printed.
"""

import argparse
import base64
import concurrent.futures
import datetime
import hashlib
import hmac
import http.client
import json
import math
import os
import pathlib
import statistics
import ssl
import time
import urllib.parse
import uuid
import xml.etree.ElementTree as ET


DEPLOYMENT_ROOT = pathlib.Path(__file__).resolve().parent
PROJECT_ROOT = DEPLOYMENT_ROOT.parent
REPORT_ROOT = PROJECT_ROOT / "target/test-output/seaweedfs"


def read_environment():
    values = {}
    with (DEPLOYMENT_ROOT / ".env").open(encoding="utf-8") as source:
        for line in source:
            line = line.strip()
            if line and not line.startswith("#"):
                name, value = line.split("=", 1)
                values[name] = value
    return values


class S3Client:
    def __init__(self, endpoint, access_key, secret_key, region):
        self.endpoint = endpoint.rstrip("/")
        self.url = urllib.parse.urlparse(endpoint)
        self.host = self.url.netloc
        if self.url.scheme not in ["http", "https"] or self.url.path not in ["", "/"]:
            raise ValueError("expected an HTTP(S) S3 endpoint without a path prefix")
        self.ssl_context = ssl.create_default_context() if self.url.scheme == "https" else None
        self.access_key = access_key
        self.secret_key = secret_key
        self.region = region

    def request(self, method, bucket="", key="", payload=b"", authenticated=True,
                secret=None, access_key=None, signed_payload=None, time_offset=0, content_md5=True):
        path = "/" + urllib.parse.quote(bucket, safe="")
        if key:
            path += "/" + urllib.parse.quote(key, safe="/~")
        now = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=time_offset)
        timestamp = now.strftime("%Y%m%dT%H%M%SZ")
        date = now.strftime("%Y%m%d")
        content_hash = hashlib.sha256(payload if signed_payload is None else signed_payload).hexdigest()
        headers = {"Host": self.host, "x-amz-content-sha256": content_hash, "x-amz-date": timestamp}
        if content_md5 and method in ["PUT", "POST"]:
            expected_payload = payload if signed_payload is None else signed_payload
            digest = hashlib.md5(expected_payload, usedforsecurity=False).digest()
            headers["Content-MD5"] = base64.b64encode(digest).decode("ascii")
        if authenticated:
            signing_headers = {name.lower(): value for name, value in headers.items()}
            canonical_headers = "".join(f"{name}:{signing_headers[name]}\n" for name in sorted(signing_headers))
            signed_headers = ";".join(sorted(signing_headers))
            canonical_request = "\n".join([method, path, "", canonical_headers, signed_headers, content_hash])
            scope = f"{date}/{self.region}/s3/aws4_request"
            string_to_sign = "\n".join([
                "AWS4-HMAC-SHA256", timestamp, scope,
                hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
            ])
            signing_key = ("AWS4" + (self.secret_key if secret is None else secret)).encode("utf-8")
            for component in [date, self.region, "s3", "aws4_request"]:
                signing_key = hmac.new(signing_key, component.encode("utf-8"), hashlib.sha256).digest()
            signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
            credential = self.access_key if access_key is None else access_key
            headers["Authorization"] = (
                f"AWS4-HMAC-SHA256 Credential={credential}/{scope}, "
                f"SignedHeaders={signed_headers}, Signature={signature}"
            )
        data = payload if method in ["PUT", "POST"] else None
        # Independent HTTP connections avoid shared mutable state and ambient proxies.
        # Build a TLS context only for HTTPS, rather than on every loopback HTTP request.
        if self.url.scheme == "https":
            connection = http.client.HTTPSConnection(self.url.hostname, self.url.port, timeout=60,
                                                      context=self.ssl_context)
        else:
            connection = http.client.HTTPConnection(self.url.hostname, self.url.port, timeout=60)
        try:
            connection.request(method, path, body=data, headers=headers)
            response = connection.getresponse()
            return response.status, response.read(), dict(response.headers)
        finally:
            connection.close()


def require(response, statuses, message):
    status, body, _ = response
    if status not in statuses:
        error_code = ""
        try:
            error_code = ET.fromstring(body).findtext("Code") or ""
        except ET.ParseError:
            pass
        raise AssertionError(f"{message}: HTTP {status}, code={error_code}")
    return body


def bootstrap(admin, buckets):
    for bucket in buckets:
        response = admin.request("HEAD", bucket)
        if response[0] == 404:
            require(admin.request("PUT", bucket), [200], f"create bucket {bucket}")
        else:
            require(response, [200], f"existing bucket {bucket}")


def security_checks(app, admin, buckets, prefix):
    checks = []
    findings = []
    private_bucket = buckets[1]
    key = prefix + "/private.txt"
    payload = b"  private test data\r\n\x00\xff  "
    denied_bucket = "ymcloud-denied-" + uuid.uuid4().hex[:12]
    require(admin.request("PUT", denied_bucket), [200], "create unrelated security-test bucket")
    try:
        require(app.request("PUT", private_bucket, key, payload), [200], "application private upload")
        downloaded = require(app.request("GET", private_bucket, key), [200], "application private download")
        if downloaded != payload:
            raise AssertionError("private data bytes changed")
        checks.append("authenticated private round-trip preserves bytes")
        for method, bucket, object_key, kwargs, description in [
            ("GET", private_bucket, key, {"authenticated": False}, "anonymous private read denied"),
            ("GET", private_bucket, "", {"authenticated": False}, "anonymous private list denied"),
            ("PUT", private_bucket, key, {"authenticated": False}, "anonymous private write denied"),
            ("GET", buckets[0], "", {"authenticated": False}, "anonymous public-bucket list denied"),
            ("GET", private_bucket, key, {"secret": "wrong-secret"}, "incorrect signing secret denied"),
            ("GET", private_bucket, key, {"access_key": "unknown-access-key"}, "unknown access key denied"),
            ("GET", denied_bucket, "", {}, "application unrelated-bucket list denied"),
            ("PUT", denied_bucket, "unexpected.txt", {}, "application unrelated-bucket write denied"),
            ("PUT", "ymcloud-app-create-" + uuid.uuid4().hex[:12], "", {}, "application bucket creation denied"),
            ("DELETE", private_bucket, "", {}, "application bucket deletion denied"),
            ("GET", private_bucket, key, {"time_offset": -3600}, "expired request timestamp denied"),
        ]:
            require(app.request(method, bucket, object_key, **kwargs), [403], description)
            checks.append(description)
        response = app.request("PUT", private_bucket, prefix + "/tampered.txt",
                               payload=b"tampered body", signed_payload=b"signed body")
        require(response, [400, 403], "tampered body with signed Content-MD5 denied")
        checks.append("tampered body with signed Content-MD5 denied")
        raw_response = app.request("PUT", private_bucket, prefix + "/raw-sha256.txt",
                                   payload=b"tampered body", signed_payload=b"signed body", content_md5=False)
        if raw_response[0] == 200:
            findings.append("SeaweedFS 4.48 accepts a changed PUT body when only x-amz-content-sha256 is signed. "
                            "Use signed Content-MD5 plus application SHA-256 read-back verification and TLS.")
        else:
            require(raw_response, [400, 403], "standalone SHA-256 signed-body probe")
            checks.append("standalone signed SHA-256 body tampering denied")
        if require(app.request("GET", private_bucket, key), [200], "recheck unchanged private object") != payload:
            raise AssertionError("denied write changed the object")
        checks.append("rejected writes preserve original object")
        require(app.request("DELETE", private_bucket, key), [204], "application private delete")
        require(app.request("GET", private_bucket, key), [404], "deleted private object absent")
        checks.append("authorized object delete")
    finally:
        for object_key in [key, prefix + "/tampered.txt", prefix + "/raw-sha256.txt"]:
            require(admin.request("DELETE", private_bucket, object_key), [204], "security cleanup")
        require(admin.request("DELETE", denied_bucket, "unexpected.txt"), [204], "security cleanup unrelated object")
        require(admin.request("DELETE", denied_bucket), [204], "security cleanup unrelated bucket")
    return {"passed": checks, "findings": findings}


def percentile(values, ratio):
    return sorted(values)[max(0, math.ceil(len(values) * ratio) - 1)]


def benchmark(app, bucket, prefix, size, iterations, concurrency):
    payload = os.urandom(size)
    expected_hash = hashlib.sha256(payload).hexdigest()
    keys = [f"{prefix}/perf-{size}-{index}.bin" for index in range(iterations)]
    metrics = []

    def execute(operation, key):
        start = time.perf_counter()
        response = app.request(operation, bucket, key, payload=payload if operation == "PUT" else b"")
        elapsed = time.perf_counter() - start
        body = require(response, [200], f"benchmark {operation}")
        if operation == "GET" and hashlib.sha256(body).hexdigest() != expected_hash:
            raise AssertionError("benchmark download integrity mismatch")
        return elapsed * 1000

    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
            for operation in ["PUT", "GET"]:
                start = time.perf_counter()
                latencies = list(pool.map(lambda key: execute(operation, key), keys))
                elapsed = time.perf_counter() - start
                metrics.append({
                    "operation": operation,
                    "object_bytes": size,
                    "samples": iterations,
                    "concurrency": concurrency,
                    "seconds": round(elapsed, 3),
                    "operations_per_second": round(iterations / elapsed, 2),
                    "mib_per_second": round(size * iterations / elapsed / 1048576, 2),
                    "mean_ms": round(statistics.mean(latencies), 2),
                    "p50_ms": round(percentile(latencies, 0.5), 2),
                    "p95_ms": round(percentile(latencies, 0.95), 2),
                })
    finally:
        for key in keys:
            require(app.request("DELETE", bucket, key), [204], "benchmark cleanup")
    return metrics


def persistence(app, bucket, mode):
    marker_path = REPORT_ROOT / "persistence.json"
    if mode == "write":
        payload = os.urandom(4096)
        key = "validation/persistence/" + uuid.uuid4().hex + ".bin"
        require(app.request("PUT", bucket, key, payload), [200], "persistence probe upload")
        marker_path.parent.mkdir(parents=True, exist_ok=True)
        marker_path.write_text(json.dumps({"bucket": bucket, "key": key,
                                          "sha256": hashlib.sha256(payload).hexdigest()}), encoding="utf-8")
        return "probe written; restart all storage services, then use --persistence-check"
    marker = json.loads(marker_path.read_text(encoding="utf-8"))
    if marker["bucket"] != bucket or not marker["key"].startswith("validation/persistence/"):
        raise AssertionError("invalid persistence marker")
    payload = require(app.request("GET", bucket, marker["key"]), [200], "persistence probe after restart")
    if hashlib.sha256(payload).hexdigest() != marker["sha256"]:
        raise AssertionError("persistence probe hash changed")
    require(app.request("DELETE", bucket, marker["key"]), [204], "persistence probe cleanup")
    marker_path.unlink()
    return "probe survives full storage restart and hash matches"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bootstrap", action="store_true")
    parser.add_argument("--security", action="store_true")
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--sizes", nargs="+", type=int, default=[4096, 65536, 1048576, 16777216])
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--persistence-write", action="store_true")
    group.add_argument("--persistence-check", action="store_true")
    args = parser.parse_args()
    if not 0 <= args.iterations <= 1000 or not 1 <= args.concurrency <= 64:
        parser.error("iterations must be 0..1000 and concurrency 1..64")
    if any(size <= 0 or size > 67108864 for size in args.sizes):
        parser.error("sizes must be 1..67108864 bytes")
    values = read_environment()
    endpoint = values["YMCLOUD_S3_ENDPOINT"]
    region = values["YMCLOUD_S3_REGION"]
    app = S3Client(endpoint, values["YMCLOUD_S3_ACCESS_KEY"], values["YMCLOUD_S3_SECRET_KEY"], region)
    admin = S3Client(endpoint, values["YMCLOUD_S3_ADMIN_ACCESS_KEY"], values["YMCLOUD_S3_ADMIN_SECRET_KEY"], region)
    buckets = [values["YMCLOUD_S3_PUBLIC_BUCKET"], values["YMCLOUD_S3_PRIVATE_BUCKET"]]
    result = {"endpoint": endpoint, "timestamp_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "security": {"passed": [], "findings": []}, "performance": [], "limitations": [
                  "Local Docker Desktop measurement includes client, virtualization and loopback overhead.",
                  "The harness signs each request and opens an independent connection; SDK pooling differs.",
                  "This measures the S3 service, not complete Web/DB/version publication throughput.",
                  "Single master, volume and filer are development topology without high availability.",
              ]}
    prefix = "validation/" + uuid.uuid4().hex
    if args.bootstrap:
        bootstrap(admin, buckets)
        result["bootstrap"] = "existing buckets retained; missing buckets created"
    if args.security:
        result["security"] = security_checks(app, admin, buckets, prefix)
    if args.persistence_write or args.persistence_check:
        result["persistence"] = persistence(app, buckets[1], "write" if args.persistence_write else "check")
    if args.iterations:
        warmup_key = prefix + "/warmup.bin"
        try:
            for _ in range(3):
                require(app.request("PUT", buckets[1], warmup_key, b"warmup"), [200], "warmup upload")
                require(app.request("GET", buckets[1], warmup_key), [200], "warmup download")
        finally:
            require(app.request("DELETE", buckets[1], warmup_key), [204], "warmup cleanup")
        for size in args.sizes:
            result["performance"].extend(benchmark(app, buckets[1], prefix, size, args.iterations, args.concurrency))
    report_dir = REPORT_ROOT
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / ("storage-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")
    output = json.dumps(result, ensure_ascii=False, indent=2)
    report_path.write_text(output + "\n", encoding="utf-8")
    print(output)
    print(f"Report: {report_path}")


if __name__ == "__main__":
    main()

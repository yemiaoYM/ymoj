#!/usr/bin/env python3
"""Read-only Web/DB/S3 smoke checks using historical migration metadata.

No login, cookies, writes, redirects or environment proxy settings are used.
Response bodies, private file contents and credentials are never reported.
"""

import argparse
import concurrent.futures
import datetime
import hashlib
import http.client
import json
import math
import pathlib
import re
import ssl
import statistics
import time
import urllib.parse


PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[5]


def read_json(path):
    with pathlib.Path(path).open(encoding="utf-8") as source:
        return json.load(source)


def column(row, name):
    normalized = name.replace("_", "").lower()
    for key, value in row.items():
        if key.replace("_", "").lower() == normalized:
            return value
    return None


class ApplicationClient:
    def __init__(self, base_url):
        self.url = urllib.parse.urlsplit(base_url)
        if (self.url.scheme not in ["http", "https"] or not self.url.hostname
                or self.url.username or self.url.password or self.url.query or self.url.fragment
                or self.url.path not in ["", "/"]):
            raise ValueError("base-url must be an HTTP(S) origin without credentials or a path prefix")
        self.ssl_context = ssl.create_default_context() if self.url.scheme == "https" else None
        # Force port validation before any output is emitted. Do not retain an arbitrary input URL in reports.
        self.port = self.url.port
        self.origin = urllib.parse.urlunsplit((self.url.scheme, self.url.netloc, "", "", ""))

    def get(self, path):
        # http.client never consults HTTP_PROXY/HTTPS_PROXY and sends encoded attack paths unchanged.
        if not path.startswith("/") or "\r" in path or "\n" in path:
            raise ValueError("invalid request path")
        encoded_path = urllib.parse.quote(path, safe="/%?=&;:!$'()*+,-._~")
        if self.url.scheme == "https":
            connection = http.client.HTTPSConnection(self.url.hostname, self.port, timeout=60,
                                                      context=self.ssl_context)
        else:
            connection = http.client.HTTPConnection(self.url.hostname, self.port, timeout=60)
        start = time.perf_counter()
        result = {"path": path}
        try:
            connection.request("GET", encoded_path, headers={"Accept-Encoding": "identity"})
            response = connection.getresponse()
            headers = {name.lower(): value for name, value in response.getheaders()}
            digest = hashlib.sha256()
            body_bytes = 0
            preview = bytearray()
            while True:
                block = response.read(64 * 1024)
                if not block:
                    break
                body_bytes += len(block)
                digest.update(block)
                if len(preview) < 65536:
                    preview.extend(block[:65536 - len(preview)])
            result.update({"httpStatus": response.status, "bytes": body_bytes, "sha256": digest.hexdigest(),
                           "nosniff": headers.get("x-content-type-options", "").lower() == "nosniff"})
            content_type = headers.get("content-type", "").split(";", 1)[0].strip().lower()
            if content_type == "application/json" and body_bytes <= 65536:
                try:
                    envelope = json.loads(preview.decode("utf-8"))
                    if isinstance(envelope, dict):
                        result["applicationCode"] = envelope.get("code")
                        result["hasData"] = envelope.get("data") is not None
                except (UnicodeDecodeError, json.JSONDecodeError):
                    pass
        except (OSError, http.client.HTTPException) as error:
            # Report exception type only; exception text can contain arbitrary endpoint details.
            result["transportError"] = type(error).__name__
        finally:
            connection.close()
            result["elapsedMs"] = round((time.perf_counter() - start) * 1000, 3)
        return result


def legacy_exception_ids(rows, migration_report, allow_exceptions):
    failures = [item for key in ["files", "verifiedFiles"]
                for item in migration_report.get(key, []) if item.get("ok") is False]
    if any(item.get("errors") for item in migration_report.get("testData", [])):
        raise ValueError("test-data migration errors cannot be excluded")
    errors = migration_report.get("errors")
    if not isinstance(errors, int) or errors != len(failures):
        raise ValueError("migration error count is missing or inconsistent")
    if not failures:
        return set()
    if not allow_exceptions:
        raise ValueError("legacy exceptions require an explicit operator flag")
    by_id = {str(column(row, "id")): row for row in rows}
    failed_ids = {str(item["id"]) for item in failures}
    imported = {str(item["id"]): item for item in migration_report.get("files", [])}
    for identifier in failed_ids:
        row = by_id.get(identifier)
        if (not row or column(row, "storage_profile") not in [None, ""]
                or str(column(row, "storage_type")) != "0"
                or str(column(row, "biz_type")) != "4"
                or str(column(row, "is_public")).lower() not in ["1", "true"]):
            raise ValueError("only retained original local public image records may be excluded")
        attempted = imported.get(identifier)
        if attempted and (attempted.get("ok") is not False or attempted.get("storageProfile") not in [None, ""]):
            raise ValueError("an imported object's verification failure cannot be excluded as legacy")
        if migration_report.get("mode") == "apply" and attempted is None:
            raise ValueError("an apply exception requires a failed original local-file import")
    return failed_ids


def expected_public_files(before_directory, migration_report, allow_exceptions=False):
    rows = read_json(pathlib.Path(before_directory) / "files-before.json")
    exception_ids = legacy_exception_ids(rows, migration_report, allow_exceptions)
    local_files = {}
    if exception_ids:
        local_files = {item["path"]: item for item in
                       read_json(pathlib.Path(before_directory) / "local-files-before.json")}
    verified = {str(item["id"]): item for item in migration_report.get("verifiedFiles", [])}
    public_files = []
    exceptions = []
    for row in rows:
        if str(column(row, "is_public")).lower() not in ["1", "true"]:
            continue
        url = column(row, "url")
        if not url:
            continue
        parsed = urllib.parse.urlsplit(str(url))
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("historical public URL contains unsupported credentials, query or fragment")
        path = parsed.path
        if not path.startswith("/files/public/"):
            raise ValueError("historical public URL is outside the supported compatibility route")
        identifier = str(column(row, "id"))
        if identifier in exception_ids:
            local = local_files.get("public/" + path[len("/files/public/"):])
            exception = {"id": identifier, "path": path, "migrationExcluded": True}
            if local is None:
                exception.update({"status": "excluded-missing-source", "httpVerified": False,
                                  "reason": "Original local public file is absent; bytes were not reconstructed."})
            else:
                if (not re.fullmatch(r"[a-f0-9]{64}", local.get("sha256", ""))
                        or int(local["bytes"]) != int(column(row, "file_size"))):
                    raise ValueError("retained local public file manifest is invalid or has a size mismatch")
                exception.update({"status": "retained-local-compatibility", "httpVerified": False,
                                  "reason": "Migration was rejected; verify only the original public URL's local bytes."})
                public_files.append({"id": identifier, "path": path, "sha256": local["sha256"],
                                     "bytes": local["bytes"], "source": "original-local-manifest"})
            exceptions.append(exception)
            continue
        expected = verified.get(identifier)
        if not expected or expected.get("ok") is not True or not re.fullmatch(r"[a-f0-9]{64}", expected.get("sha256", "")):
            raise ValueError("a historical public file lacks successful migration SHA-256 verification")
        size = column(row, "file_size")
        public_files.append({"id": identifier, "path": path, "sha256": expected["sha256"], "bytes": size,
                             "source": "verified-migrated-file"})
    return public_files, exceptions


def expected_probe_file(path):
    probe = read_json(path)
    if (probe.get("cleaned") is not False or probe.get("storageProfile") != "seaweedfs"
            or not re.fullmatch(r"storage-smoke-[a-f0-9-]{36}\.bin", probe.get("fileName", ""))
            or not re.fullmatch(r"[a-f0-9]{64}", probe.get("sha256", ""))
            or probe.get("bytes") != 64 * 1024
            or not str(probe.get("id", "")).isdigit()):
        raise ValueError("an active tracked 64KiB SeaweedFS smoke artifact is required")
    url = urllib.parse.urlsplit(probe.get("path", ""))
    if (url.scheme or url.netloc or url.query or url.fragment
            or not url.path.startswith("/files/public/") or "\\" in url.path or "%" in url.path):
        raise ValueError("probe URL must be a relative public file route")
    return {"id": str(probe["id"]), "path": url.path, "sha256": probe["sha256"],
            "bytes": probe["bytes"], "source": "controlled-seaweedfs-db-artifact"}


def private_test_metadata(migration_report):
    private_hashes = set()
    problem_id = None
    for result in migration_report.get("testData", []):
        if result.get("errors") or (result.get("fileCount", 0) > 0 and result.get("versionId") is None):
            raise ValueError("test-data migration must be complete and error-free")
        if result.get("versionId") is None or result.get("errors"):
            continue
        identifier = str(result.get("problemId", ""))
        if not re.fullmatch(r"[0-9]{1,20}", identifier):
            raise ValueError("invalid migrated problem identifier")
        if problem_id is None:
            problem_id = identifier
        manifest = result.get("manifest") or {}
        for entry in manifest.get("entries", []):
            for name in ["inputSha256", "outputSha256"]:
                if re.fullmatch(r"[a-f0-9]{64}", entry.get(name, "")):
                    private_hashes.add(entry[name])
    if problem_id is None:
        raise ValueError("no successful migrated problem is available for private-data smoke checks")
    return problem_id, private_hashes


def verify_public(response, expected):
    if response.get("transportError") or response.get("httpStatus") != 200:
        return "public URL did not return HTTP 200"
    if response.get("sha256") != expected["sha256"]:
        return "public URL bytes differ from the verified migration hash"
    if expected["bytes"] is not None and response.get("bytes") != int(expected["bytes"]):
        return "public URL size differs from the original file record"
    if not response.get("nosniff"):
        return "public URL is missing X-Content-Type-Options: nosniff"
    return None


def verify_denial(response, allowed_codes, private_hashes):
    if response.get("transportError"):
        return "transport failure does not prove authorization or traversal rejection"
    status = response.get("httpStatus", 0)
    code = response.get("applicationCode")
    if status >= 500 or (isinstance(code, int) and code >= 500):
        return "server/application 5xx is not an acceptable rejection"
    if response.get("bytes", 0) > 0 and response.get("sha256") in private_hashes:
        return "response returned known private test-data bytes"
    if response.get("hasData"):
        return "rejected response contains a non-null data payload"
    if status in allowed_codes:
        return None
    if status == 200 and code in allowed_codes:
        return None
    return "request was not rejected with an expected HTTP or application error code"


def percentile(values, ratio):
    return sorted(values)[max(0, math.ceil(len(values) * ratio) - 1)]


def run_checks(client, public_files, problem_id, private_hashes, samples, concurrency, probe_file=None):
    result = {"historicalPublicFiles": [], "anonymousSecurity": [], "traversalSecurity": [], "errors": 0}
    for expected in public_files:
        response = client.get(expected["path"])
        response["fileId"] = expected["id"]
        response["source"] = expected["source"]
        response["error"] = verify_public(response, expected)
        result["errors"] += bool(response["error"])
        result["historicalPublicFiles"].append(response)
    for path in [
        f"/files/private/problem/{problem_id}/in/1.in",
        "/admin/storage/migration/files",
        f"/admin/problem/{problem_id}/testcases",
        f"/admin/problem/{problem_id}/testcases/migration",
    ]:
        response = client.get(path)
        response["error"] = verify_denial(response, {401, 403}, private_hashes)
        result["errors"] += bool(response["error"])
        result["anonymousSecurity"].append(response)
    for path in [
        f"/files/public/%2e%2e/private/problem/{problem_id}/in/1.in",
        f"/files/public/%2e%2e%2fprivate%2fproblem%2f{problem_id}%2fin%2f1.in",
        f"/files/public/%252e%252e%252fprivate%252fproblem%252f{problem_id}%252fin%252f1.in",
        f"/files/public/%2e%2e%5cprivate%5cproblem%5c{problem_id}%5cin%5c1.in",
        f"/files/public/..;/private/problem/{problem_id}/in/1.in",
    ]:
        response = client.get(path)
        response["error"] = verify_denial(response, {400, 401, 403, 404}, private_hashes)
        result["errors"] += bool(response["error"])
        result["traversalSecurity"].append(response)
    performance_files = [probe_file] if probe_file else public_files
    if not performance_files:
        raise ValueError("no verified public file is available for HTTP performance checks")
    jobs = [performance_files[index % len(performance_files)] for index in range(samples)]

    def download(expected):
        response = client.get(expected["path"])
        response["error"] = verify_public(response, expected)
        return response

    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        responses = list(pool.map(download, jobs))
    elapsed = time.perf_counter() - start
    latencies = [response["elapsedMs"] for response in responses]
    failures = [response for response in responses if response["error"]]
    result["errors"] += len(failures)
    result["performance"] = {
        "samples": samples, "concurrency": concurrency, "errors": len(failures),
        "p50Ms": round(percentile(latencies, .5), 3), "p95Ms": round(percentile(latencies, .95), 3),
        "meanMs": round(statistics.mean(latencies), 3), "seconds": round(elapsed, 3),
        "downloadsPerSecond": round(samples / elapsed, 3),
        "verifiedMiBPerSecond": round(sum(response.get("bytes", 0) for response in responses if not response["error"])
                                      / elapsed / 1048576, 3),
        "failures": failures,
        "source": "controlled-seaweedfs-db-artifact" if probe_file else "historical-public-files",
        "operations": "HTTP public URL + actual authorization/DB lookup + storage read + SHA-256",
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:9091")
    parser.add_argument("--before", required=True, type=pathlib.Path, help="pre-migration metadata backup directory")
    parser.add_argument("--report", required=True, type=pathlib.Path, help="original apply/verify report.json")
    parser.add_argument("--probe-file", type=pathlib.Path, help="active tracked smoke-create JSON artifact")
    parser.add_argument("--allow-legacy-exceptions", action="store_true",
                        help="retain failed original local image records and report every exclusion explicitly")
    parser.add_argument("--output", type=pathlib.Path, default=PROJECT_ROOT / "target/test-output/application-storage.json")
    parser.add_argument("--samples", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=4)
    args = parser.parse_args()
    if not 1 <= args.samples <= 1000 or not 1 <= args.concurrency <= 32:
        parser.error("samples must be 1..1000 and concurrency 1..32")
    output = args.output.resolve()
    # Reports containing business metadata stay in an ignored project test-output directory.
    if (PROJECT_ROOT not in output.parents or "test-output" not in output.parts
            or output == args.report.resolve()):
        parser.error("output must be a separate file inside the project's ignored test-output directory")
    result = {"timestampUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "limitations": ["Local HTTP measurement includes Web, real DB lookup and storage reads.",
                              "Only anonymous permissions are probed; role-specific checks run in backend tests."]}
    try:
        client = ApplicationClient(args.base_url)
        result["baseUrl"] = client.origin
        migration_report = read_json(args.report)
        if migration_report.get("mode") not in ["apply", "verify"]:
            raise ValueError("an apply/verify migration report is required")
        result["migrationErrors"] = migration_report.get("errors")
        public_files, exceptions = expected_public_files(args.before, migration_report, args.allow_legacy_exceptions)
        result["legacyExceptions"] = exceptions
        result["allHistoricalFilesMigrated"] = not exceptions
        result["migrationStatus"] = "partial-legacy-retained" if exceptions else "complete"
        probe_file = expected_probe_file(args.probe_file) if args.probe_file else None
        if probe_file:
            result["probeArtifact"] = probe_file
        if exceptions and probe_file is None:
            raise ValueError("a controlled SeaweedFS artifact is required when historical files are excluded")
        problem_id, private_hashes = private_test_metadata(migration_report)
        result.update(run_checks(client, public_files, problem_id, private_hashes, args.samples, args.concurrency,
                                 probe_file))
        by_id = {item["fileId"]: item for item in result["historicalPublicFiles"]}
        for exception in exceptions:
            response = by_id.get(exception["id"])
            exception["httpVerified"] = response is not None and response["error"] is None
    except (OSError, ValueError, KeyError, TypeError) as error:
        # Keep arbitrary JSON, database metadata or endpoint strings out of error output.
        result["errors"] = 1
        result["configurationError"] = type(error).__name__
    result["verificationStatus"] = ("failed" if result["errors"] else
                                    "checks-passed-with-legacy-exclusions" if result["legacyExceptions"] else
                                    "checks-passed")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"Report: {output}")
    return 1 if result["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())

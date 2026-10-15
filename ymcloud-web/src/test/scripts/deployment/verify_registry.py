#!/usr/bin/env python3
"""Verify two public release repositories without using Docker login credentials.

Inspect every versioned service tag before optional anonymous pulls of those
same tags. Pulls refresh existing local tags, then validate config ID, platform
and project version so a tag moving during verification fails. Per-repository
manifest digests remain diagnostic data. Reports and temporary configuration stay
under target/phase3-verification. No image, container or user login is deleted.
"""

import argparse
import contextlib
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[5]
DOCKER_DIRECTORY = ROOT / "docker"
if str(DOCKER_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(DOCKER_DIRECTORY))

from project_version import project_version


ARTIFACTS = ROOT / "target" / "phase3-verification"
SERVICES = ("web", "worker", "go-judge", "frontend", "mysql", "redis", "rabbitmq")
PROJECT_SERVICES = frozenset(SERVICES[:4])
UPSTREAM_VERSIONS = {"mysql": "8.4", "redis": "7.4-alpine", "rabbitmq": "4.1-alpine"}
DIGEST = re.compile(r"sha256:[a-f0-9]{64}")
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")
TAG = re.compile(r"[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}")
REPOSITORY_PATH = re.compile(r"[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*")
REGISTRY_HOST = re.compile(r"[a-z0-9]+(?:[.-][a-z0-9]+)*(?::[0-9]{1,5})?")
MANIFEST_TYPES = {
    "SchemaV2Manifest": "application/vnd.docker.distribution.manifest.v2+json",
    "OCIManifest": "application/vnd.oci.image.manifest.v1+json",
}
DOCKER_ENV = {
    "DOCKER_AUTH_CONFIG", "DOCKER_CONFIG", "DOCKER_CONTEXT", "DOCKER_HOST",
    "DOCKER_CUSTOM_HEADERS", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH",
}
MAX_JSON_BYTES = 16 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def parse_json(raw, label):
    require(isinstance(raw, str) and len(raw.encode("utf-8")) <= MAX_JSON_BYTES,
            label + ": JSON response is too large")
    try:
        return json.loads(raw)
    except (ValueError, RecursionError):
        raise ValueError(label + ": invalid JSON response") from None


def confined_report(path):
    """Reject lexical escapes, symlinks and Windows junctions before any writes."""
    absolute = pathlib.Path(os.path.abspath(path))
    boundary = pathlib.Path(os.path.abspath(ARTIFACTS))
    require(absolute.is_relative_to(boundary) and absolute != boundary,
            "Report must stay under target/phase3-verification")
    require(boundary.is_relative_to(ROOT), "Artifact directory is outside the repository")
    current = ROOT
    for part in absolute.relative_to(ROOT).parts:
        current = current / part
        require(not current.is_symlink() and current.resolve() == current,
                "Report paths must not contain symlinks or directory junctions")
    require(absolute.suffix == ".json" and not absolute.is_dir(), "Report must be a JSON file")
    return absolute


def repository_name(value):
    """Require a full registry/repository, never a URL, tag, digest or credential."""
    require(isinstance(value, str) and len(value) <= 255, "Invalid release repository")
    parts = value.split("/")
    require(len(parts) >= 2 and REGISTRY_HOST.fullmatch(parts[0])
            and all(REPOSITORY_PATH.fullmatch(part) for part in parts[1:]),
            "Repository must contain a registry host and an untagged repository path")
    host = parts[0]
    require("." in host or ":" in host or host == "localhost", "Repository registry host must be explicit")
    if ":" in host:
        require(1 <= int(host.rsplit(":", 1)[1]) <= 65535, "Invalid registry port")
    if host in ("index.docker.io", "registry-1.docker.io"):
        parts[0] = "docker.io"
    return "/".join(parts)


def repositories(values):
    require(len(values) == 2, "Exactly two --repository arguments are required")
    result = [repository_name(value) for value in values]
    require(len(set(result)) == 2, "Release repositories must be different")
    return result


def load_lock(path):
    """Use verified local config IDs, preserving middleware's upstream versions."""
    try:
        lock = parse_json(pathlib.Path(path).read_text(encoding="utf-8"), "Local lock")
    except (OSError, UnicodeError):
        raise ValueError("Cannot read the UTF-8 local image lock") from None
    require(isinstance(lock, dict), "Local image lock must be an object")
    version = lock.get("version")
    require(isinstance(version, str) and len(version) <= 100 and VERSION.fullmatch(version),
            "Local image lock has an invalid project version")
    require(version == project_version(ROOT), "Local image lock version differs from the parent POM")
    require(lock.get("platform") == "linux/amd64", "Local image lock must target linux/amd64")
    images = lock.get("images")
    require(isinstance(images, dict) and set(images) == set(SERVICES),
            "Local image lock must contain all seven release services")
    expected = {}
    for service in SERVICES:
        record = images[service]
        require(isinstance(record, dict) and isinstance(record.get("imageId"), str)
                and DIGEST.fullmatch(record["imageId"]), "Invalid local image ID: " + service)
        if service in PROJECT_SERVICES:
            require(record.get("version") == version
                    and record.get("versionTag") == f"ymcloud-{service}:{version}",
                    "Project image version differs from its release: " + service)
            image_version = version
        else:
            source = record.get("source")
            require(isinstance(source, str) and source.count("@") == 1,
                    "Middleware source must include its pinned digest: " + service)
            tagged, digest = source.rsplit("@", 1)
            leaf = tagged.rsplit("/", 1)[-1]
            require(DIGEST.fullmatch(digest) and leaf.startswith(service + ":"),
                    "Middleware source must retain its upstream name and version: " + service)
            image_version = leaf[len(service) + 1:]
            require(image_version == UPSTREAM_VERSIONS[service],
                    "Middleware source must use its selected upstream version: " + service)
        tag = f"{service}-{image_version}"
        require(TAG.fullmatch(tag), "Release tag is too long: " + service)
        expected[service] = {"tag": tag, "imageId": record["imageId"]}
    return version, expected


def command(arguments, environment, label, timeout=120):
    options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    try:
        result = subprocess.run(arguments, env=environment, capture_output=True, text=True,
                                encoding="utf-8", errors="strict", timeout=timeout, **options)
    except (OSError, UnicodeError, subprocess.TimeoutExpired):
        raise ValueError(label + ": Docker command could not complete") from None
    # Registry/proxy failures may contain credentials. Never copy raw output to reports.
    require(result.returncode == 0, label + ": Docker command failed")
    return result.stdout


def current_engine_endpoint(environment):
    """Resolve the original context before replacing Docker's configuration directory."""
    if environment.get("DOCKER_HOST") and not environment.get("DOCKER_CONTEXT"):
        endpoint = environment["DOCKER_HOST"]
    else:
        raw = command(["docker", "context", "inspect", "--format", "{{json .Endpoints.docker}}"],
                      environment, "Resolve current Docker context")
        context = parse_json(raw, "Docker context")
        require(isinstance(context, dict), "Current Docker context has no engine endpoint")
        endpoint = context.get("Host")
    # Remote contexts can require private TLS/SSH material from the original config.
    # Fail explicitly instead of silently dropping that material or selecting another engine.
    require(isinstance(endpoint, str) and endpoint.startswith(("npipe:///", "unix:///"))
            and not any(character.isspace() for character in endpoint),
            "Anonymous pulls require a local Unix socket or Windows named-pipe Docker context")
    return endpoint


@contextlib.contextmanager
def anonymous_client(repository_values, inherited_environment):
    confined_report(ARTIFACTS / "registry-config-boundary.json")
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="registry-anonymous-", dir=ARTIFACTS) as directory:
        configuration = pathlib.Path(directory)
        # A literally empty auth map lets Docker discover the system credential helper.
        # Empty entries contain no credentials and disable this implicit fallback.
        auths = {"https://index.docker.io/v1/": {}}
        for repository in repository_values:
            host = repository.split("/", 1)[0]
            auths[host] = {}
        (configuration / "config.json").write_text(json.dumps({"auths": auths}), encoding="utf-8")
        environment = {key: value for key, value in inherited_environment.items()
                       if key.upper() not in DOCKER_ENV}
        environment["DOCKER_CONFIG"] = str(configuration)
        yield ["docker", "--config", str(configuration)], environment


def manifest_record(raw, image_id):
    """Select the actual linux/amd64 manifest descriptor, including OCI index output."""
    value = parse_json(raw, "Remote manifest")
    candidates = value if isinstance(value, list) else [value]
    require(all(isinstance(item, dict) for item in candidates), "Unexpected verbose manifest response")
    selected = []
    for item in candidates:
        descriptor = item.get("Descriptor")
        require(isinstance(descriptor, dict), "Verbose manifest descriptor is missing")
        platform = descriptor.get("platform")
        if isinstance(platform, dict) and platform.get("os") == "linux" and platform.get("architecture") == "amd64":
            require(platform.get("variant", "") in ("", "v1"), "Unsupported amd64 platform variant")
            selected.append(item)
    require(len(selected) == 1, "Expected exactly one linux/amd64 image manifest")
    item = selected[0]
    descriptor = item["Descriptor"]
    digest = descriptor.get("digest")
    require(isinstance(digest, str) and DIGEST.fullmatch(digest) and digest != image_id,
            "Missing actual manifest digest; config IDs cannot serve as manifest digests")
    payloads = [key for key in MANIFEST_TYPES if item.get(key) is not None]
    require(len(payloads) == 1, "Expected one schema2 or OCI image manifest")
    payload = item[payloads[0]]
    media_type = MANIFEST_TYPES[payloads[0]]
    require(isinstance(payload, dict) and payload.get("schemaVersion") == 2
            and payload.get("mediaType") == media_type and descriptor.get("mediaType") == media_type,
            "Unexpected image manifest schema or media type")
    config = payload.get("config")
    require(isinstance(config, dict) and config.get("digest") == image_id,
            "Remote manifest config digest differs from the locked local image ID")
    return digest


def verify_local_image(raw, image_id, version=None):
    images = parse_json(raw, "Pulled image")
    require(isinstance(images, list) and len(images) == 1 and isinstance(images[0], dict),
            "Expected one locally pulled image")
    actual = images[0]
    require(actual.get("Id") == image_id, "Pulled image differs from the locked local image ID")
    require(actual.get("Os") == "linux" and actual.get("Architecture") == "amd64"
            and actual.get("Variant", "") in ("", "v1"), "Pulled image must target linux/amd64")
    if version is not None:
        configuration = actual.get("Config") or {}
        labels = configuration.get("Labels") or {}
        require(labels.get("org.opencontainers.image.version") == version,
                "Pulled project image has a different OCI version label")


def verify(repository_values, local_lock, pull=False, environment=None):
    repository_values = repositories(repository_values)
    version, expected = load_lock(local_lock)
    inherited = dict(os.environ if environment is None else environment)
    endpoint = current_engine_endpoint(inherited) if pull else None
    report = {"ok": False, "version": version, "platform": "linux/amd64",
              "repositorySwitchSafe": False, "anonymousManifestVerified": False,
              "anonymousPullVerified": False, "images": {}}
    with anonymous_client(repository_values, inherited) as (docker, anonymous_environment):
        for service, record in expected.items():
            checks = {}
            for repository in repository_values:
                reference = repository + ":" + record["tag"]
                raw = command(docker + ["manifest", "inspect", "--verbose", reference],
                              anonymous_environment, "Anonymous manifest inspection: " + service)
                digest = manifest_record(raw, record["imageId"])
                checks[repository] = {"tagReference": reference, "reference": repository + "@" + digest,
                                      "manifestDigest": digest, "configId": record["imageId"],
                                      "anonymousManifest": True,
                                      "anonymousPull": None}
            # Registries may re-encode/compress manifests differently. Their tags
            # are interchangeable only after both config IDs match this build.
            report["images"][service] = {**record, "repositories": checks}
        # No pull begins until every tag in both registries has passed the manifest checks.
        if pull:
            engine = docker + ["--host", endpoint]
            for service, record in report["images"].items():
                for check in record["repositories"].values():
                    reference = check["tagReference"]
                    command(engine + ["pull", "--platform", "linux/amd64", reference],
                            anonymous_environment, "Anonymous version tag pull: " + service, timeout=900)
                    raw = command(engine + ["image", "inspect", reference], anonymous_environment,
                                  "Inspect anonymously pulled image: " + service)
                    verify_local_image(raw, record["imageId"], version if service in PROJECT_SERVICES else None)
                    check["anonymousPull"] = True
    report.update(ok=True, repositorySwitchSafe=True, anonymousManifestVerified=True,
                  anonymousPullVerified=pull,
                  verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    return report


def write_report(path, report):
    """Atomically replace our JSON report; never leave a previous success after failure."""
    destination = confined_report(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=destination.parent,
                                     prefix=".registry-report-", suffix=".tmp", delete=False) as output:
        temporary = pathlib.Path(output.name)
        try:
            json.dump(report, output, ensure_ascii=False, indent=2)
            output.write("\n")
        except BaseException:
            output.close()
            temporary.unlink(missing_ok=True)
            raise
    try:
        confined_report(destination)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", action="append", required=True,
                        help="Full public registry/repository; specify exactly twice")
    parser.add_argument("--local-lock", type=pathlib.Path, required=True,
                        help="Latest local deployment images.lock.json")
    parser.add_argument("--report", type=pathlib.Path, required=True,
                        help="JSON report inside target/phase3-verification")
    parser.add_argument("--pull", action="store_true",
                        help="Refresh public version tags anonymously and verify their actual image content")
    args = parser.parse_args(argv)
    destination = confined_report(args.report)
    require(destination != args.local_lock.resolve(), "Report must not replace the local image lock")
    # Invalidate an earlier report before remote work, including interrupted verification.
    write_report(destination, {"ok": False, "status": "verification-in-progress"})
    try:
        report = verify(args.repository, args.local_lock, args.pull)
    except ValueError as error:
        write_report(destination, {"ok": False, "status": "verification-failed", "error": str(error)})
        raise
    write_report(destination, report)
    print(json.dumps({"ok": True, "services": len(SERVICES), "repositories": 2,
                      "anonymousPullVerified": args.pull, "report": str(destination)}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError):
        print("Registry verification failed; inspect the report for a sanitized error when available.",
              file=sys.stderr)
        sys.exit(1)

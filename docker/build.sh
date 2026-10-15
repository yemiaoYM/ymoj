#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/target/phase3-verification"
mkdir -p "$OUT/packages"
cd "$ROOT"
YMCLOUD_VERSION="$(python3 docker/project_version.py)"
test -f ymcloud-judge/target/ymcloud-judge.jar || {
    echo '请先运行 mvn -pl ymcloud-judge -am package -DskipTests' >&2
    exit 1
}
test "$(docker info --format '{{.OSType}}')" = linux
python3 - <<'PY'
import sys
from pathlib import Path

sys.path.insert(0, 'docker')
from project_version import project_version
from release import validate_jar

validate_jar(Path('ymcloud-judge/target/ymcloud-judge.jar'), project_version())
PY

for target in runtime worker; do
    service=go-judge
    if [ "$target" = worker ]; then
        service=worker
    fi
    docker buildx build --platform linux/amd64 --load \
        --build-context "deb-cache=$OUT/packages" \
        --target "$target" --file docker/go-judge/Dockerfile \
        --build-arg "YMCLOUD_VERSION=$YMCLOUD_VERSION" \
        --tag "ymcloud-$service:$YMCLOUD_VERSION" \
        --metadata-file "$OUT/$target.metadata.json" \
        --iidfile "$OUT/$target.iid" .
    check_args=()
    if [ "$target" = worker ]; then
        check_args+=(--toolchain-only)
    fi
    docker run --rm --network none --entrypoint python3 \
        "$(cat "$OUT/$target.iid")" /opt/ymcloud/verify_versions.py "${check_args[@]}"
done

# Compose 使用不可变内容 ID；仓库地址确定后再接入镜像推送。
python3 docker/go-judge/lock_images.py "$OUT"
echo "ymcloud $YMCLOUD_VERSION 统一运行镜像与 Worker 已构建并锁定，产物：$OUT"

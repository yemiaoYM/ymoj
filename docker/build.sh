#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/target/phase3-verification"
mkdir -p "$OUT/packages"
cd "$ROOT"
test -f ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar || {
    echo '请先运行 mvn -pl ymcloud-judge -am package -DskipTests' >&2
    exit 1
}
test "$(docker info --format '{{.OSType}}')" = linux
python3 - <<'PY'
import sys
import zipfile
with zipfile.ZipFile('ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar') as archive:
    private = {'BOOT-INF/classes/application-dev.yml', 'BOOT-INF/classes/application-prod.yml'}
    if private.intersection(archive.namelist()):
        sys.exit('Worker jar 包含私有环境配置；清理模块构建产物后重新打包')
PY

for target in runtime worker; do
    docker buildx build --platform linux/amd64 --load \
        --build-context "deb-cache=$OUT/packages" \
        --target "$target" --file docker/go-judge/Dockerfile \
        --tag "ymcloud-judge-$target:v1.13.3" \
        --metadata-file "$OUT/$target.metadata.json" \
        --iidfile "$OUT/$target.iid" .
    check_args=()
    if [ "$target" = worker ]; then
        check_args+=(--toolchain-only)
    fi
    docker run --rm --network none --entrypoint python3 \
        "$(cat "$OUT/$target.iid")" /opt/ymcloud/verify_versions.py "${check_args[@]}"
done

# 本地交付不发布仓库。Compose 使用不可变内容 ID，归档保留构建导出证据。
python3 docker/go-judge/lock_images.py "$OUT"
docker image save --output "$OUT/images.tar" \
    "$(cat "$OUT/runtime.iid")" "$(cat "$OUT/worker.iid")"
(
    cd "$OUT"
    sha256sum images.tar > images.tar.sha256
)
echo "统一运行镜像与 Worker 已构建并锁定，产物：$OUT"

#!/bin/bash
set -e

# 当前脚本所在目录（也就是 docker/）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

IMAGES=("python" "cpp" "java")

echo "=============================="
echo " ? OJ Judge Environment Build "
echo "=============================="

for IMG in "${IMAGES[@]}"; do
    IMAGE_NAME="oj-${IMG}-env"
    DOCKERFILE_PATH="${SCRIPT_DIR}/${IMG}"

    echo ""
    echo "? Checking image: ${IMAGE_NAME}"

    # 判断镜像是否存在
    if docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        echo "   ? Image already exists. Skipped."
        continue
    fi

    # 判断构建目录是否存在
    if [ ! -d "$DOCKERFILE_PATH" ]; then
        echo "   ? Directory ${DOCKERFILE_PATH} not found. Skipped."
        continue
    fi

    echo "   ? Building image from ${IMG}/..."
    docker build -t "$IMAGE_NAME" "$DOCKERFILE_PATH" >/dev/null

    if [ $? -eq 0 ]; then
        echo "   ? Build complete."
    else
        echo "   ? Build failed!"
    fi
done

echo ""
echo "=============================="
echo " ? All tasks completed."
echo "=============================="

#!/bin/bash

# 判题任务ID或提交ID
SUBMISSION_ID=$1
# 编译命令
COMPILE_CMD=$2
# Docker 映像名称
IMAGE_NAME=$3
# 代码路径
BASE_DIR=$4

# 默认路径配置
HOST_CODE_DIR="${BASE_DIR}/tmp/submission_${SUBMISSION_ID}"
CONTAINER_CODE_DIR="/app"

# 输出文件
ERROR_FILE="compile_error.out"

echo "📁 HOST DIR      : $HOST_CODE_DIR"
echo "📁 CONTAINER DIR : $CONTAINER_CODE_DIR"
echo "🐳 IMAGE         : $IMAGE_NAME"
echo "🚀 SUBMISSION ID : $SUBMISSION_ID"

# Docker 执行逻辑
docker run --rm \
    --name "compile_task_${SUBMISSION_ID}" \
    --cpus="0.5" -m 256m \
    --pids-limit=128 \
    --network none \
    -v "${HOST_CODE_DIR}:${CONTAINER_CODE_DIR}" \
    -w "${CONTAINER_CODE_DIR}" \
    "${IMAGE_NAME}" \
    /bin/bash -c "
        ${COMPILE_CMD} 2>${ERROR_FILE} || exit 1
    "

if [ $? -eq 0 ]; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
    exit 1
fi


#echo "📄 Listing files in HOST_CODE_DIR:"
#ls -l "${HOST_CODE_DIR}"

# ./compile.sh 10009 "g++ main.cpp -o main" "oj-cpp-env" "/mnt/e/Java/ymcloud/files"
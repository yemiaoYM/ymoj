#!/bin/bash

# 判题任务ID或提交ID
SUBMISSION_ID=$1

# 编译命令（可选）
COMPILE_CMD=$2

# 运行命令
RUN_CMD=$3

# Docker 映像名称
IMAGE_NAME=$4

# 默认路径配置
HOST_CODE_DIR="$(pwd)/submission/${SUBMISSION_ID}"
CONTAINER_CODE_DIR="/app"

# 输出文件
TIME_FILE="time.txt"
ERROR_FILE="error.txt"

echo "📁 HOST DIR      : $HOST_CODE_DIR"
echo "📁 CONTAINER DIR : $CONTAINER_CODE_DIR"
echo "🐳 IMAGE         : $IMAGE_NAME"
echo "🚀 SUBMISSION ID : $SUBMISSION_ID"

# Docker 执行逻辑
docker run --rm \
    --name "judge_task_${SUBMISSION_ID}" \
    --cpus="0.5" -m 256m \
    --pids-limit=64 \
    --network none \
    -v "${HOST_CODE_DIR}:${CONTAINER_CODE_DIR}" \
    -w "${CONTAINER_CODE_DIR}" \
    "${IMAGE_NAME}" \
    /bin/bash -c " \
        ${COMPILE_CMD} 2>${ERROR_FILE} && \
        /usr/bin/time -f 'TIME:%es\nMEM:%Kb' -o ${TIME_FILE} \
        timeout 2s ${RUN_CMD} < input.txt > output.txt 2>>${ERROR_FILE} \
    "

# 判题结果输出
if [ $? -eq 0 ]; then
    echo "✅ 判题成功"
    echo "📄 输出文件: ${HOST_CODE_DIR}/output.txt"

    if [ -f "${HOST_CODE_DIR}/${TIME_FILE}" ]; then
        echo "⏱ 运行时间与内存:"
        cat "${HOST_CODE_DIR}/${TIME_FILE}"
    fi
else
    echo "❌ 编译或运行失败"
    echo "🚫 错误日志:"
    cat "${HOST_CODE_DIR}/${ERROR_FILE}"
fi

# ./judge.sh 10003 "" "python3 main.py" "oj-python-env"
# ./judge.sh 10002 "javac Main.java" "java Main" "oj-java-env"
# ./judge.sh 10001 "g++ main.cpp -o main" "./main" "oj-cpp-env"
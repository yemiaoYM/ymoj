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
COMPILE_ERROR_FILE="compile_error.txt"

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
        ${COMPILE_CMD} 2>${COMPILE_ERROR_FILE} || exit 1; \
        i=1; \
        while [[ -f input_\${i}.txt ]]; do \
            /usr/bin/time -f 'TIME:%es\nMEM:%Kb' -o time_\${i}.txt \
            timeout 2s ${RUN_CMD} < input_\${i}.txt > output_\${i}.txt 2>>${ERROR_FILE}; \
            ((i++)); \
        done \
    "

# 判题结果输出
if [ $? -eq 0 ]; then
    echo "✅ 判题成功"
else
    echo "❌ 编译或运行失败"
fi

# ./judge.sh 10003 "" "python3 main.py" "oj-python-env"
# ./judge.sh 10002 "javac Main.java" "java Main" "oj-java-env"
# ./judge.sh 10001 "g++ main.cpp -o main" "./main" "oj-cpp-env"
# ./judge.sh 10000 "gcc main.c -o main" "./main" "oj-cpp-env"
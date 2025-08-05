#!/bin/bash
# ================================
# judge_multi.sh
# 参数:
#   $1 SUBMISSION_ID   提交 ID
#   $2 PROBLEM_ID      题目 ID
#   $3 COMPILE_CMD     编译命令（可空）
#   $4 RUN_CMD         运行命令
#   $5 IMAGE_NAME      Docker 镜像名
#
# 目录约定:
#   HOST_SUB_DIR = pwd/files/tmp/submission_${SUBMISSION_ID}
#   HOST_PROB_DIR = pwd/files/private/problem/problem_${PROBLEM_ID}
#   CONTAINER_SUB = /app/submission
#   CONTAINER_PROB= /app/problem
# ================================

SUBMISSION_ID=$1
PROBLEM_ID=$2
COMPILE_CMD=$3
RUN_CMD=$4
IMAGE_NAME=$5

# 本地目录
# 取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$(dirname "${SCRIPT_DIR}")")"  # 脚本目录上两级就是 ymcloud 根目录

HOST_SUB_DIR="${BASE_DIR}/tmp/submission_${SUBMISSION_ID}"
HOST_PROB_DIR="${BASE_DIR}/private/problem/problem_${PROBLEM_ID}"


# 容器内目录
CONTAINER_SUB="/app/submission"
CONTAINER_PROB="/app/problem"

# 容器内文件名常量
COMPILE_ERROR_FILE="compileError.out"

echo "📁 Host submission dir: ${HOST_SUB_DIR}"
echo "📁 Host problem dir   : ${HOST_PROB_DIR}"
echo "🐳 Docker image       : ${IMAGE_NAME}"
echo "🚀 Submission ID      : ${SUBMISSION_ID}"
echo "🚀 Problem ID         : ${PROBLEM_ID}"

docker run --rm \
  --name "judge_${SUBMISSION_ID}" \
  --cpus="0.5" -m 256m \
  --pids-limit=64 \
  --network none \
  -v "${HOST_SUB_DIR}:${CONTAINER_SUB}" \
  -v "${HOST_PROB_DIR}:${CONTAINER_PROB}:ro" \
  -w "${CONTAINER_SUB}" \
  "${IMAGE_NAME}" \
  /bin/bash -c "
    if [ -n \"${COMPILE_CMD}\" ]; then
        ${COMPILE_CMD} 2>${COMPILE_ERROR_FILE} || exit 1
    fi
    i=1;
    while [[ -f ${CONTAINER_PROB}/\${i}.in ]]; do
        /usr/bin/time -f 'TIME:%e\nMEM:%M' -o time_\${i}.out \
        timeout 2s ${RUN_CMD} < ${CONTAINER_PROB}/\${i}.in > \${i}.out 2>error_\${i}.out;
        ((i++));
    done
  "

# 判题结果输出
if [ $? -eq 0 ]; then
    echo "✅ 判题成功"
else
    echo "❌ 编译或运行失败"
fi
# ./judge1.sh 10009 1 'g++ main.cpp -o main' './main' 'oj-cpp-env'

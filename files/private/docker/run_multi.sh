#!/bin/bash

# 判题任务ID或提交ID
SUBMISSION_ID=$1
# 题目ID
PROBLEM_ID=$2
# 运行指令
RUN_CMD=$3
# Docker 映像名称
IMAGE_NAME=$4
# 代码路径
BASE_DIR=$5

# 默认路径配置
HOST_SUB_DIR="${BASE_DIR}/tmp/submission_${SUBMISSION_ID}"
HOST_PROB_DIR="${BASE_DIR}/private/problem/problem_${PROBLEM_ID}"

# 容器内目录
CONTAINER_SUB="/app/submission"
CONTAINER_PROB="/app/problem"


echo "📁 Host submission dir: ${HOST_SUB_DIR}"
echo "📁 Host problem dir   : ${HOST_PROB_DIR}"
echo "🐳 Docker image       : ${IMAGE_NAME}"
echo "🚀 Submission ID      : ${SUBMISSION_ID}"
echo "🚀 Problem ID         : ${PROBLEM_ID}"

#docker运行脚本
docker run --rm \
  --name "run_task_${SUBMISSION_ID}" \
  --cpus="0.5" -m 128m \
  --pids-limit=64 \
  --network none \
  -v "${HOST_SUB_DIR}:${CONTAINER_SUB}" \
  -v "${HOST_PROB_DIR}:${CONTAINER_PROB}:ro" \
  -w "${CONTAINER_SUB}" \
  "${IMAGE_NAME}" \
  /bin/bash -c "
    i=1;
    while [[ -f ${CONTAINER_PROB}/\${i}.in ]]; do
        /usr/bin/time -f 'TIME:%e\nMEM:%M' -o time_\${i}.out \
        timeout 2s ${RUN_CMD} < ${CONTAINER_PROB}/\${i}.in > \${i}.out 2>error_\${i}.out
        ((i++));
    done
  "

if [ $? -eq 0 ]; then
    echo "✅ 运行成功"
else
    echo "❌ 运行失败"
    exit 1
fi

#echo "📄 Listing files in HOST_SUB_DIR:"
#ls -l "${HOST_SUB_DIR}"
#
#echo "📄 Listing files in HOST_PROB_DIR:"
#ls -l "${HOST_PROB_DIR}"


# ./run_multi.sh 10009 1 "./main" "oj-cpp-env" "/mnt/e/Java/ymcloud/files"
# ./run_multi.sh 10021 1 "java Main" "oj-java-env" "/mnt/e/Java/ymcloud/files"
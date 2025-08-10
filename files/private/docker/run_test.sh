#!/bin/bash

# 判题任务ID或提交ID
SUBMISSION_ID=$1
# 运行指令
RUN_CMD=$2
# Docker 映像名称
IMAGE_NAME=$3
# 代码路径
BASE_DIR=$4

# 默认路径配置
HOST_CODE_DIR="${BASE_DIR}/tmp/submission_${SUBMISSION_ID}"
CONTAINER_CODE_DIR="/app"

echo "📁 HOST DIR      : $HOST_CODE_DIR"
echo "📁 CONTAINER DIR : $CONTAINER_CODE_DIR"
echo "🐳 IMAGE         : $IMAGE_NAME"
echo "🚀 SUBMISSION ID : $SUBMISSION_ID"


#docker运行脚本
docker run --rm \
  --name "run_task_${SUBMISSION_ID}" \
  --cpus="0.5" -m 128m \
  --pids-limit=64 \
  --network none \
  -v "${HOST_CODE_DIR}:${CONTAINER_CODE_DIR}" \
  -w "${CONTAINER_CODE_DIR}" \
  "${IMAGE_NAME}" \
  /bin/bash -c "
    /usr/bin/time -f 'TIME:%e\nMEM:%M' -o time.out \
    timeout 2s ${RUN_CMD} < input.in > output.out 2>error.out
  "

if [ $? -eq 0 ]; then
    echo "SUCCESS"
else
    echo "FAILURE"
    exit 1
fi

#echo "📄 Listing files in HOST_CODE_DIR:"
#ls -l "${HOST_CODE_DIR}"


# ./run_test.sh b17f24ff026d40949c85a24f4f375d42 "./main" "oj-cpp-env" "/mnt/e/Java/ymcloud/files"
# ./run_test.sh b17f24ff026d40949c85a24f4f375d42 "java Main" "oj-java-env" "/mnt/e/Java/ymcloud/files"
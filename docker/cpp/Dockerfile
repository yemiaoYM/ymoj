FROM gcc:10

RUN sed -i 's/deb.debian.org/mirrors.tencent.com/g' /etc/apt/sources.list && \
    sed -i 's/security.debian.org/mirrors.tencent.com/g' /etc/apt/sources.list

RUN apt update && apt install -y time

WORKDIR /app

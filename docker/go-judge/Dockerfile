# 官方 manifest list，构建脚本固定 linux/amd64；2026-10-13 核验并拉取。
FROM criyle/go-judge:v1.13.3@sha256:97bec66d054e4d844e0ad29dfdd5c79ef3d09efba25b06dd58643a6e99918dcd AS upstream
FROM debian:bookworm-slim@sha256:a4672c0cb26fbdde88e38fa2dfb6c681942306680e41e4378b28770b6e79ee91 AS toolchain

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
COPY docker/go-judge/packages.lock /opt/ymcloud/packages.lock
# slim 不带 CA，先用已核验快照索引内的包摘要引导 HTTPS；不跳过 TLS 验证。
ADD --checksum=sha256:62b08a77d985d4253894b1f69aebda5925034ca4e294add364167fad8cb64a44 https://snapshot.debian.org/archive/debian-security/20261012T000000Z/pool/updates/main/c/ca-certificates/ca-certificates_20250419~deb12u1_all.deb /tmp/ca-certificates.deb
RUN dpkg-deb -x /tmp/ca-certificates.deb /tmp/ca-bootstrap \
    && mkdir -p /etc/ssl/certs \
    && cat /tmp/ca-bootstrap/usr/share/ca-certificates/mozilla/*.crt > /etc/ssl/certs/ca-certificates.crt \
    && rm -rf /tmp/ca-bootstrap /tmp/ca-certificates.deb
# 固定快照锁定传递依赖；保留 Debian 签名验证，仅关闭快照的过期日期检查。
RUN --mount=type=bind,from=deb-cache,target=/opt/deb-cache,readonly \
    rm -f /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources \
    && printf '%s\n' \
       'deb [check-valid-until=no] https://snapshot.debian.org/archive/debian/20261012T000000Z bookworm main' \
       'deb [check-valid-until=no] https://snapshot.debian.org/archive/debian-security/20261012T000000Z bookworm-security main' \
       > /etc/apt/sources.list \
    && apt-get -o Acquire::By-Hash=false -o Acquire::Retries=3 \
       -o Acquire::CompressionTypes::Order::=xz update \
    && mkdir -p /var/cache/apt/archives/partial \
    && for cached in /opt/deb-cache/*.deb; do \
         [ -f "$cached" ] || continue; \
         package="$(dpkg-deb --field "$cached" Package)"; \
         version="$(dpkg-deb --field "$cached" Version)"; \
         expected="$(apt-cache show "$package=$version" | sed -n 's/^SHA256: //p' | head -n 1)"; \
         test "${#expected}" = 64 \
           && printf '%s  %s\n' "$expected" "$cached" | sha256sum --check --status \
           && cp "$cached" /var/cache/apt/archives/ || exit 1; \
       done \
    && xargs -r apt-get install -y --no-install-recommends < /opt/ymcloud/packages.lock \
    && dpkg-query -W -f='${Package}=${Version}\n' | sort > /opt/ymcloud/packages.manifest \
    && rm -rf /var/lib/apt/lists/*

COPY ymcloud-judge/src/main/c/ymcloud-output-limit.c /opt/ymcloud/output-limit.c
RUN gcc -std=c11 -O2 -Wall -Wextra -Werror /opt/ymcloud/output-limit.c \
        -o /usr/local/bin/ymcloud-output-limit \
    && chmod 0555 /usr/local/bin/ymcloud-output-limit \
    && rm /opt/ymcloud/output-limit.c
COPY docker/go-judge/verify_versions.py docker/go-judge/healthcheck.py /opt/ymcloud/
RUN python3 /opt/ymcloud/verify_versions.py --toolchain-only

FROM toolchain AS runtime
COPY --from=upstream /opt/go-judge /opt/go-judge
COPY docker/go-judge/mount.yaml /opt/mount.yaml
COPY docker/go-judge/entrypoint.py /opt/ymcloud/entrypoint.py
RUN mkdir -p /var/lib/go-judge/files /var/lib/go-judge/run \
    && chmod 0700 /var/lib/go-judge/files /var/lib/go-judge/run \
    && python3 /opt/ymcloud/verify_versions.py
WORKDIR /opt
EXPOSE 5050
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD ["python3", "/opt/ymcloud/healthcheck.py"]
ENTRYPOINT ["python3", "/opt/ymcloud/entrypoint.py"]

# Worker 与沙箱共用只读软件层；Worker 不具有 Docker 或主机管理权限。
FROM toolchain AS worker
RUN groupadd --gid 10001 ymcloud \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin ymcloud \
    && mkdir -p /app /data/work /data/cache /data/results \
    && chown -R 10001:10001 /data
COPY ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar /app/worker.jar
COPY docker/go-judge/worker-entrypoint.py docker/go-judge/worker-healthcheck.py /opt/ymcloud/
USER 10001:10001
WORKDIR /app
ENTRYPOINT ["python3", "/opt/ymcloud/worker-entrypoint.py"]

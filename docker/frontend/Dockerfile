# 默认值同样固定摘要；发布脚本始终传入基础镜像锁中的已核验引用。
ARG NGINX_IMAGE=nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
FROM ${NGINX_IMAGE}
ARG YMCLOUD_VERSION
LABEL org.opencontainers.image.version="${YMCLOUD_VERSION}"
RUN test -n "$YMCLOUD_VERSION"
COPY docker/frontend/nginx.conf /etc/nginx/nginx.conf
COPY ymcloud-vue/dist/ /usr/share/nginx/html/
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["nginx", "-g", "daemon off;"]

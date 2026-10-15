# ymcloud 单机部署

这套部署一次启动整个网站：前端、Web、MySQL、Redis、RabbitMQ、Worker 和 go-judge。所有日常配置集中在 `.env`，部署只需要 Docker，无需安装 Java、Maven、Node 或 Python。Compose 已填写七个镜像的版本标签，默认从腾讯云下载；拉取失败时可切换到 Docker Hub 主仓库。

> **默认配置只用于快速部署测试，不可直接用于正式上线。** `.env` 已提供可用的公开测试密码和密钥，可以直接启动。正式上线前，必须将 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`、`TOKEN_SECRET`、`WORKER_TOKEN`、`GO_JUDGE_TOKEN` 全部改为独立随机值，并修改网站初始管理员密码和邮箱后再对外开放。

## 开始使用

准备 Docker Engine 和 Docker Compose v2.20 或更新版本，使用 Linux amd64 容器、cgroup v2。Windows Docker Desktop 可以本机体验。生产最低 2 核 4GB，判题并发先保持 1；Docker Desktop 的测试不能代替目标 Linux 服务器的容量验收。

**第一步：取得已发布版本的源码并进入部署目录。** 下载并解压源码，或克隆仓库后切换到需要的发布版本。在项目根目录执行：

```shell
cd deploy
```

源码中的 Compose 直接读取同一仓库的 `../sql/ymcloud.sql`，无需生成或手工复制初始化文件。请保留源码的目录结构；不要只复制 `deploy/` 后丢掉相邻的 `sql/`。维护者生成的独立部署目录已经包含 `initdb/ymcloud.sql`，进入该目录也可按下面步骤启动。

**第二步：按需编辑 `.env`。** 快速测试可以保留全部默认值；端口被占用时修改 `HTTP_PORT`。正式部署请先按文件中的中文注释修改 4 个密码和 3 个独立服务密钥，可以用密码管理器生成随机字符串，建议只用大小写字母和数字。其他参数默认即可，不用填写容器地址。

默认 `IMAGE_REPOSITORY=ccr.ccs.tencentyun.com/ymoj/ymcloud`，从腾讯云公开仓库下载。拉取失败时，只改成 `IMAGE_REPOSITORY=docker.io/yemiao1/ymoj`，七个镜像会一起切换到 Docker Hub 主仓库。两个仓库都无需登录；镜像版本已随当前发布配置提供，不用再填写版本号或修改 Compose。

Compose 中的 `${变量:-默认值}` 表示变量未设置或为空时使用默认值，所以不要用留空来代替正式密码。部署目录只提供 `.env`，无需复制或重命名 `.env.example`。启动检查已内置在镜像中，部署目录不放 Python 脚本。

**第三步：启动网站。**

```shell
docker compose up -d
docker compose ps -a
```

第一次启动会拉取本机缺少的镜像，自动导入空数据库并等待依赖健康。`preflight` 是一次性配置检查，正常状态为 `Exited (0)`；其余 7 个服务应为 `Up` 且最终为 `healthy`。下载和初始化可能需要几分钟。旧版独立命令 `docker-compose` 只在其支持此 Compose 配置时可用，文档统一使用 Docker Compose v2 的 `docker compose`。

访问 `http://服务器IP`；端口改为 8080 时访问 `http://服务器IP:8080`。后台路径 `/admin/login`。全新安装管理员为 `admin` / `Ymcloud@123`，首次登录后修改密码和邮箱。创建题目、上传 `.in/.out` 数据，再提交代码确认判题。

本地体验可以设置 `HTTP_BIND=127.0.0.1`，浏览器访问 `http://localhost:端口`。只有网站端口对外发布；数据库、Redis、RabbitMQ、内部下载接口和沙箱接口均不映射宿主端口。公开 HTTPS 网站可在网站端口前配置现有 TLS 反向代理。

## 日常使用

```shell
# 查看状态、查看应用日志
docker compose ps -a
docker compose logs --tail 100 web worker

# 停止网站，保留全部数据
docker compose down

# 再次启动
docker compose up -d
```

`docker compose config` 会展开密码，请不要截图或发送其完整输出；需要检查语法可用 `docker compose config --quiet`。

Web 和 Worker 都会输出 UTF-8 控制台日志，并在各自容器 `/data/logs` 保存 `application.log` 与仅含 ERROR 的 `error.log`。控制台保留 INFO，方便用上面的命令观察启动和判题；文件日志没有终端颜色字符。默认按天或单文件达到 20MB 滚动，历史日志 gzip 压缩，每一类最多保留 15 天、归档总量 200MB，启动时也清理过期归档；当前正在写入的文件不计入归档上限。可在 `.env` 的“应用日志”部分调整，运行日志与判题结果恢复记录分开保存。

更改 `.env` 后运行 `docker compose up -d`。数据库和 RabbitMQ 的账号密码在首次初始化后存入各自数据卷，不能只改 `.env` 来修改已有密码；应先按对应服务的账号管理流程修改，再同步配置。不要通过删卷来解决密码不匹配。

## 数据保存和备份

Docker 自动建立项目专用数据卷，无需手工建目录或修改权限：

| 数据卷 | 内容 |
| --- | --- |
| `mysql-data` | 用户、题目、比赛、提交与语言元数据 |
| `web-data` | 上传文件、私有测试数据、存储加密主密钥 `.secrets/storage-config.key` |
| `web-logs` | Web 运行日志及轮转归档 |
| `redis-data` | 登录缓存、判题租约与状态，启用 AOF |
| `rabbitmq-data` | 持久化判题任务、重试及结果消息 |
| `worker-data` | 测试数据缓存、工作目录、结果恢复记录及 `logs` 运行日志 |
| `judge-files` | go-judge 独占文件缓存，正常清理并有 TTL 兜底 |

实际卷名有项目名前缀，例如 `ymcloud_mysql-data`。**安装后不要随意更改 `COMPOSE_PROJECT_NAME`**，否则会启动一套新卷，看起来像数据消失。不要执行 `docker compose down -v` 或删除这些卷，除非明确要永久清空这套系统。

备份时暂停提交并排空任务/重试/结果队列，确认没有活跃租约，再停止应用写入并备份数据库及全部持久卷。数据库和 `web-data` 内的加密主密钥必须成套保存。保留 `.env`、当前版本的部署配置、镜像与 Worker 结果日志，备份应存放在受限位置，以便按上一版本恢复。不要直接复制正在写入的 MySQL 数据文件作为一致性备份。

默认文件存储使用 `web-data`，因此首次安装不需要 SeaweedFS。已有对象存储可以在后台配置，需提供 Web 可访问的 HTTPS 端点。不要把旧本机 `127.0.0.1` 地址直接当作新容器内的存储地址。

判题临时文件不用手工定时删除：源码上传文件在编译结束后删除，每点输入在该点结束后删除，编译产物复用于全部测试点后随任务关闭清理，异常和取消也执行清理。沙箱无法连接时由默认 1 小时 TTL 兜底，服务重启还会清理独占的 `files`、`run` 两个子目录。Worker 的测试数据缓存按 5GiB 容量淘汰无引用版本；`results` 内的正式结果默认保留 7 天，每分钟检查一次，用于重复消息与结果恢复。原子写入异常遗留的 `.result-*.tmp` 超过 1 小时后清理，不删除正在写入的临时文件。不要把整个 `worker-data` 当作临时目录删除。

## 更新已有系统

这套自动初始化面向**全新空卷**，不是把当前开发库自动搬进 Docker 的迁移工具。MySQL 只在空数据目录首次启动时运行挂载的初始化 SQL，普通重启不会重新导入、清空或更新表结构。已有数据迁移需要先备份并按版本核对结构与文件路径。

升级时取得新发布版本的源码和部署配置。暂停提交、排空任务/重试/结果队列并备份，保留原 `.env`、项目名和持久卷，按新版本公开模板补齐新增配置。源码部署同时保留对应的 `sql/ymcloud.sql`；独立部署目录更新脚本生成的 `initdb`。如版本需要数据库迁移，必须先按该版本说明执行；不能把新安装 SQL 导入已有库来代替迁移。然后在新部署目录执行：

```shell
docker compose pull
docker compose up -d
```

配置默认复用本机已有镜像，因此升级或需要重新下载同名版本标签时，先执行 `docker compose pull`；首次部署只需 `docker compose up -d`。四语言及业务冒烟通过后恢复提交。回退时使用上一版本的部署配置、对应镜像及数据库恢复方案，不在代码中保留双执行引擎。

使用本机构建镜像的维护者，应先在同一 Docker 环境完成新版本构建，并用 `--output` 生成到新的空目录，再按上述维护窗口替换配置。保留本地镜像，不能把只含本地 image ID 的配置直接交给另一台机器使用。

## 出错时看这里

| 现象 | 操作 |
| --- | --- |
| `preflight` 退出码非 0 | `docker compose logs preflight`；按字段名修正密码或容量配置，不会输出实际密钥 |
| 腾讯云镜像拉取失败 | 将 `.env` 中 `IMAGE_REPOSITORY` 改为 `docker.io/yemiao1/ymoj`，再执行 `docker compose up -d` |
| 需要重新下载已有版本标签 | 先执行 `docker compose pull`，成功后执行 `docker compose up -d` |
| 本机构建配置提示找不到镜像 | 确认使用构建时的 Docker 环境；重新构建并生成匹配配置 |
| 提示初始化 SQL 路径不存在 | 源码部署需保留相邻的 `sql/ymcloud.sql`；独立目录需包含生成的 `initdb/ymcloud.sql`，不能创建空目录代替文件 |
| MySQL 一直不健康 | 看 `docker compose logs --tail 100 mysql`；检查初始化错误、磁盘及是否误改原密码 |
| Web 不健康 | 看 Web 日志，检查 MySQL、Redis、RabbitMQ 是否健康 |
| Worker 不健康 | 看 Worker 日志，检查沙箱版本、默认 seccomp、cgroup v2 和两个服务密钥 |
| 前端端口占用 | 修改 `.env` 的 `HTTP_PORT` 后重新启动 |
| 题目得到容量不足的 SE | 题目限额超过当前支持范围；配套提高 `.env` 中内存、并发、CPU 和进程预算 |

go-judge 默认关闭上游日志，防止启动日志打印令牌；诊断信息由 Worker 输出。默认保留 stdout/stderr 合计 1MiB、单文件及编译产物 16MiB、编译墙钟 10 秒等边界。调高并发不等于整机具有相应容量，应在目标机器重新做业务和公平性验收。

## 维护者构建镜像与部署配置

在源码仓库根目录执行：

```shell
python docker/release.py
```

此命令仅供维护者，需要 JDK 17、Maven、Node/npm、Python 和 Docker Buildx。它构建干净的 Web/Worker jar 和前端，核验固定基础镜像及工具链，并构建、验证 `ymcloud-web:4.0.0`、`ymcloud-worker:4.0.0`、`ymcloud-go-judge:4.0.0`、`ymcloud-frontend:4.0.0`。默认将本地部署配置及内部构建记录写入 `target/phase3-verification/full-stack-deployment`，镜像保留在本机 Docker 中。这些构建记录用于维护者核对内容，普通部署用户无需生成或维护。`--output` 可指定新的空目录；脚本不会覆盖已有私有配置。`--skip-build` 只跳过 Java/前端编译，仍验证产物并构建镜像。

本机构建生成的 Compose 使用已验证的裸 image ID 和 `pull_policy: never`，进入输出目录、修改 `.env` 后同样执行 `docker compose up -d`。这套配置只使用当前 Docker 中的镜像；其 `.env` 中的 `IMAGE_REPOSITORY` 不会改变固定的本地 image ID，也无需运行 `docker compose pull`。公开部署的 `deploy/compose.yaml` 使用仓库路径和版本标签，在缺少镜像时自动拉取。

源码 `deploy/.env` 保存公开测试默认值，生成部署配置时会核对其与 Compose 默认值一致。在源码目录部署时，实际密码只留在当前机器的 `.env`，不要提交这些修改；维护者生成配置应使用保留公开默认值的干净源码。启动检查源码位于 `docker/web/preflight.py`，随 Web 镜像构建，部署用户无需执行或维护它。

初始化 SQL 始终只维护 `sql/ymcloud.sql`。源码 Compose 直接绑定此文件；生成独立目录时，脚本自动复制到 `initdb/ymcloud.sql` 并调整挂载路径。绑定设置 `create_host_path: false`，文件缺失时直接报错，不自动创建同名目录。部署目录不含 Python 脚本，也不生成镜像归档。

项目版本唯一配置在根 `pom.xml` 的 `<revision>`，本次为 `4.0.0`。修改此处后重新构建即可同步 Java 模块、前端项目版本及四个自研镜像的版本标签；维护者发布时将公开 Compose 的四个项目标签更新为该版本，脚本会拒绝与父 POM 不一致的部署模板。部署用户不需要在 `.env` 中再填写版本。内部构建记录保存版本与内容信息，自研镜像同时包含 OCI 版本标签。MySQL、Redis、RabbitMQ 和 go-judge 二进制保留各自的上游版本。

`--skip-build` 要求现有 `ymcloud-web.jar`、`ymcloud-judge.jar` 内的 Maven 版本和前端 `dist/version.json` 均与父 POM 一致，否则拒绝发布。不要手工修改版本文件或给旧镜像重打标签来代替实际构建。

## 维护者发布公开镜像

先完成本机构建和回归，并确认 Docker Hub 的 `yemiao1/ymoj` 为公开仓库。推送到腾讯云之前，必须先在个人版控制台选择命名空间 `ymoj`、新建仓库 `ymcloud`，类型选择“公有”，再验证公开访问。不要依赖 Docker 自动创建来保证仓库为公有；登录后能拉取镜像也不能证明匿名用户可以拉取。腾讯云的创建入口和公有类型说明见[个人版快速入门](https://cloud.tencent.com/document/product/1141/63910)。

确认两个仓库后登录。以下登录只供有发布权限的维护者使用，普通部署用户不需要登录：

```shell
docker login docker.io
docker login ccr.ccs.tencentyun.com
```

发布同一批已验证镜像到 `docker.io/yemiao1/ymoj` 与 `ccr.ccs.tencentyun.com/ymoj/ymcloud`，两个仓库均设为公开。每端只使用这一个仓库，通过“服务名-版本”标签区分七个镜像。四个项目镜像的版本读取父 POM；三个基础服务镜像直接复制已锁定的上游内容，不自行替换软件版本：

| 服务 | 本次发布标签 |
| --- | --- |
| Web | `web-4.0.0` |
| Worker | `worker-4.0.0` |
| go-judge | `go-judge-4.0.0` |
| 前端 | `frontend-4.0.0` |
| MySQL | `mysql-8.4` |
| Redis | `redis-7.4-alpine` |
| RabbitMQ | `rabbitmq-4.1-alpine` |

将同一批已验证的本地镜像分别推送到两个公开仓库。基础服务使用本次内部构建记录中的镜像，四个项目镜像使用本次构建产物；以下以 Web 为例，其余六个服务按表中标签处理：

```shell
docker tag ymcloud-web:4.0.0 docker.io/yemiao1/ymoj:web-4.0.0
docker push docker.io/yemiao1/ymoj:web-4.0.0
docker tag ymcloud-web:4.0.0 ccr.ccs.tencentyun.com/ymoj/ymcloud:web-4.0.0
docker push ccr.ccs.tencentyun.com/ymoj/ymcloud:web-4.0.0
```

推送完成后，按[维护者公开镜像发布验收](../docker/go-judge/README.md#公开镜像发布验收)检查两端七个版本标签的匿名拉取、平台和镜像内容，完成部署回归后再发布对应的源码版本。这些验证仅由维护者执行，部署用户只需使用随发布版本提供的 Compose 和 `.env`。

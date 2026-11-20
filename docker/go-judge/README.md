# go-judge v1.13.3 部署与验收

Worker 只通过 JDK 17 HTTP 客户端调用一个 go-judge 执行服务。RabbitMQ 任务/结果队列、Redis 租约与缓存、固定数据版本、结果幂等、历史提交均沿用现有实现。Docker 只部署两个常驻服务，Worker 不访问 Docker Socket。

本页用于已有 Web、Redis、RabbitMQ 的独立判题节点，保留维护者本机构建与两服务部署流程。完整网站请使用 [单机 Docker 部署](https://gitee.com/yemiaoo/ymoj-deploy)：克隆独立部署仓库，按指南准备 `.env.local` 后执行 `docker compose --env-file .env.local up -d`，按版本标签拉取七个镜像。默认使用腾讯云；拉取失败时，只需将 `.env.local` 中的 `IMAGE_REPOSITORY` 改为 Docker Hub 主仓库 `docker.io/yemiao1/ymoj`。下面的构建锁和内容核对仅用于维护者，不需要普通部署用户操作。

## 固定构建

本轮生产目标为 Linux amd64、cgroup v2，最低同机 2 核 4GB，共享核心，只设资源限额，不设置 CPU 绑核。使用 Docker Engine 与 Compose v2，构建需要 Buildx、JDK 17、Maven 及 Python 3；软件包均来自 Debian 预构建包。

2026-10-13 已从 Docker Hub 官方 tag API 核验并实际拉取：

| 来源 | 固定摘要 |
| --- | --- |
| `criyle/go-judge:v1.13.3` manifest list | `sha256:97bec66d054e4d844e0ad29dfdd5c79ef3d09efba25b06dd58643a6e99918dcd` |
| 同版本 linux/amd64 manifest | `sha256:537632f86eef5eac173b1738fa24dcfba51c53fba5cff64435452d675157619f` |
| `debian:bookworm-slim` linux/amd64 manifest | `sha256:a4672c0cb26fbdde88e38fa2dfb6c681942306680e41e4378b28770b6e79ee91` |

来源可复核：[go-judge 发布](https://github.com/criyle/go-judge/releases/tag/v1.13.3)、[官方镜像 tag](https://hub.docker.com/v2/repositories/criyle/go-judge/tags/v1.13.3)、[Debian tag](https://hub.docker.com/v2/repositories/library/debian/tags/bookworm-slim)。官方安装说明允许从官方镜像复制 `/opt/go-judge`，工具链由运行镜像提供。[官方安装说明](https://docs.goj.ac/cn/install)

仓库固定 `20261012T000000Z` 的 Debian 主仓及安全仓快照；`packages.lock` 固定直接安装版本，传递依赖由快照锁定。APT 继续校验 Debian 签名，只关闭历史快照的过期日期检查，不使用不可信软件仓库。

| 入口 | 安装版本 / 标准 |
| --- | --- |
| Java | OpenJDK `17.0.20.1+1-1~deb12u1` headless |
| C、C++ | GCC/G++ `12.2.0-14+deb12u1`，C11 / C++17 |
| Python | Python `3.11.2-6+deb12u9`，保留语法编译检查 |

在仓库根目录执行：

```bash
mvn -pl ymcloud-judge -am package -DskipTests
bash docker/build.sh
```

ymcloud 的发布版本统一读取根 `pom.xml` 的 `<revision>`，从 `4.0.0` 开始。脚本构建 `ymcloud-go-judge:4.0.0` 和 `ymcloud-worker:4.0.0`，并把同一版本写入镜像 OCI 标签及发布锁；后续升级由用户明确指定目标版本，按 `docs/代码规范.md` 的 G-VERSION-001 手动核对并更新前后端项目版本，再重新打包、构建；构建命令不自动改写源码版本。Worker 输入为稳定路径 `ymcloud-judge/target/ymcloud-judge.jar`，构建前核对 JAR 中的 Maven 版本。这里的项目版本与上游 go-judge `v1.13.3` 分别记录，避免把上游版本当作网站发布版本。

脚本编译可信输出限制程序并验证包版本、四入口工具链及 go-judge 版本。维护者的构建记录写入 `target/phase3-verification/images.lock.json`，本地两服务 Compose 使用 `images.env` 中的 image ID；构建失败不会生成可部署的占位锁。镜像保留在本机 Docker 中，不导出镜像归档。公开发布时，将同一批构建镜像分别推送到 `docker.io/yemiao1/ymoj` 与 `ccr.ccs.tencentyun.com/ymoj/ymcloud`，使用 `go-judge-4.0.0`、`worker-4.0.0` 标签，并核对两端对应镜像的 config image ID 和平台。完整网站的 `deploy/compose.yaml` 直接使用仓库地址与版本标签，普通部署用户无需处理构建锁。

升级锁定软件包或基础镜像时重新构建、验证及生成镜像锁；不得只手工修改 `images.env`。固定 snapshot 和版本保证依赖来源确定，不声称不同 BuildKit 环境必定生成逐字节相同镜像。

网络受限时可把同一固定快照的 `.deb` 放入忽略目录 `target/phase3-verification/packages/`，再次执行相同构建脚本。构建将此目录作为只读可选缓存，仅在 Debian 签名验证后的索引中找到相同包版本、SHA-256 校验完全一致时复用；缺失包仍从固定快照下载，缓存为空也可正常构建。软件包不加入 Git，也不把本地缓存作为新的受信任仓库。

## Linux 部署

Compose 仅启动 Worker、go-judge，连接已有 Web、Redis、RabbitMQ。MySQL 仍由 Web 使用。Web 服务令牌和 go-judge 令牌必须分别生成，均至少 32 个字符；不复用 JWT、数据库密码或存储密钥。

```bash
cp docker/go-judge/.env.example docker/go-judge/.env
install -d -m 700 /srv/ymcloud/go-judge
install -d -m 700 -o 10001 -g 10001 /srv/ymcloud/worker
```

创建目录的账户需要 `/srv` 写权限。填写 `.env` 中的独占目录、连接信息和令牌；可用 `openssl rand -hex 32` 分别生成令牌，保存至受限私有配置。执行：

```bash
python3 docker/go-judge/deploy.py --check-only
python3 docker/go-judge/deploy.py
```

Worker 为 UID/GID 10001，去除全部 capabilities、禁止获得新权限，使用宿主网络访问已有回环服务。go-judge 使用独立桥接网络，只映射 `127.0.0.1:5050`。执行服务需要 privileged 建立嵌套 namespace，但不挂载主机 Docker Socket、主机根目录或 systemd D-Bus；使用私有 cgroup namespace，子 cgroup 必须继承该容器总限额。不得添加 `/sys/fs/cgroup` 的主机根绑定挂载而绕过容器预算。

go-judge 保持 v1.13.3 内置默认 seccomp；入口清除外部 `ES_*` 覆盖，只设置独立鉴权，并固定 `-no-fallback=true`、`-no-seccomp=false`、`-enable-cpu-rate=true`。`-silent=true` 防止上游启动日志输出 token，Worker 提供中文诊断日志。不以关闭 seccomp 或回退 rlimit 解决启动失败。

| 限额 | 默认值 |
| --- | --- |
| Worker / 沙箱并发 | 1 / 1 |
| go-judge 总 CPU / 内存 | 1 核 / 1GiB，swap=0 |
| 单题支持上限 / 编译内存 | 512MiB / 512MiB |
| 编译墙钟 | 10 秒 |
| 单次沙箱线程及进程合计 | 64，包含可信输出程序及 JVM 内部线程 |
| stdout + stderr | 合计 32MiB，通过缓存文件流传输 |
| 编译产物 / 单文件 | 64MiB / 64MiB |
| Worker 上传请求 / 执行响应 | 65MiB / 8MiB；输出正文不进入执行响应 JSON |
| 每个沙箱 `/w`、`/tmp` | 各 64MiB tmpfs |
| Worker 内存 / CPU | 384MiB / 0.5 核；Java 堆 128MiB |

总内存预算还需覆盖 tmpfs 和服务自身开销。`deploy.py` 要求总预算同时满足 `并发 × (max(题目上限, 编译内存) + 128MiB) + 128MiB` 和 Worker 的 `并发 × (max(题目上限, 编译内存) + 64MiB) + 256MiB`；CPU 和进程总预算也必须与每任务配置配套。增加 `YMCLOUD_WORKER_CONCURRENCY` 时同步调整 `YMCLOUD_GO_JUDGE_CONCURRENCY`、服务 CPU/内存/进程预算。无 CPU 绑核和自动加时补偿。超出部署容量的题目直接终态 SE，说明容量不足，不截短题目内存、不重复执行。

文件缓存目录 `/var/lib/go-judge` 由一个实例独占，必须位于独立受控磁盘目录，配置磁盘容量监控。服务启动持有目录锁，再清空旧 `files`、`run` 子目录，防止异常退出遗留文件复用；默认 1 小时 TTL，不短于最大 1 小时任务截止窗口，重试共用原截止时间。Worker 按点删除输入、任务结束删除编译产物；服务重启导致缓存文件消失时按原任务有限重试，重新准备源码和数据。Worker 只清理 `/data/work`，保留 `/data/cache`、`/data/results`；不得在升级时删除结果日志。

编译器、运行库与 Java 配置只读进入沙箱。Java 产物保存完整 `.class` 集合，含内部类、辅助类；C/C++ 保存可执行文件；Python 保留语法检查。测试点使用新的工作环境，复用同一份编译产物。内存统计统一 KiB，时间统一毫秒；业务限时及展示使用墙钟 `runTime`，CPU 时间只作保护和诊断。多点继续取最大时间、最大内存。编译、下载、队列等待另记。

go-judge 健康探针检查鉴权 `/file`；Worker 健康探针确认 Java 进程及消费者心跳不超过 30 秒。心跳仅在应用就绪、RabbitMQ 消费者运行且活跃数量满足并发要求时每 10 秒更新，启动清理旧心跳。Worker 消费前仍独立校验版本、鉴权、cgroup、默认 seccomp 及工具链。go-judge 日志默认关闭；Docker 日志分别限制为 2×5MiB、3×10MiB。

CPU 保护预算为 `(墙钟限时毫秒 + 100) × max(2, ceil(cpuRateLimit / 1000))`，其中 100 毫秒只覆盖默认 CFS 周期突发；`clockLimit` 和结果 `runTime` 严格使用原题墙钟时限，未给业务限时加时。

Worker 运行日志使用独立的 `/data/logs`，包含 `application.log` 和仅含 ERROR 的 `error.log`；控制台保留 INFO，均为 UTF-8。每类日志按日或 20MB 滚动、gzip 压缩，默认保留 15 天且归档总量不超过 200MB，启动也清理过期归档。文件日志通过异步队列写入，不采集额外调用位置；正常关闭会等待日志刷新。诊断日志不同于 `/data/results` 的结果恢复记录，后者默认保留 7 天、每分钟检查一次。异常退出留下的 `.result-*.tmp` 超过 1 小时才回收，写入与清理互斥，不删除活跃写入或未过期正式结果。

## 回归与公平性

各入口验收应分别记录编译成功/失败、死循环、休眠、内存超限、崩溃、输出超限、多点、空输入、Unicode/空白、Java 多类、子进程、文件污染及隔离限制。业务层还需普通提交、比赛、自测、固定数据版本、ACM/OI、状态轮询、AC 统计、服务中断、文件丢失、租约丢失、Worker 重启、重复消息及结果恢复。未执行的项目不得填写 PASS。

公平性每个入口使用固定源代码，每档预热 5 次、测量至少 30 次。在目标机器采集两批空闲样本，再分别采集常规业务负载和满载样本。

报告保留每次 CPU/墙钟、内存、请求总时间及 `请求总时间 - runTime`，最后一项包含等待及传输，不能冒充纯 RabbitMQ 等待时间；队列等待以 Worker 的队列指标补充。记录 CPU/墙钟 p50/p95、宿主 `/proc/stat` 的 CPU steal 和 cgroup `cpu.stat` 限流差值。虚拟机内 steal 只反映该层可观测信息。

默认门槛：两批空闲墙钟 p95 漂移不超过 10%，常规负载相对第一批空闲不超过 20%，空闲样本至少保留 20% 限时余量，空闲/常规负载下所有 AC 样本无误判 TLE。满载用于容量边界，不作为隐藏 TLE 的加时依据。失败时降低承诺的业务负载或增加服务预算，重新完整采集各档。

## 上线与恢复

1. 暂停新提交，等待任务、重试、结果队列 ready/unacked 均归零。检查 `judge_dispatch` 非终态任务以及 Redis 活跃租约已收敛；不要清空队列跳过未完成结果。
2. 保留当前部署配置、镜像版本锁、数据库备份、Worker 缓存及结果日志。确认新版本构建锁、版本验证与 Linux 验收报告齐全。
3. 新安装在空数据库导入 `sql/ymcloud.sql`。已有数据库需按实际版本单独准备迁移方案，将四种语言的展示版本与统一工具链对齐，保留 ID、image/env、模板、状态、历史提交和任务快照；不直接导入初始化文件。
4. 停止旧 Worker，按不可变镜像锁替换 Worker 和 go-judge。四语言与普通/比赛/自测、状态、AC 统计冒烟通过后恢复提交。
5. 恢复旧部署时重新暂停、排空，再恢复旧版部署配置、对应镜像及其语言元数据；保留现有历史记录与结果日志。不在新代码保留双引擎切换开关。

## 故障排查

| 现象 | 处理 |
| --- | --- |
| Worker 不开始消费 | 查看 Worker 启动诊断；核对 go-judge 版本、鉴权、默认 seccomp、cgroup v2 及工具链 |
| 文件 ID 404 / File Error | 视为系统故障，按原任务有限重试并重新准备；检查服务重启、TTL 和磁盘容量 |
| 未知状态 / Internal Error | 视为 SE，检查服务健康与受限资源；不能按 AC 或用户文本猜测状态 |
| 题目内存超容量 | 明确 SE 终态；扩容并配套调整全部预算，不截短题目限制 |
| 编译资源超限 | CE；编译限额独立于题目运行限额 |
| 运行输出超限 / 非零退出 / 信号 | RE；不要根据 stderr 文本判断 TLE/MLE |
| cgroup 回退、只读或 controller 缺失 | 检查 Docker、Linux v2 delegation；保持 fail-closed，不映射主机 cgroup 根来绕过限额 |
| 启动提示缓存目录锁定 | 存在另一实例共用目录；为实例分配独立目录，不手工删除活跃锁 |
| 镜像摘要或软件包下载校验失败 | 检查网络代理和官方仓库连通性，保留固定摘要与 Debian 签名验证；不得换浮动 tag |
| p95 漂移、限流增加 | 对照 steal、限流与业务请求量，降低支持负载或调整配套资源；不自动加时 |

全部文件使用 UTF-8。SQL、配置及文档提交/推送前必须取得针对具体文件的审批；本地 `AGENTS.md` 不进入 Git。

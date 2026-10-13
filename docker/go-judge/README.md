# go-judge v1.13.3 部署与验收

Worker 只通过 JDK 17 HTTP 客户端调用一个 go-judge 执行服务。RabbitMQ 任务/结果队列、Redis 租约与缓存、固定数据版本、结果幂等、历史提交均沿用现有实现。Docker 只部署两个常驻服务，Worker 不访问 Docker Socket。

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

脚本编译可信输出限制程序并验证包版本、四入口工具链及 go-judge 版本。运行镜像与 Worker 的实际 Buildx 导出摘要、config image ID 写入 `target/phase3-verification/images.lock.json`，Compose 使用 `images.env` 中不可变 image ID；构建失败不会生成可部署的占位锁。`images.tar` 和 SHA-256 用于本地发布包运输，目标主机校验后执行 `docker image load --input images.tar`。经典 Docker 导出后端返回的导出摘要与 config image ID 相同，锁中明确标记 `digestKind=docker-config`；该摘要锁定配置和全部 rootfs 内容，不把它冒充 OCI manifest digest 或已发布仓库的 RepoDigest。本轮不上传镜像仓库，发布渠道与流水线另行规划。

升级锁定软件包或基础镜像时重新构建、验证及生成整套发布包；不得只手工修改 `images.env`。固定 snapshot 和版本保证依赖来源确定，不声称不同 BuildKit 环境必定生成逐字节相同镜像。

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
| stdout + stderr | 合计 1MiB |
| 编译产物 / 单文件 | 16MiB / 16MiB |
| 每个沙箱 `/w`、`/tmp` | 各 64MiB tmpfs |
| Worker 内存 / CPU | 384MiB / 0.5 核；Java 堆 128MiB |

总内存预算还需覆盖 tmpfs 和服务自身开销。`deploy.py` 要求总预算同时满足 `并发 × (max(题目上限, 编译内存) + 128MiB) + 128MiB` 和 Worker 的 `并发 × (max(题目上限, 编译内存) + 64MiB) + 256MiB`；CPU 和进程总预算也必须与每任务配置配套。增加 `YMCLOUD_WORKER_CONCURRENCY` 时同步调整 `YMCLOUD_GO_JUDGE_CONCURRENCY`、服务 CPU/内存/进程预算。无 CPU 绑核和自动加时补偿。超出部署容量的题目直接终态 SE，说明容量不足，不截短题目内存、不重复执行。

文件缓存目录 `/var/lib/go-judge` 由一个实例独占，必须位于独立受控磁盘目录，配置磁盘容量监控。服务启动持有目录锁，再清空旧 `files`、`run` 子目录，防止异常退出遗留文件复用；默认 1 小时 TTL，不短于最大 1 小时任务截止窗口，重试共用原截止时间。Worker 按点删除输入、任务结束删除编译产物；服务重启导致缓存文件消失时按原任务有限重试，重新准备源码和数据。Worker 只清理 `/data/work`，保留 `/data/cache`、`/data/results`；不得在升级时删除结果日志。

编译器、运行库与 Java 配置只读进入沙箱。Java 产物保存完整 `.class` 集合，含内部类、辅助类；C/C++ 保存可执行文件；Python 保留语法检查。测试点使用新的工作环境，复用同一份编译产物。内存统计统一 KiB，时间统一毫秒；业务限时及展示使用墙钟 `runTime`，CPU 时间只作保护和诊断。多点继续取最大时间、最大内存。编译、下载、队列等待另记。

go-judge 健康探针检查鉴权 `/file`；Worker 健康探针确认 Java 进程及消费者心跳不超过 30 秒。心跳仅在应用就绪、RabbitMQ 消费者运行且活跃数量满足并发要求时每 10 秒更新，启动清理旧心跳。Worker 消费前仍独立校验版本、鉴权、cgroup、默认 seccomp 及工具链。go-judge 日志默认关闭；Docker 日志分别限制为 2×5MiB、3×10MiB。

CPU 保护预算为 `(墙钟限时毫秒 + 100) × max(2, ceil(cpuRateLimit / 1000))`，其中 100 毫秒只覆盖默认 CFS 周期突发；`clockLimit` 和结果 `runTime` 严格使用原题墙钟时限，未给业务限时加时。

## 回归与公平性

测试源码位于模块 `src/test`；所有临时配置、样本产物和报告均写入忽略的 `target/phase3-verification`。使用专属测试实例与独立队列/Redis database，不对生产服务执行故障注入。Docker Desktop 结果只能作为开发验证，不能替代目标 2 核 4GB Linux 机器的公平性验收。

真实运行前设置 `YMCLOUD_RUN_GO_JUDGE_TESTS=true`、`YMCLOUD_GO_JUDGE_URL` 与 `YMCLOUD_GO_JUDGE_TOKEN`。Java 集成测试和业务验证入口：

```bash
mvn -pl ymcloud-judge -am test
python3 -B ymcloud-web/src/test/scripts/judge/verify_worker.py
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_runtime.py --container <go-judge容器名>
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_jdk17.py --container <go-judge容器名>
```

`verify_worker.py` 还需其专用 `YMCLOUD_WORKER_E2E=true` 及空的非开发 Redis database，详见根 README。`verify_runtime.py` 使用固定可信 Python 程序产生父进程及三个 `setsid` 子进程，主动关闭 HTTP 连接后检查进程消失、`pids.current` 回到基线并复用执行槽；仅出现客户端取消异常不算回收通过。脚本还从活动进程的实际 cgroup 祖先检查内存/进程限额继承，验证服务总 CPU、内存、swap、进程上限及鉴权。

在专属实例没有其他验收任务时，可给 `verify_runtime.py` 增加 `--restart`，验证服务重启后旧文件 ID 返回 404、异常退出遗留的临时文件被清理，以及新执行请求恢复；不要与业务回归或公平性采集并行重启服务。

`verify_jdk17.py` 还需 `YMCLOUD_WORKER_LINUX_TEST=true`、构建锁中的 `YMCLOUD_GO_JUDGE_IMAGE` 和专属服务令牌。它在 UID 1000、去除 capabilities 且无 Docker Socket 的临时容器中，用实际 JDK 17 编译并运行模块 `src/test` 中的客户端冒烟程序，验证四语言及中断后恢复；只共享专属 go-judge 的网络 namespace，报告位于 `target/phase3-verification/jdk17/report.json`。

四入口验收应分别记录编译成功/失败、死循环、休眠、内存超限、崩溃、输出超限、多点、空输入、Unicode/空白、Java 多类、子进程、文件污染及隔离限制。业务层还需普通提交、比赛、自测、固定数据版本、ACM/OI、状态轮询、AC 统计、服务中断、文件丢失、租约丢失、Worker 重启、重复消息及结果恢复。未执行的项目不得填写 PASS。

公平性每个入口使用固定源代码，每档预热 5 次、测量至少 30 次。在目标机器依次采集两批空闲、实际常规业务负载、满载：

当前公平性脚本校验表中的默认预算，运行前导出与部署 `.env` 相同的预算环境变量；高配参数会被明确拒绝，应先扩展采样配置并记录实际任务参数，再进行该部署的验收。

```bash
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_fairness.py --container <容器名> --tier idle-a
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_fairness.py --container <容器名> --tier idle-b
# 先从真实业务压测端施加可重复的常规或满载流量，保持相同样本和题目时限。
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_fairness.py --container <容器名> --tier normal --load-description '记录请求率、并发和业务样本集'
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_fairness.py --container <容器名> --tier full --load-description '记录饱和负载、请求率和并发'
python3 -B ymcloud-judge/src/test/scripts/gojudge/verify_fairness.py --compare
```

报告保留每次 CPU/墙钟、内存、请求总时间及 `请求总时间 - runTime`，最后一项包含等待及传输，不能冒充纯 RabbitMQ 等待时间；队列等待以 Worker 的队列指标补充。记录 CPU/墙钟 p50/p95、宿主 `/proc/stat` 的 CPU steal 和 cgroup `cpu.stat` 限流差值。虚拟机内 steal 只反映该层可观测信息。

开发环境可用模块同目录的 `load_service.py` 产生固定 200 毫秒 CPU 请求，单流加 200 毫秒间隔作为模拟常规执行负载、双流无间隔作为执行槽饱和负载。负载进程应运行在独立非特权限额容器，设置持续时间并在采集后停止；其输出记录成功/失败数和实际持续时间。这仅验证模拟执行流量，不代替目标机器上真实 Web、数据库、队列负载的常规业务验收。

默认门槛：两批空闲墙钟 p95 漂移不超过 10%，常规负载相对第一批空闲不超过 20%，空闲样本至少保留 20% 限时余量，空闲/常规负载下所有 AC 样本无误判 TLE。满载用于容量边界，不作为隐藏 TLE 的加时依据。失败时降低承诺的业务负载或增加服务预算，重新完整采集各档。

## 上线与恢复

1. 暂停新提交，等待任务、重试、结果队列 ready/unacked 均归零。检查 `judge_dispatch` 非终态任务以及 Redis 活跃租约已收敛；不要清空队列跳过未完成结果。
2. 保留整份当前发布包、数据库备份、Worker 缓存及结果日志。确认新包构建锁、版本验证与 Linux 验收报告齐全。
3. 执行 `sql/migrations/phase3-language-versions.sql`。脚本可重跑，仅按 `oj='myy'` 和四个语言名称更新展示版本，保留 ID、image/env、模板、状态、历史提交和任务快照。新安装直接使用 `sql/ymcloud.sql`。
4. 停止旧 Worker，按不可变镜像锁替换 Worker 和 go-judge。四语言与普通/比赛/自测、状态、AC 统计冒烟通过后恢复提交。
5. 恢复旧部署时重新暂停、排空，再恢复整份旧发布包及其语言元数据；保留现有历史记录与结果日志。不在新代码保留双引擎切换开关。

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

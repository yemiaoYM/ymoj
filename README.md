# ymcloud · 在线判题系统

`ymcloud` 是基于 Spring Boot 3、Vue 3 和 TypeScript 的在线判题系统，提供题库、ACM / OI 比赛、异步评测和后台管理。文件默认保存在本地，也可在后台配置 SeaweedFS、MinIO、阿里云 OSS 或其他 S3 服务。

Web 和 Java Judge Worker 分别启动：Web 负责提交鉴权、任务持久化及结果落库，Worker 通过 RabbitMQ 消费任务，下载固定版本的私有测试数据，使用 Docker 执行 Java、C、C++ 和 Python 程序。

## 项目结构

```text
ymcloud/
├── docker/                # 判题镜像及构建脚本
├── seaweedfs/             # 可选的 SeaweedFS Docker 安装工具
├── sql/ymcloud.sql        # 当前版本的建库结构与初始化数据
├── files/                 # 启动后创建的本地文件目录，不提交 Git
├── ymcloud-common/        # 常量、工具、异常和统一响应
├── ymcloud-pojo/          # 实体、DTO、VO；contract/judge 存放共用判题契约
├── ymcloud-core/          # 鉴权、数据访问、缓存、消息与文件存储
├── ymcloud-judge/         # 独立 Java Worker、版本缓存、Docker 沙箱与评分
├── ymcloud-web/           # OJ 和后台接口、业务服务、应用入口
├── ymcloud-vue/           # Vue 前端，包含 OJ 与后台页面
└── pom.xml                # Maven 聚合工程与依赖管理
```

## 主要功能

- 用户注册、验证码登录、个人资料、角色和菜单权限管理。
- 题目与标签管理、公开和私有题目、成对 `.in` / `.out` 测试数据上传。
- Java / C / C++ / Python 异步评测，自测与 ACM / OI 评分。
- 比赛管理、报名、提交记录和排行榜。
- 公告、系统设置、文件管理和存储配置。
- 测试数据版本管理：上传或删除生成新版本，提交固定所用版本，文件按 SHA-256 校验。

后台使用 Element Plus，OJ 页面使用 Naive UI。后端采用 Spring Security、MyBatis-Plus、Redis、RabbitMQ 和 Docker Java；对象存储使用 AWS SDK for Java v2 或阿里云 OSS SDK。具体依赖版本以 [pom.xml](pom.xml) 和 [package.json](ymcloud-vue/package.json) 为准。

Web 与 Worker 共用 `ymcloud-pojo` 中的 `com.ymcloud.pojo.contract.judge`。消息契约、状态编号和测试版本清单集中在该包；数据库分页、校验等 Web 专用依赖不会通过 `pojo` 传递到 Worker。项目不启用 Swagger 或 Knife4j。

## 运行环境

| 环境 | 要求 |
| --- | --- |
| Java | JDK 17，项目编译目标为 Java 17 |
| Maven | 3.9+ |
| Node.js | 推荐 22，当前前端依赖要求至少 20.5 |
| npm | 9+，使用仓库中的 `package-lock.json` 安装 |
| MySQL | 8.0+，字符集 `utf8mb4` |
| Redis | 6+ |
| RabbitMQ | 3.13+；判题使用普通持久化 Topic，不需要延迟交换机插件 |
| Docker | Docker Engine 或 Docker Desktop，使用 Linux 容器 |

可以使用已经运行的 MySQL、Redis 和 RabbitMQ。开始前确认后端能连接这些服务，并且 RabbitMQ 账户有相应 virtual host 的权限。

## 首次启动

以下命令从项目根目录执行，前端步骤会明确切换到 `ymcloud-vue`。

### 1. 初始化一个空数据库

[sql/ymcloud.sql](sql/ymcloud.sql) 是当前版本的完整初始化文件，包含 23 张业务表及必要数据。使用现有 MySQL 即可，无需另建 MySQL 容器。

首次安装时创建一个空库，再导入 SQL。下面的命令适用于 Linux、Windows PowerShell 和命令提示符；密码在提示时输入。替换连接地址、用户名和库名后，请同步修改后端配置。

```text
mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE IF NOT EXISTS ymcloud DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root -p ymcloud -e "SOURCE sql/ymcloud.sql;"
```

如果 `ymcloud` 已经是业务库，请使用另一个空库名。此文件用于全新安装，不能作为已有版本的升级脚本；重复导入不会覆盖已有记录或修改旧表结构。

初始化数据包括：

| 内容 | 初始值 |
| --- | --- |
| 超级管理员 | 用户 ID 为 `1`，用户名 `admin`，初始密码 `Ymcloud@123` |
| 管理员邮箱 | `admin@example.invalid`，仅为占位值，不能接收邮件 |
| 内置角色 | 管理员、题目管理员、普通用户，共 3 个角色 |
| 菜单与权限 | 67 项，包含文件管理、存储配置与历史文件迁移权限 |
| 判题语言 | Java、C、C++、Python，共 4 种 |
| 注册开关 | 开启，可在后台“常用设置 → 系统配置”关闭 |
| 文件存储 | 本地存储，其他服务需要在后台配置 |

用户 ID 为 `1` 的正常账号具有全部系统权限。初始密码是公开的安装示例，首次登录后立即修改密码和邮箱，再对外开放服务。请在后台创建自己的题目并上传测试数据；初始库不包含历史用户、题目、比赛、提交、文件或存储连接凭据。

### 2. 填写后端连接配置

通用默认值位于 [application.yml](ymcloud-web/src/main/resources/application.yml)，默认启用 `dev` 环境。在同一目录新建 `application-dev.yml`，按自己的服务填写以下内容。`yyyy` 仅为占位值，需要替换；Redis 没有密码时使用空字符串。

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/ymcloud?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
    username: yyyy
    password: yyyy
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      password: yyyy
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: yyyy
    password: yyyy
    virtual-host: /

token:
  secret: ${YMCLOUD_TOKEN_SECRET}
```

`YMCLOUD_TOKEN_SECRET` 是 OJ 登录令牌的签名密钥，不能使用仓库中的公开示例值。首次安装可在准备运行后端的终端生成一个随机值。

Linux / Bash（需要 OpenSSL）：

```bash
export YMCLOUD_TOKEN_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
export YMCLOUD_WORKER_SERVICE_TOKEN="$(openssl rand -hex 32)"
```

Windows / PowerShell 7：

```powershell
$env:YMCLOUD_TOKEN_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
$env:YMCLOUD_WORKER_SERVICE_TOKEN = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

环境变量只对当前终端及其启动的进程生效。部署时将生成值保存在自己的服务环境配置或密钥管理中，后续启动继续使用同一值；从 IDE 启动时也要设置该环境变量。不要将实际密钥提交 Git。

`YMCLOUD_WORKER_SERVICE_TOKEN` 是 Web 与 Worker 之间的独立服务密钥，至少 32 个可打印字符，两个进程必须使用相同值。它只允许读取已发布测试版本，不赋予用户或管理员权限，也不复用存储密钥。未配置时 Web 拒绝数据下载，Worker 拒绝启动。

基础登录与注册不依赖邮件发送。需要忘记密码或邮箱修改功能时，再在私有环境配置中填写 `mail.from`、`mail.pass`；当前邮件实现使用 QQ SMTP 和邮箱授权码。

### 3. 准备判题镜像

确认 Docker 已启动。Linux / Bash 可运行：

```bash
bash docker/build.sh
```

Windows 或希望逐个构建的用户，在项目根目录执行：

```text
docker build -t oj-java-env docker/java
docker build -t oj-cpp-env docker/cpp
docker build -t oj-python-env docker/python
```

这 3 个镜像分别提供 Java 17、GCC 和 Python 3.11 环境，其中 `oj-cpp-env` 同时用于 C 与 C++。Worker 通过 Docker Java 的默认连接配置访问本机 Docker socket 或 Docker Desktop，启动 Worker 的账户需要 Docker 权限；Web 不需要 Docker 权限。

### 4. 构建并分别运行 Web 与 Worker

在已设置签名密钥的终端执行：

```text
mvn clean package -DskipTests
java -jar ymcloud-web/target/ymcloud-web-0.0.1-SNAPSHOT.jar
```

也可以在 IDE 中运行 `com.ymcloud.YmCloudWebApplication`。默认后端监听 `127.0.0.1:9091`。

另一个终端启动 Worker。它不连接 MySQL，也不需要存储服务的地址、桶或访问密钥。使用与 Web 相同的开发 Redis、RabbitMQ virtual host 和服务身份，并设置 Worker 可访问的 Web 地址：

```text
java -jar ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar
```

Worker 与 Web 一样默认启用 `dev`。在 `ymcloud-judge/src/main/resources/application-dev.yml` 中填写与 Web 开发环境一致的 `spring.data.redis`、`spring.rabbitmq` 和 `ymcloud.worker.data` 配置，直接启动即可。不要复制包含业务数据库凭据的 Web 配置到 Worker。也可通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 和 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`、`RABBITMQ_VHOST` 设置基础连接默认值；实际环境配置中的显式值会覆盖这些默认值。`YMCLOUD_WORKER_API_URL` 默认为 `http://127.0.0.1:9091`。

远程 Worker 访问 Web 必须使用 HTTPS；只有本机回环地址允许 HTTP，客户端不跟随重定向。反向代理只向判题节点开放 `/internal/judge-worker/**`；无需向浏览器提供该入口。

双方会声明以下新交换机和队列：

| 用途 | 默认名称 |
| --- | --- |
| 判题交换机 | `judge.worker-v2.topic` |
| 判题任务队列 | `judge.worker-v2.queue` |
| 判题结果队列 | `judge.worker-v2.result.queue` |
| 有限重试 / 无效任务队列 | 任务队列名加 `.retry` / `.dead` |
| 任务 / 结果路由键 | `judge` / `judge.result` |

名称可通过 `ymcloud.judge.exchange`、`task-queue`、`result-queue` 配置，或对应的 `YMCLOUD_JUDGE_EXCHANGE`、`YMCLOUD_JUDGE_TASK_QUEUE`、`YMCLOUD_JUDGE_RESULT_QUEUE` 环境变量修改。

Web 的 `judge_dispatch` 表保存任务快照，任务发布等待 RabbitMQ 确认；失败后有限重试，超过总截止时间记录系统错误。Worker 用 Redis 租约合并重复执行，最终结果先保存到本地日志，再确认发布并应答任务。Web 在事务内校验执行标识、版本和结果序号，重复结果不重复插入测试点或 AC 记录。Redis 状态缓存丢失时，状态查询从数据库恢复。

任务箱的发布时间、恢复时间和截止时间统一使用 UTC；消息使用 epoch 毫秒，跨主机时区不改变任务预算。

正式提交与自测共用任务箱的数据库自增编号，保持浏览器可以精确表示的整数范围。已有数据库升级时从历史最大提交编号之后继续分配，旧提交 URL 保持不变。

### 5. 启动前端并登录后台

另开一个终端执行：

```text
cd ymcloud-vue
npm ci
npm run dev
```

访问 `http://localhost:5173`。后台登录地址为 `http://localhost:5173/admin/login`，使用初始化管理员账号登录并修改密码、邮箱。

Vite 的开发代理将 `/api` 转发到 `http://localhost:9091` 并去掉前缀，将 `/files` 转发到同一后端。创建题目、上传成对测试数据，再用初始化的语言提交代码，即可验证判题链路。

## 文件与对象存储

Web 默认启动自动创建项目根目录下的 `files/public`、`files/private`。将 jar 复制到独立部署目录时，默认文件根目录位于 jar 所在目录。需要固定目录时设置 `YMCLOUD_FILE_BASE_DIR`。

Worker 的工作目录、结果日志及版本缓存默认位于启动目录的 `files/worker/work`、`files/worker/results`、`files/worker/cache`。通过 `YMCLOUD_JUDGE_WORK_DIR`、`YMCLOUD_JUDGE_RESULT_DIR` 和 `YMCLOUD_JUDGE_CACHE_DIR` 固定部署目录。测试数据以文件流读取，保留输入原始字节；下载校验成功后才原子启用。缓存有容量上限，只清理没有评测引用的版本；每个 Worker 使用独立目录，同一缓存目录不能被两个进程共用。结果日志应保存在可持续写入的本地磁盘，重启时保留。运行账户需要相应目录读写权限。

当前阶段仍使用 Docker 沙箱，已经限制网络、CPU、内存、进程数及输出大小。任务工作目录使用绑定挂载，尚未提供单任务磁盘写入配额；对外提供判题服务时，应为判题节点工作盘设置独立容量和文件系统配额，避免用户程序填满业务磁盘。Linux 沙箱使用工作目录的 UID/GID，支持非 root Worker，缓存和结果文件保持私有权限。

公开头像、封面等通过当前网站的 `/files/public/**` 访问。测试数据始终作为私有文件，查询和下载需要题目测试数据权限；普通私有文件还需要独立的私有文件权限。不要将整个 `files/private` 目录配置为静态资源目录。

使用外部存储时，由超级管理员进入“常用设置 → 存储配置”，填写实际服务的地址、桶和访问密钥，保存后“测试连接”，成功再“设为默认”。配置立即生效，无需重启后端。MinIO、SeaweedFS 等待配置行只是示例，保存不会自动启用；新默认配置只影响之后上传的文件，已有文件仍从原配置读取。

公开文件与私有文件使用不同存储桶，两个桶均保持私有，由 OJ 控制访问。远程存储使用 HTTPS，连接地址以 OJ 后端所在机器或容器能够访问的地址为准。

如果没有对象存储，可选用 [SeaweedFS 安装说明](seaweedfs/README.md) 中的 Docker 脚本；已有 SeaweedFS、MinIO 或 OSS 的用户直接添加后台配置即可。

OJ 首次保存存储配置时自动生成 `files/.secrets/storage-config.key`，用于加密数据库中的存储连接凭据。部署备份应同时保留数据库、文件或对象存储数据及该主密钥；恢复时使用对应的主密钥。自定义文件根目录后，主密钥也位于该目录的 `.secrets` 中。

## 开发与生产环境

开发和生产使用独立的数据库、Redis database、RabbitMQ virtual host，以及各自的文件目录、存储桶和主密钥。只区分 `dev` / `prod` 文件名不会隔离共享服务的数据。

开发连接写入 `application-dev.yml`，生产连接写入 `application-prod.yml`。生产启动时显式选择环境：

```text
java -jar ymcloud-web/target/ymcloud-web-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
java -jar ymcloud-judge/target/ymcloud-judge-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

这两个环境文件被 Git 忽略。使用命令行参数、环境变量或外置配置管理实际部署信息；分发 jar 前也检查打包资源，避免把开发或生产的真实凭据随制品发送给他人。

生产前端构建：

```text
cd ymcloud-vue
npm run type-check
npm run build
```

产物位于 `ymcloud-vue/dist`。部署静态页面时配置 SPA 路由回退，并将 `/api`、`/files` 代理到后端；`/api` 代理需去掉前缀，与开发代理保持一致。后端默认仅监听本机，可由同机反向代理提供 HTTPS 访问。

真实凭据、访问密钥、私有配置、数据库导出、用户数据和测试数据不上传 Git。

## 从第一阶段升级

第一阶段旧任务和新版协议不兼容。先暂停新提交，让旧 Web 消费者处理完 `judge.storage-v1.*` 队列并确认结果已落库，再停止旧 Web。不要清空队列或让新旧消费者共用判题队列。

已有第一阶段数据库执行一次 [sql/upgrade-worker.sql](sql/upgrade-worker.sql)，仅增加 `judge.execution_id` 和 `judge_dispatch`，保留历史结果及原测试版本。全新安装使用 `sql/ymcloud.sql`，无需执行升级文件。数据库 DDL 不具备整体事务回滚，升级前按自己的部署流程准备维护与恢复措施。

部署新 Web 和 Worker，使用相同的服务密钥及新版队列，完成一次真实提交和自测后恢复提交。维护窗口内回退时，先排空新版任务，停止新版服务，再恢复旧 Web 和旧队列配置；保留新增字段、任务表和文件数据，不删除历史记录。

多实例扩展时，每个 Worker 设置不同 `YMCLOUD_WORKER_ID`、独立缓存/工作/结果目录，使用同一环境的任务队列、Redis 和 Web 服务入口。初始部署建议单 Worker、并发 1，确认判题节点容量后再增加 `YMCLOUD_WORKER_CONCURRENCY`。

Worker 已记录队列等待时间、执行耗时、缓存命中、下载失败、校验失败和系统错误指标（`ymcloud.judge.*`）。它不启动 HTTP 服务；需要查看指标时，通过自己的私有配置开启 `spring.jmx.enabled=true`、`management.endpoints.jmx.exposure.include=metrics`，使用本机 JMX 工具读取。任务箱中已完成的任务仍保留快照，后续可按部署的数据保留规则归档，勿直接清理未完成任务或运行中的版本缓存。

## 开发验证

修改前端后运行 `npm run type-check` 和 `npm run build`。Java 单元测试可按涉及的模块与测试类筛选执行；默认完整 `mvn test` 包含加载真实应用上下文的测试，运行前确认使用隔离的开发配置。

真实存储数据库测试需要显式启用 `YMCLOUD_STORAGE_DB_IT=true`，真实 S3 测试需要 `YMCLOUD_RUN_S3_TESTS=true`。SeaweedFS 安装、权限、持久性及性能验证入口见 [SeaweedFS 开发验证说明](seaweedfs/README.md#排查与开发验证)。

Worker 的真实 Docker 回归需要显式启用 `YMCLOUD_RUN_SANDBOX_TESTS=true` 并准备上述三个语言镜像。任务发布与结果事务测试 `JudgeDispatchDatabaseIT` 需要 `YMCLOUD_JUDGE_DB_IT=true`，仅接受本机开发 MySQL，在随机临时库中执行并清理，覆盖并发重复消息、事务回滚、超时恢复和提交编号兼容性。

打包后可运行 [独立进程整体验证](ymcloud-web/src/test/scripts/judge/verify_worker.py)。安装测试依赖 `PyYAML`、`pymysql`、`redis`、`pika`，设置 `YMCLOUD_WORKER_E2E=true` 和 `YMCLOUD_WORKER_E2E_REDIS_DATABASE` 后执行：

```text
python -B ymcloud-web/src/test/scripts/judge/verify_worker.py
```

该 Redis database 必须为空、不同于开发配置中的 database，且处于 1–15 范围。脚本读取本机 `application-dev.yml`，使用随机数据库、独立队列和临时目录启动真实 Web 与 Worker，验证版本固定、缓存修复、普通/比赛提交、自测、权限、重复消息及重启恢复。测试连接配置只保存在临时目录，结束后清理；报告位于 `target/phase2-e2e`。不要使用生产连接配置运行测试。

[Linux 权限验证](ymcloud-web/src/test/scripts/judge/verify_linux.py) 使用现有 `oj-java-env` 镜像和打包后的 Worker，在独立非 root 容器内检查私有缓存权限、损坏恢复、链接拒绝及工作目录属主配置。先设置 `YMCLOUD_WORKER_LINUX_TEST=true`，再执行：

```text
python -B ymcloud-web/src/test/scripts/judge/verify_linux.py
```

## 许可

本项目采用 [MIT License](LICENSE)。欢迎通过 Issue 和 Pull Request 交流、改进。

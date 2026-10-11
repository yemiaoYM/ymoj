# ymcloud · 在线判题系统

`ymcloud` 是基于 Spring Boot 3、Vue 3 和 TypeScript 的在线判题系统，提供题库、ACM / OI 比赛、异步评测和后台管理。文件默认保存在本地，也可在后台配置 SeaweedFS、MinIO、阿里云 OSS 或其他 S3 服务。

当前后端将 Web 接口和判题模块一起启动，通过 RabbitMQ 调度任务，使用 Docker 执行 Java、C、C++ 和 Python 程序。

## 项目结构

```text
ymcloud/
├── docker/                # 判题镜像及构建脚本
├── seaweedfs/             # 可选的 SeaweedFS Docker 安装工具
├── sql/ymcloud.sql        # 当前版本的建库结构与初始化数据
├── files/                 # 启动后创建的本地文件目录，不提交 Git
├── ymcloud-common/        # 常量、工具、异常和统一响应
├── ymcloud-pojo/          # 实体、DTO、VO 和消息模型
├── ymcloud-core/          # 鉴权、数据访问、缓存、消息与文件存储
├── ymcloud-judge/         # 编译执行、Docker 沙箱、判题与评分
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

## 运行环境

| 环境 | 要求 |
| --- | --- |
| Java | JDK 17，项目编译目标为 Java 17 |
| Maven | 3.9+ |
| Node.js | 推荐 22，当前前端依赖要求至少 20.5 |
| npm | 9+，使用仓库中的 `package-lock.json` 安装 |
| MySQL | 8.0+，字符集 `utf8mb4` |
| Redis | 6+ |
| RabbitMQ | 需要安装并启用与服务版本匹配的 `rabbitmq_delayed_message_exchange` 插件 |
| Docker | Docker Engine 或 Docker Desktop，使用 Linux 容器 |

可以使用已经运行的 MySQL、Redis 和 RabbitMQ。开始前确认后端能连接这些服务，并且 RabbitMQ 账户有相应 virtual host 的权限。

## 首次启动

以下命令从项目根目录执行，前端步骤会明确切换到 `ymcloud-vue`。

### 1. 初始化一个空数据库

[sql/ymcloud.sql](sql/ymcloud.sql) 是当前版本的完整初始化文件，包含 22 张业务表及必要数据。使用现有 MySQL 即可，无需另建 MySQL 容器。

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
```

Windows / PowerShell 7：

```powershell
$env:YMCLOUD_TOKEN_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

环境变量只对当前终端及其启动的进程生效。部署时将生成值保存在自己的服务环境配置或密钥管理中，后续启动继续使用同一值；从 IDE 启动时也要设置该环境变量。不要将实际密钥提交 Git。

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

这 3 个镜像分别提供 Java 17、GCC 和 Python 3.11 环境，其中 `oj-cpp-env` 同时用于 C 与 C++。后端通过 Docker Java 的默认连接配置访问 Docker；使用本机 Docker socket 或 Docker Desktop，并保证启动后端的账户有权限访问 Docker。

### 4. 构建并运行后端

在已设置签名密钥的终端执行：

```text
mvn clean package -DskipTests
java -jar ymcloud-web/target/ymcloud-web-0.0.1-SNAPSHOT.jar
```

也可以在 IDE 中运行 `com.ymcloud.YmCloudWebApplication`。默认后端监听 `127.0.0.1:9091`。

当前判题消费者随后端一起启动。RabbitMQ 延迟插件必须提前可用，后端会声明以下交换机和队列：

| 用途 | 默认名称 |
| --- | --- |
| 判题交换机 | `judge.storage-v1.topic` |
| 判题任务队列 | `judge.storage-v1.queue` |
| 判题结果队列 | `judge.storage-v1.result.queue` |
| 任务 / 结果路由键 | `judge` / `judge.result` |

名称可通过 `ymcloud.judge.exchange`、`task-queue`、`result-queue` 配置，或对应的 `YMCLOUD_JUDGE_EXCHANGE`、`YMCLOUD_JUDGE_TASK_QUEUE`、`YMCLOUD_JUDGE_RESULT_QUEUE` 环境变量修改。

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

默认启动自动创建项目根目录下的 `files/public`、`files/private`，判题临时目录为 `files/tmp`。将 jar 复制到独立部署目录时，默认文件根目录位于 jar 所在目录。需要固定目录时设置 `YMCLOUD_FILE_BASE_DIR`；判题临时目录可通过 `YMCLOUD_JUDGE_WORK_DIR` 单独指定。运行后端的账户需要有相应目录读写权限。

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

## 开发验证

修改前端后运行 `npm run type-check` 和 `npm run build`。Java 单元测试可按涉及的模块与测试类筛选执行；默认完整 `mvn test` 包含加载真实应用上下文的测试，运行前确认使用隔离的开发配置。

真实存储数据库测试需要显式启用 `YMCLOUD_STORAGE_DB_IT=true`，真实 S3 测试需要 `YMCLOUD_RUN_S3_TESTS=true`。SeaweedFS 安装、权限、持久性及性能验证入口见 [SeaweedFS 开发验证说明](seaweedfs/README.md#排查与开发验证)。

## 许可

本项目采用 [MIT License](LICENSE)。欢迎通过 Issue 和 Pull Request 交流、改进。

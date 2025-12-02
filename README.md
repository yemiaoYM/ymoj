# ymcloud · 在线判题系统

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.5.13-4FC08D?logo=vue.js&logoColor=white" alt="Vue 3">
  <img src="https://img.shields.io/badge/JDK-17-5382a1?logo=openjdk&logoColor=white" alt="JDK 17">
  <img src="https://img.shields.io/badge/Vite-6.2.4-646cff?logo=vite&logoColor=white" alt="Vite">
  <img src="https://img.shields.io/badge/TypeScript-5.8-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
  <img src="https://img.shields.io/badge/MySQL-8.0.43-4479A1?logo=mysql&logoColor=white" alt="MySQL 8.0.43">
  <img src="https://img.shields.io/badge/Docker-20+-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="License">
</p>

`ymcloud` 是一个基于 **Spring Boot 3 + Vue 3 + TypeScript** 的现代化在线判题系统。后端聚合 `ymcloud-common / ymcloud-pojo / ymcloud-core / ymcloud-judge / ymcloud-web` 五个模块，前端位于 `ymcloud-vue`，通过 RabbitMQ 与 Docker 沙箱实现异步判题、隔离运行和可观测的竞赛流程。

---

## 🏗️ 仓库结构

```
ymcloud/
├── docker/                # 判题容器镜像 Dockerfile 与构建脚本
├── files/                 # 默认的文件/测试数据存储目录（可自定义）
├── sql/ymcloud.sql        # MySQL 8.0.43 导出脚本
├── ymcloud-common/        # 通用工具、常量、异常与结果封装
├── ymcloud-pojo/          # Entity/DTO/VO/Query 等数据模型
├── ymcloud-core/          # 安全、配置、拦截器、消息队列等核心能力
├── ymcloud-judge/         # 判题业务、Docker 沙箱、消息监听与策略
├── ymcloud-web/           # Web 接口模块（controller/service/mapper）
├── ymcloud-vue/           # Vue3 + Vite 前端工程
└── pom.xml                # Maven 聚合与依赖管理
```

---

## 🧱 后端模块一览

<details open>
<summary><strong>模块职责速览</strong></summary>

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 通用模块 | `ymcloud-common` | `constant/` 定义缓存、判题、消息队列等常量，`utils/` 提供文件、JSON、Token、Redis 工具，`exception/` 统一异常层。 |
| 数据模型 | `ymcloud-pojo` | `entity/` 包含 `User`、`Problem`、`Contest` 等实体，`dto/`、`vo/`、`query/` 用于管理端与 OJ 端的数据请求与响应。 |
| 核心模块 | `ymcloud-core` | `config/` 下提供 Spring Security、Redis、MyBatis-Plus、RabbitMQ 等配置，`security/` 内实现鉴权、JWT 过滤与权限注解。 |
| 判题模块 | `ymcloud-judge` | `sandbox/` 内的 `SandBox` 抽象类及 Java/C/C++/Python 实现，`docker/` 管理代码执行容器，`listeners/JudgeListener` 消费队列消息，`strategy/` 负责评分策略。 |
| Web 接口 | `ymcloud-web` | `controller/oj` 暴露 OJ 端 API（题目、提交、比赛、用户），`controller/admin` 提供后台管理接口，`service/`、`mapper/` 与 `task/` 组成业务实现。 |

</details>

---

## 🖥️ 前端结构要点

- `src/router` 通过模块化路由拆分 OJ 端、后台端页面，`vite.config.ts` 将 `/api` 与 `/files` 代理到后端 `http://localhost:9091`，方便本地联调。
- `src/views/oj` 覆盖题目、提交、竞赛、用户主页等页面；`src/views/admin` 提供题目、比赛、公告、监控等管理界面。
- `src/components` 下拆分 `admin/common/oj` 三类组件，配合 `Pinia` (`src/stores`) 管理全局状态。

---

## 🚀 核心功能

1. **用户与安全**：基于 Spring Security + JWT 实现登录、角色/权限控制、登录失败锁定时间（`user.password.lockTime`）。
2. **题库管理**：题目增删改查、标签管理、可见性控制与多组测试数据维护，支持在线代码编辑。
3. **竞赛系统**：ACM / OI 赛制、比赛报名、克隆、题面展示、提交记录、排行榜等全部由 `ContestController`/`ContestService` 提供。
4. **判题系统**：
   - 队列：`judge.queue`、`judge.result.queue`、`judge.topic`、`judge`/`judge.result` 路由键集中定义于 `RabbitMQConstants`。
   - 消费：`ymcloud-judge` 监听消息创建 Docker 容器 (`docker/container/CodeExecContainer`)，通过语言工厂 `SandBoxFactory` 创建对应沙箱并执行代码。
   - 语言：Java / C / C++ / Python（见 `sandbox/` 目录）。
   - 资源限制：`SandBox` 统一拼接 `timeout`、`time -f` 命令，检查 `OOMKilled` 状态并抛出 `TimeLimitExceededException`、`MemoryLimitExceededException` 等异常。
5. **状态与可视化**：提交记录分页、代码查看、筛选（用户/题目/语言/状态），排行榜按比赛模式计算罚时或得分。
6. **系统运营**：后台提供公告、文件、用户、比赛等管理接口；`task/` 与定制化监控便于维护。

---

## 🧮 判题链路

```
1. ymcloud-web 接收提交，写入 judge 记录并投递消息到 judge.topic(judge).
2. ymcloud-judge 的 JudgeListener 消费消息，按语言创建 Docker 容器 (docker/ 目录镜像)。
3. SandBox 保存代码 -> (可选) 编译 -> 执行测试输入，解析 time/内存输出。
4. 根据结果/异常更新 judge 表与用户 AC 统计，再推送 judge.result.queue。
5. Web 端轮询/推送得到最新评测状态并展示。
```

---

## 🧊 技术栈

### 后端

| 组件 | 版本来源 | 用途 |
| --- | --- | --- |
| Spring Boot | 3.5.3 (`pom.xml`) | Web 框架与自动配置 |
| Spring Security | 6.5.1 | 身份认证与授权 |
| MyBatis-Plus | 3.5.10.1 | ORM 拓展、分页、枚举支持 |
| MySQL Connector/J | 9.2.0 | 连接 MySQL 8.0.43 数据库（`sql/ymcloud.sql`） |
| Redis Starter | 3.5.3 | 缓存、分布式锁、验证码等 |
| RabbitMQ Starter | Spring 配置 + `RabbitMQConfig` | 判题消息队列 |
| Docker Java | 3.4.0 | 连接 Docker 引擎、创建容器 |
| Hutool | 5.8.38 | 工具集（文件、JSON、集合等） |
| Lombok | 1.18.38 | 精简 Java 代码 |
| Druid | 1.2.23 | 数据源连接池 |
| Fastjson2 | 2.0.57 | JSON 序列化 |
| OSHI | 6.4.1 (`ymcloud-core`) | 系统资源监控，用于后台监控页面 |

### 前端

| 组件 | 版本来源 | 用途 |
| --- | --- | --- |
| Vue | ^3.5.13 | 渲染与组件化 |
| TypeScript | ~5.8.0 | 静态类型 |
| Vite | ^6.2.4 | 构建与本地开发 |
| Pinia | ^3.0.1 | 状态管理 |
| Vue Router | ^4.5.0 | 前端路由 |
| Element Plus | ^2.10.7 | 管理端 UI 组件 |
| Naive UI | ^2.42.0 | OJ 端轻量组件 |
| Axios | ^1.9.0 | HTTP 请求 |
| CodeMirror | ^5.65.19 | 在线代码编辑器 |
| ECharts | ^5.6.0 | 数据可视化与监控图表 |

---

## 🧱 运行环境

- **JDK 17** 与 **Maven 3.9+**（`pom.xml` 统一设置 `java.version=17`）。
- **Node.js ≥ 18** 与 npm / pnpm，用于 `ymcloud-vue`。
- **MySQL 8.0.43**（SQL 脚本导出版本）与 **Redis 6.0+**。
- **RabbitMQ 3.8+**（`application.yml` 中配置连接重试、预取等参数）。
- **Docker 20+**，需允许宿主机访问守护进程以创建 `oj-*-env` 镜像。

---

## ⚙️ 快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS ymcloud DEFAULT CHARACTER SET utf8mb4";
mysql -uroot -p ymcloud < sql/ymcloud.sql
```

> `sql/ymcloud.sql` 由 Navicat 于 2025-11-30 22:50:00 导出，包含公告、题目、比赛、判题、文件等核心表结构与示例数据。

### 2. 配置后端

默认配置位于 `ymcloud-web/src/main/resources/application.yml`，请额外创建 `application-dev.yml`（或 `prod`）以覆盖数据库、缓存、消息队列、文件路径等。

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ymcloud?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_mysql_user
    password: your_mysql_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  redis:
    host: localhost
    port: 6379
    password: # 可留空
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

mail:
  from: no-reply@example.com
  pass: mail_authorization_code

ymcloud:
  file:
    base-dir: /data/ymcloud/files
  docker:
    host-working-dir: /data/ymcloud/files/tmp
    container-working-dir: /app

token:
  header: Authorization
  secret: Ymcloud
  expireTime: 60

user:
  password:
    maxRetryCount: 5
    lockTime: 10
```

- 服务默认监听 `127.0.0.1:9091`（`application.yml`）。
- MyBatis-Plus 已配置日志输出、驼峰映射、逻辑删除、雪花 ID。
- `mail`, `token`, `user.password`、`logging` 等可直接继承默认值。

### 3. 构建与运行后端

```bash
mvn clean package -DskipTests
java -jar ymcloud-web/target/ymcloud-web-0.0.1-SNAPSHOT.jar
# 或使用 IDE 运行 com.ymcloud.YmCloudWebApplication
```

### 4. 准备判题 Docker 镜像

执行 `docker/build.sh`，脚本会检查并按需构建 `oj-python-env`、`oj-cpp-env`、`oj-java-env` 三个镜像，每个镜像都会替换为腾讯云镜像源并安装 `time` 工具。

```bash
cd docker
bash build.sh
```

### 5. 启动前端

```bash
cd ymcloud-vue
npm install
npm run dev   # 默认 http://localhost:5173
```

Vite 已在 `vite.config.ts` 中配置代理：

- `/api` → `http://localhost:9091`（会去掉 `/api` 前缀）
- `/files` → `http://localhost:9091`

打包生产版本：

```bash
npm run build
```

构建产物位于 `ymcloud-vue/dist`，可部署到任意静态服务器，与后端通过 Nginx 或同源代理连接。

---

## 💾 数据与存储

- **文件上传**：默认写入 `ymcloud/files` 目录，可在 `ymcloud.file.base-dir` 中改为绝对路径。
- **判题临时目录**：`ymcloud.docker.host-working-dir`，请确保读写权限并定期清理。
- **日志**：`logging.pattern.dateformat` 已设置为 `MM-dd HH:mm:ss:SSS`，详见 `application.yml`。

---

## 🌐 常用端口

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 API | http://localhost:9091 | RESTful 接口、文件访问 `/files` |
| 前端（开发） | http://localhost:5173 | Vite Dev Server |
| RabbitMQ | amqp://localhost:5672 | `judge.queue`、`judge.result.queue` |
| MySQL | jdbc:mysql://localhost:3306/ymcloud | 主库 |

---

## 📄 许可

本项目采用 [MIT License](LICENSE)。欢迎 Issue / PR 交流，也欢迎 Star 支持。

---

👏 如果本项目对你有帮助，请留下 Star，感谢支持！
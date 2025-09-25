# ymcloud - 现代化在线判题系统

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.5.13-4FC08D?logo=vue.js&logoColor=white" alt="Vue 3">
  <img src="https://img.shields.io/badge/JDK-17-blue?logo=openjdk&logoColor=white" alt="JDK 17">
  <img src="https://img.shields.io/badge/TypeScript-5.8-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
  <img src="https://img.shields.io/badge/MySQL-9.2.0-4479A1?logo=mysql&logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white" alt="Redis">
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?logo=rabbitmq&logoColor=white" alt="RabbitMQ">
  <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="License">
</p>

## 📖 项目简介

`ymcloud` 是一个基于 **Spring Boot 3 + Vue 3 + TypeScript** 构建的现代化在线判题系统（Online Judge）。项目采用前后端分离架构和模块化设计，实现了完整的用户管理、题库管理、竞赛系统、代码提交与判题等核心功能。系统通过引入消息队列、代码沙箱、Docker容器隔离等技术，保证了判题过程的高效性、异步性和安全性。

> **后端仓库**：https://gitee.com/yemiaoo/ymcloud

> **前端仓库**：https://gitee.com/yemiaoo/ymcloud-vue3

## ✨ 项目亮点

### 🏗️ 架构设计
- **前后端分离**：后端提供RESTful API，前端独立部署，职责分明，便于团队协作
- **模块化设计**：后端分为通用、数据模型、核心、判题和Web接口等模块，结构清晰，易于维护和扩展
- **微服务友好**：模块间低耦合，支持后续微服务化改造

### ⚡ 性能与安全
- **异步化判题**：基于 RabbitMQ 消息队列处理判题任务，实现异步化，大幅提升并发处理能力
- **Docker容器隔离**：每个代码提交运行在独立的Docker容器中，确保系统安全性和资源隔离
- **代码沙箱机制**：内置多语言代码沙箱，支持时间、内存、进程数等多维度限制
- **缓存优化**：Redis缓存热点数据，提升系统响应速度

### 🎯 技术特色
- **多语言支持**：支持 Java、C++、C、Python 等主流编程语言
- **多种赛制**：支持 ACM、OI 等不同竞赛模式
- **权限控制**：基于 Spring Security 的细粒度权限管理
- **响应式设计**：前端适配不同设备尺寸，提供一致的用户体验

## 🛠️ 技术栈

### 后端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.3 | 应用框架 |
| Spring Security | 6.5.1 | 安全框架 |
| MyBatis-Plus | 3.5.10.1 | ORM框架 |
| MySQL | 8.0.39 | 主数据库 |
| Redis | 3.5.3 | 缓存数据库 |
| RabbitMQ | 4.1.0 | 消息队列 |
| Docker API | 3.4.0 | 容器管理 |
| JWT | 0.9.1 | 身份认证 |
| Hutool | 5.8.38 | 工具类库 |
| Knife4j | 4.4.0 | API文档 |

### 前端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue.js | 3.5.13 | 前端框架 |
| TypeScript | 5.8.0 | 类型系统 |
| Vite | 6.2.4 | 构建工具 |
| Pinia | 3.0.1 | 状态管理 |
| Vue Router | 4.5.0 | 路由管理 |
| Element Plus | 2.10.7 | UI组件库 |
| Naive UI | 2.42.0 | UI组件库 |
| CodeMirror | 5.65.19 | 代码编辑器 |
| ECharts | 5.6.0 | 数据可视化 |
| Axios | 1.9.0 | HTTP客户端 |

## 📦 项目结构

### 后端模块说明

```
ymcloud/
├── ymcloud-common/          # 通用模块
│   ├── 工具类 (FileUtils, SecurityUtils 等)
│   ├── 全局常量 (CacheConstants, JudgeStatus 等)
│   ├── 自定义异常 (JudgeException, ProblemException 等)
│   └── 通用上下文 (SecurityContext 等)
├── ymcloud-pojo/           # 数据模型模块
│   ├── entity/            # 实体类 (User, Problem, Contest 等)
│   ├── dto/               # 数据传输对象
│   ├── vo/                # 视图对象
│   └── query/             # 查询对象
├── ymcloud-core/           # 核心模块
│   ├── 安全认证 (Spring Security 配置)
│   ├── 通用拦截器
│   ├── 自定义注解
│   └── 数据库配置
├── ymcloud-judge/          # 判题模块 ⭐
│   ├── sandbox/           # 代码沙箱
│   │   ├── SandBox.java          # 沙箱抽象类
│   │   ├── JavaSandBox.java      # Java沙箱实现
│   │   ├── CppSandBox.java       # C++沙箱实现
│   │   ├── CSandBox.java         # C沙箱实现
│   │   └── PythonSandBox.java    # Python沙箱实现
│   ├── docker/            # Docker容器管理
│   │   ├── container/            # 容器操作
│   │   └── factory/              # 容器工厂
│   ├── strategy/          # 判题策略
│   └── listener/          # 消息监听器
└── ymcloud-web/            # Web接口模块
    ├── controller/        # 控制器层
    ├── service/           # 业务服务层
    ├── mapper/            # 数据访问层
    └── config/            # 配置类
```

### 前端目录结构

```
ymcloud-vue/
├── src/
│   ├── api/               # API请求封装
│   ├── assets/            # 静态资源
│   ├── components/        # 公共组件
│   │   ├── admin/                # 管理端组件
│   │   ├── common/               # 通用组件
│   │   └── oj/                   # OJ端组件
│   ├── constants/         # 常量定义
│   ├── router/            # 路由配置
│   │   ├── modules/              # 路由模块
│   │   └── index.ts              # 路由入口
│   ├── stores/            # 状态管理 (Pinia)
│   ├── utils/             # 工具函数
│   ├── views/             # 页面组件
│   │   ├── admin/               # 管理端页面
│   │   │   ├── contest/         # 比赛管理
│   │   │   ├── problem/         # 题目管理
│   │   │   ├── user/            # 用户管理
│   │   │   └── monitor/         # 系统监控
│   │   ├── oj/                  # OJ端页面
│   │   │   ├── contest/         # 比赛页面
│   │   │   ├── problem/         # 题目页面
│   │   │   ├── status/          # 状态页面
│   │   │   ├── userHome/        # 用户主页
│   │   │   ├── home/            # 首页
│   │   │   ├── login/           # 登录注册
│   │   │   └── about/           # 关于页面
│   │   └── error/               # 错误页面
│   └── type/              # TypeScript类型定义
└── dist/                  # 构建输出目录
```

## 🚀 核心功能

### 👥 用户系统
- **用户注册/登录**：支持邮箱验证、密码加密存储
- **权限管理**：基于角色的访问控制 (RBAC)
- **用户主页**：个人信息展示、提交统计热力图、解题记录展示
- **账号安全**：登录失败锁定、JWT Token 认证
- **个人设置**：个人信息修改、密码修改

### 📚 题库系统
- **题目管理**：支持题目的增删改查、标签分类
- **多种难度**：入门、普及、提高、省选及以上
- **权限控制**：公开题目、私有题目、比赛专用题目
- **测试用例**：支持多组测试数据，自动判题
- **题目浏览**：题目列表展示、搜索筛选、题目详情查看
- **在线编程**：集成 CodeMirror 代码编辑器，支持语法高亮

### 🏆 竞赛系统
- **多种赛制**：
  - **ACM 赛制**：通过即得分，有罚时机制
  - **OI 赛制**：按最后提交计分，支持部分分
- **比赛管理**：创建比赛、题目配置、权限设置
- **比赛功能**：
  - 比赛列表展示
  - 比赛详情页面
  - 比赛题目列表
  - 实时排名榜
  - 比赛提交记录
- **比赛权限**：公开比赛、私有比赛（密码保护）
- **比赛克隆**：支持克隆已有比赛

### ⚖️ 判题系统
- **多语言支持**：
  - **Java** (OpenJDK 17)
  - **C++** (GCC 编译器)
  - **C** (GCC 编译器)  
  - **Python** (Python 3.x)
- **安全沙箱**：
  - Docker 容器隔离
  - 内存限制 (可配置)
  - 时间限制 (可配置)
  - 进程数限制 (防 fork 炸弹)
  - 网络隔离 (禁用网络访问)
  - 文件系统保护 (只读根文件系统)
- **异步判题**：基于 RabbitMQ 的消息队列，支持高并发
- **判题策略**：支持不同编程语言的编译和运行策略
- **判题结果**：
  - **Accepted (AC)**：答案正确
  - **Wrong Answer (WA)**：答案错误
  - **Time Limit Exceeded (TLE)**：超时
  - **Memory Limit Exceeded (MLE)**：内存超限
  - **Runtime Error (RE)**：运行时错误
  - **Compile Error (CE)**：编译错误
  - **System Error (SE)**：系统错误
  - **Partial Accepted (PA)**：部分正确（OI模式）

### 📊 状态系统
- **提交记录**：查看所有提交的详细信息
- **代码查看**：支持语法高亮的代码展示
- **筛选功能**：按用户、题目、语言、状态筛选
- **提交详情**：查看具体的判题结果和错误信息

### 🔧 管理系统
- **管理仪表盘**：系统概览、快速操作入口
- **用户管理**：用户信息管理、权限分配
- **题目管理**：题目编辑、测试用例管理
- **比赛管理**：
  - 比赛创建和编辑
  - 比赛题目配置
  - 比赛题目管理（添加、移除、编辑显示信息）
- **系统监控**：服务器状态监控、在线用户统计

### 🏠 门户系统
- **首页展示**：系统公告、最新比赛、用户排行榜
- **关于页面**：系统介绍、更新日志
- **404页面**：友好的错误页面

## 🏗️ 系统架构

### 判题流程架构
```
用户提交代码
    ↓
Web层接收请求
    ↓
创建判题记录
    ↓
发送到RabbitMQ队列
    ↓
判题服务监听消息
    ↓
创建Docker容器
    ↓
代码沙箱执行
    ↓
收集执行结果
    ↓
更新判题状态
    ↓
返回判题结果
```

### 代码沙箱安全机制
- **容器隔离**：每个提交运行在独立的 Docker 容器中
- **资源限制**：严格限制 CPU、内存、磁盘使用
- **网络隔离**：禁用网络访问，防止恶意网络操作
- **文件系统保护**：只读根文件系统，防止恶意文件操作
- **进程限制**：限制进程数量，防止 fork 炸弹攻击
- **时间控制**：超时自动终止，防止无限循环

## 🚀 快速启动

### 环境要求
- **JDK**: 17+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **RabbitMQ**: 3.8+
- **Docker**: 20.0+

### 后端启动

1. **克隆项目**
   ```bash
   git clone https://gitee.com/yemiaoo/ymcloud.git
   cd ymcloud
   ```

2. **配置数据库**
   ```bash
   # 创建数据库
   mysql -u root -p
   CREATE DATABASE ymcloud DEFAULT CHARACTER SET utf8mb4;
   
   # 导入表结构
   mysql -u root -p ymcloud < sql/ymcloud.sql
   ```

3. **配置环境**
   
   创建 `ymcloud-web/src/main/resources/application-dev.yml` 文件：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/ymcloud
       username: your_username
       password: your_password
       driver-class-name: com.mysql.cj.jdbc.Driver
     redis:
       host: localhost
       port: 6379
       password: # 如果有密码请填写
     rabbitmq:
       host: localhost
       port: 5672
       username: guest
       password: guest
   
   # 邮箱配置（用于注册验证）
   mail:
     from: your_email@example.com
     pass: your_email_password
   
   # 文件存储路径配置
   ymcloud:
     file:
       base-dir: /path/to/your/files
     docker:
       host-working-dir: /path/to/your/files/tmp
       container-working-dir: /app
   ```

4. **启动服务**
   ```bash
   # Maven 构建
   mvn clean package -DskipTests
   
   # 启动应用
   java -jar ymcloud-web/target/ymcloud-web-0.0.1-SNAPSHOT.jar
   ```

### 前端启动

1. **克隆前端项目**
   ```bash
   git clone https://gitee.com/yemiaoo/ymcloud-vue3.git
   cd ymcloud-vue3
   ```

2. **安装依赖**
   ```bash
   npm install
   # 或使用 yarn/pnpm
   ```

3. **启动开发服务器**
   ```bash
   npm run dev
   ```
   
   访问 http://localhost:5173

4. **构建生产版本**
   ```bash
   npm run build
   ```

### 访问地址

- **前端页面**: http://localhost:5173
- **后端API**: http://localhost:9091

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源协议。

## 🙏 致谢

感谢所有为本项目做出贡献的开发者和开源社区的支持！

## 📞 联系方式

- **作者**: Yemiao
- **Gitee**: https://gitee.com/yemiaoo
- **问题反馈**: [Issues](https://gitee.com/yemiaoo/ymcloud/issues)

---

⭐ 如果这个项目对你有帮助，请给一个 Star 支持一下！
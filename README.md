<div align="center">

<img src="ymcloud-vue/src/assets/image/logo.jpg" alt="ymcloud Logo" width="144">

# ymcloud

**从一道题的练习，到一场比赛的组织。**

面向算法学习与竞赛的开源在线评测平台

![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3-6DB33F?logo=springboot&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue-3-42B883?logo=vuedotjs&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Java 17](https://img.shields.io/badge/Java-17-E76F00)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![MIT License](https://img.shields.io/badge/License-MIT-0F766E)](LICENSE)

[部署指南](https://gitee.com/yemiaoo/ymoj-deploy) · [功能概览](#功能概览) · [技术架构](#技术架构) · [开发文档](https://gitee.com/yemiaoo/ymoj-deploy#源码开发与维护) · [反馈问题](https://gitee.com/yemiaoo/ymcloud/issues)

</div>

---

ymcloud 将题库、代码提交、在线判题、比赛排名和后台管理整合在同一个平台中。学习者可以练习算法、运行自测和参与比赛；维护者可以管理题目、测试数据、比赛与用户权限。

项目采用 **Spring Boot 3 + Vue 3 + TypeScript**，通过独立 Worker 与 go-judge 沙箱完成异步评测，支持 Docker Compose 部署。

## 功能概览

| | 能做什么 |
| --- | --- |
| 📚 **题库与练习** | 题目分类与标签、难度分级、公开与私有题目、代码自测、提交记录 |
| ⚡ **多语言评测** | 支持 C、C++、Java、Python，提供 ACM / OI 评分及测试点结果 |
| 🏆 **比赛组织** | 比赛创建与报名、公开与私有比赛、比赛题目管理、排行榜 |
| 🛠️ **后台管理** | 用户、角色、菜单权限、公告、系统设置、服务监控与在线用户管理 |
| 📦 **文件与存储** | 默认使用本地存储，可配置 SeaweedFS、MinIO、阿里云 OSS 或其他 S3 兼容服务 |
| 🐳 **部署与运维** | 独立部署仓库、固定版本镜像、启动配置检查、服务健康检查与持久化日志 |

## 为学习者，也为维护者

### 练习、提交、查看结果

四种语言共用一套评测流程。正式提交与代码自测均通过异步任务处理，支持查看提交状态、执行耗时、内存占用和测试点结果。题目可以按 ACM 模式判定通过，也可以按 OI 模式评分。

### 让比赛和题库各有秩序

题库与比赛分别管理，支持题目可见性、比赛报名和排行榜。后台提供普通用户、管理员、题目管理员等内置角色，并以明确的权限编码控制管理操作。

### 让评测结果有据可查

测试数据按版本保存，提交时固定所用版本，下载后校验 SHA-256。任务持久化、有限重试与结果幂等处理共同支撑判题流程，Worker 与 Web 分离运行。

### 从本地存储开始，按需要扩展

首次部署即可使用本地文件存储，无需额外安装对象存储。后续可在后台添加存储配置；已有文件保留原存储位置，新上传文件使用当前默认配置。

## 技术架构

```text
                     Vue 3 + TypeScript
                       OJ 页面 / 管理后台
                               │
                               ▼
                       Spring Boot Web
                    业务接口 · 鉴权 · 任务持久化
                       │       │       │
                       ▼       ▼       ▼
                     MySQL   Redis  RabbitMQ
                                       │
                                       ▼
                                 Judge Worker
                              测试数据准备 · 结果汇总
                                       │
                                       ▼
                               go-judge 执行沙箱
                              C / C++ / Java / Python
```

| 层次 | 技术选型 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router |
| 界面 | OJ 使用 Naive UI，管理后台使用 Element Plus |
| 后端 | Java 17、Spring Boot 3、Spring Security、MyBatis-Plus |
| 数据与消息 | MySQL、Redis、RabbitMQ |
| 评测 | 独立 Java Worker、go-judge、Linux 沙箱 |
| 部署 | Docker Compose、腾讯云 / Docker Hub 公开镜像 |

## 搭建你自己的 OJ

部署文件发布在 **[ymoj-deploy](https://gitee.com/yemiaoo/ymoj-deploy)**，包含 Compose 配置、公开环境变量模板和数据库初始化文件。

👉 **[打开部署指南：从环境准备到首次提交](https://gitee.com/yemiaoo/ymoj-deploy)**

仅部署网站时，无需编译源码或安装 Java、Maven、Node。生产部署基线为 Linux amd64、cgroup v2、2 核 4GB，默认单 Worker、并发 1；更高并发需结合题目资源限制调整容量。

## 参与开发

```text
ymcloud-common   通用工具、常量与异常
ymcloud-pojo     实体、DTO、VO 与判题消息契约
ymcloud-core     鉴权、数据访问、缓存、消息与文件存储
ymcloud-web      OJ 与后台业务接口
ymcloud-judge    独立判题 Worker
ymcloud-vue      OJ 前端与管理后台
sql             当前版本建表与必要初始化数据
deploy          镜像发布所用的源配置模板
```

阅读[源码开发与维护](https://gitee.com/yemiaoo/ymoj-deploy#源码开发与维护)，了解本地运行、环境配置和回归验证。完整网站部署见[部署指南](https://gitee.com/yemiaoo/ymoj-deploy)，独立判题节点见 [go-judge 部署文档](docker/go-judge/README.md)。

欢迎通过 [Issue](https://gitee.com/yemiaoo/ymcloud/issues) 反馈问题、提出建议，或通过 [Pull Request](https://gitee.com/yemiaoo/ymcloud/pulls) 参与改进。提交问题时请附上版本、复现步骤与脱敏日志。

## 开源许可

本项目采用 [MIT License](LICENSE)。感谢 [go-judge](https://github.com/criyle/go-judge) 及项目依赖的开源工具与社区。

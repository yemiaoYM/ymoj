# ymcloud - 在线判题系统

## 📖 项目简介

`ymcloud` 是一个基于 Spring Boot 构建的现代化在线判题系统（Online Judge）。项目采用模块化设计，实现了用户管理、题库、竞赛、代码提交与判题等核心功能。系统通过引入消息队列和代码沙箱等技术，保证了判题过程的高效性、异步性和安全性。

## ✨ 项目亮点

*   **模块化设计**：项目结构清晰，分为通用、数据模型、核心、判题和Web接口等模块，便于团队协作、维护和二次开发。
*   **异步化判题**：通过 RabbitMQ 消息队列处理判题任务，实现了判题流程的异步化，大幅提升了系统的并发处理能力和用户体验。
*   **安全性保障**：内置代码沙箱机制，有效隔离了用户提交的代码，防止恶意代码对系统造成破坏。
*   **技术栈先进**：采用 Spring Boot 3、Spring Security、MyBatis-Plus 等主流技术，保证了项目的稳定性和先进性。
*   **完善的API文档**：集成 Knife4j，自动生成美观、易用的 API 文档，方便前后端分离开发和接口调试。

## 🛠️ 技术栈

<p style="text-align: center;">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/JDK-17-blue" alt="JDK 17">
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="License">
  <img src="https://img.shields.io/badge/Gitee-yemiaoo-red" alt="Gitee">
  <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?logo=springsecurity&logoColor=white" alt="Spring Security">
  <img src="https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white" alt="Redis">
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?logo=rabbitmq&logoColor=white" alt="RabbitMQ">
  <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker">
</p>

## 📦 模块说明

### `ymcloud-common`
通用模块，提供整个项目所需的工具类、全局常量、自定义异常、通用上下文等。

### `ymcloud-pojo`
数据模型模块，定义了项目中所有的实体类（Entity）、数据传输对象（DTO）、视图对象（VO）等。

### `ymcloud-core`
核心模块，包含了项目的核心配置，如安全认证、全局异常处理、通用拦截器和自定义注解等。

### `ymcloud-judge`
判题模块，是项目的核心功能所在。它负责处理判题逻辑，包括：
*   **代码沙箱**：提供一个安全隔离的环境来编译和运行用户代码。
*   **判题策略**：根据不同的编程语言和题目要求，执行相应的判题策略。
*   **消息监听**：通过监听 RabbitMQ 中的消息来触发判题任务。

### `ymcloud-web`
Web接口模块，是项目的前端交互入口。它通过 Controller 暴露 API 接口，并处理业务逻辑，包括：
*   用户注册与登录
*   题目和比赛管理
*   代码提交与状态查询
*   定时任务

## 🚀 快速启动

1.  **克隆项目**
    ```bash
    git clone https://gitee.com/yemiaoo/ymcloud.git
    cd ymcloud
    ```

2. **准备环境**
    - 安装并配置 MySQL 数据库
    - 启动 Redis、RabbitMQ、Docker 环境

3. **配置数据库与 Redis**
   修改 `application.yml`，填入数据库、Redis、RabbitMQ 等配置信息。

4. **初始化数据库**
   导入 SQL 文件创建所需表结构，或使用项目中提供的初始化脚本。

5. **启动后端服务**
# ymcloud - 在线判题系统（前端项目）

<p align="center">
  <img src="https://img.shields.io/badge/Vue.js-3.x-4FC08D?logo=vue.js&logoColor=white" alt="Vue 3">
  <img src="https://img.shields.io/badge/Vite-6.x-646CFF?logo=vite&logoColor=white" alt="Vite">
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
  <img src="https://img.shields.io/badge/Pinia-FFD83A?logo=pinia&logoColor=black" alt="Pinia">
  <img src="https://img.shields.io/badge/Element%20Plus-409EFF?logo=element-plus&logoColor=white" alt="Element Plus">
  <img src="https://img.shields.io/badge/Naive%20UI-18A058?logo=naiveui&logoColor=white" alt="Naive UI">
  <img src="https://img.shields.io/badge/Axios-5A29E4?logo=axios&logoColor=white" alt="Axios">
</p>

## 📖 项目简介

一个基于 **Vue 3 + Vite + TypeScript** 构建的现代化在线评测系统（Online Judge）前端。项目实现了题库、在线编程、比赛、评测状态、用户主页等核心功能，旨在提供一个功能完善、体验流畅的 OJ 平台。

> 后端：https://gitee.com/yemiaoo/ymcloud

> 前端：https://gitee.com/yemiaoo/ymcloud-vue3

## ✨ 项目亮点

*   **现代化技术栈**：采用 Vue 3 Composition API (`<script setup>`)、Vite、Pinia 等前沿技术，开发体验优秀，性能卓越。
*   **响应式 UI 设计**：结合 Element Plus 和 Naive UI 组件库，适配不同设备尺寸，提供一致的用户体验。
*   **集中式状态管理**：通过 Pinia 管理用户、模态框等全局状态，逻辑清晰，易于维护。
*   **完善的 OJ 功能**：支持题目浏览、代码提交、实时评测、比赛参与、排名查看等在线评测系统的核心功能。
*   **强大的在线编辑器**：集成 CodeMirror，支持代码高亮、主题切换和多种编程语言。
*   **精细化权限控制**：基于 Vue Router 的导航守卫实现路由级权限校验，确保系统安全。

## 📦 模块说明

* 首页 (Home): 项目的门户，展示核心功能入口和最新动态.
* 题目模块 (Problem): 包括题目列表和题目详情页。用户可以浏览题目、搜索、在线编写代码并提交.
* 比赛模块 (Contest): 支持 OI/ACM 等多种赛制，包括比赛列表、比赛详情、题目列表、实时排名和提交记录.
* 状态模块 (Status): 展示全站或特定比赛的提交记录，并可查看判题详情.
* 用户模块 (User): 包括用户个人主页、设置中心等，支持个人信息修改和提交统计可视化.
* 管理模块 (Admin): (部分功能) 如题目创建与编辑，需要特定权限才能访问.

## 🚀 快速启动

- **环境要求**：Node.js >= 18, npm/yarn/pnpm

1.  **克隆项目**
    ```bash
    git clone https://gitee.com/yemiaoo/ymcloud-vue3.git
    cd vue-project
    ```

2.  **安装依赖**
    ```bash
    npm install
    ```

3.  **启动开发环境**
    ```bash
    npm run dev
    ```

4.  **构建生产包**
    ```bash
    npm run build
    ```

## 📦 目录结构

```
src/
  ├── api/          # API 请求封装 (Axios)
  ├── assets/       # 静态资源 (图片等等)
  ├── utils/       # 通用工具函数
  ├── constants/    # 常量
  ├── components/   # 全局公共组件
  ├── router/       # 路由配置与导航守卫
  ├── stores/       # 状态管理 (Pinia)
  ├── views/        # 页面视图组件
  ├── App.vue       # 根组件
  └── main.ts       # 应用入口文件
```
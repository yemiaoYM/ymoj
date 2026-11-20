# SeaweedFS

本目录为没有对象存储服务的用户提供可选 Docker 安装工具。OJ 默认使用本地 `files/public`、`files/private`；已有 SeaweedFS、Silo 或 OSS 的用户直接在 OJ 后台添加连接，无需运行安装脚本。Silo 使用现有 S3 驱动，官方项目为 [pgsty/silo](https://github.com/pgsty/silo)。历史 MinIO 配置仍可读取和维护，已有文件的存储标识保持不变；服务端迁移按 Silo 官方迁移说明另行执行。

目录只保留这份说明、`compose.yml`、Linux 入口 `manage.sh` 和 Windows 入口 `install.ps1`。`.env`、`secrets/` 是首次运行生成的私有文件，不提交 Git。

## 安装

需要已启动的 Docker 和 Docker Compose v2。在项目根目录执行：

Ubuntu / Linux 使用 Bash、OpenSSL、flock，无需 PowerShell：

```bash
sudo bash seaweedfs/manage.sh start
```

Windows 使用 PowerShell 7 和 Docker Desktop 的 Linux 容器模式：

```powershell
pwsh -NoProfile -File seaweedfs/install.ps1 -Action start
```

脚本固定使用 SeaweedFS 4.48，生成随机访问密钥并创建 `ymcloud-public`、`ymcloud-private` 两个桶。重复执行保留原密钥、密码和数据。初次安装指定其他 S3 端口，可分别添加 `--s3-port 18444` 或 `-S3Port 18444`；已有 `.env` 的端口不会被重复安装参数覆盖。

Docker Compose 分别管理 master、volume、filer、S3；对象和元数据保存在命名数据卷中。只有 S3 发布 `127.0.0.1:18333`，内部管理端口不对外发布。Linux 挂载凭据文件为组 1000、权限 640，容器以 `1000:1000` 运行；脚本会修复旧安装的 600 权限并检查容器是否可读。Windows 文件仅运行安装的用户和 SYSTEM 可访问。

## 在 OJ 后台配置

用超级管理员进入“常用设置 → 存储配置”，新增 SeaweedFS：

| 字段 | 默认安装的填写值 |
| --- | --- |
| 配置标识 | `seaweedfs`；已有文件的配置必须使用原标识 |
| 连接地址 | `127.0.0.1:18333` |
| 使用 HTTPS | 本地默认安装关闭 |
| 地域 | `us-east-1` |
| 存储桶 | `ymcloud-private` |
| 桶策略 | 私有 |
| Access Key | `.env` 中的 `YMCLOUD_S3_ACCESS_KEY` |
| Secret Key | `.env` 中的 `YMCLOUD_S3_SECRET_KEY` |

填写应用账户并保存，点击“设为默认”后，新上传的文件使用该配置。已有文件继续按登记时的配置读取。每条配置对应一个桶，文件的展示权限由 OJ 的业务类型决定。

连接地址以 OJ 后端所在机器或容器为准。后端运行在 Docker 内时，填写从后端容器可以访问的主机名与端口。

## 可选管理界面

先完成安装，再执行：

```bash
sudo bash seaweedfs/manage.sh admin
```

```powershell
pwsh -NoProfile -File seaweedfs/install.ps1 -Action admin
```

访问 `http://127.0.0.1:23646`，用户名 `admin`，密码在 `secrets/admin.options` 的 `adminPassword` 中。重复执行保留原密码。服务器可用 SSH 隧道访问，示例：

```bash
ssh -L 23646:127.0.0.1:23646 用户名@服务器地址
```

随后在自己电脑浏览器打开 `http://127.0.0.1:23646`。不要发送 `.env`、密码文件或展开后的 Compose 配置。

## 查看、停止与重新开始

Linux：

```bash
sudo bash seaweedfs/manage.sh status
sudo bash seaweedfs/manage.sh stop
# 再次启动，保留数据
sudo bash seaweedfs/manage.sh start
```

Windows 使用 `install.ps1 -Action status`、`-Action stop`、`-Action start`。

**从零重装会永久删除这套 SeaweedFS 的容器、网络、全部数据卷及访问密钥和管理员密码。** 先在 OJ 后台将本地存储设为默认，再明确执行：

```bash
sudo bash seaweedfs/manage.sh reset --delete-data
sudo bash seaweedfs/manage.sh start
```

```powershell
pwsh -NoProfile -File seaweedfs/install.ps1 -Action reset -DeleteData
pwsh -NoProfile -File seaweedfs/install.ps1 -Action start
```

重置只作用于当前 Compose 项目；模板保留，便于再次安装。镜像可复用，不必删除。重装生成新凭据，需要在 OJ 后台重新填写。备份对象数据、OJ 数据库及存储账户凭据后再重置。

## 故障排查

启动失败先查看服务状态及 S3 日志：

```bash
docker compose -f seaweedfs/compose.yml --profile admin-ui ps --all
docker compose -f seaweedfs/compose.yml logs --tail 80 s3
```

Linux 报 `s3.json: permission denied` 时，用更新后的脚本重新执行 `start`，保留原凭据及数据。rootless Docker 或 UID 映射环境需要按实际映射调整文件所属组；读取检查失败时脚本会停止。

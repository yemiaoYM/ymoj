# SeaweedFS

本目录为没有对象存储服务的用户提供可选 Docker 安装工具。OJ 默认使用本地 `files/public`、`files/private`；已有 SeaweedFS、MinIO 或 OSS 的用户直接在 OJ 后台添加连接，无需运行安装脚本。

目录只保留这份说明、`compose.yml`、Linux 入口 `manage.sh` 和 Windows 入口 `install.ps1`。`.env`、`secrets/` 是首次运行生成的私有文件，不提交 Git。开发测试工具位于 `ymcloud-web/src/test/scripts/storage/`。

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
| 连接地址 | `http://127.0.0.1:18333` |
| 地域 | `us-east-1` |
| 公开文件桶 | `ymcloud-public` |
| 私有文件桶 | `ymcloud-private` |
| 路径式访问 | 开启 |
| Access Key | `.env` 中的 `YMCLOUD_S3_ACCESS_KEY` |
| Secret Key | `.env` 中的 `YMCLOUD_S3_SECRET_KEY` |

填写应用账户，不要填写带 `ADMIN` 的建桶账户。保存后先“测试连接”，成功再“设为默认”；配置立即生效，无需重启后端。已有服务填写自己的地址、桶和账户即可。安装脚本不修改 OJ 配置、数据库、判题队列或加密主密钥。

地址以 **OJ 后端所在机器或容器** 为准：后端直接运行在同一服务器时可用上面的回环地址；后端运行在 Docker 中时，`127.0.0.1` 指向后端容器自身，需要自行接入受控网络并填写可达地址。远程连接使用 HTTPS；OJ 仅允许回环地址使用 HTTP，不要直接把私有存储端口开放到公网。

两个桶均要求签名鉴权。公开头像、封面由当前网站提供访问；私有文件仍由 OJ 的业务权限检查，公开桶名称不代表允许匿名下载。

测试确认 SeaweedFS 4.48 对只签名 `x-amz-content-sha256` 的 PUT 请求存在正文校验缺口。OJ 上传同时使用签名的 `Content-MD5`，保存前回读核对 SHA-256；远程连接仍须使用 HTTPS。这项上游限制保留在安全测试报告中。

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

重置只作用于当前 Compose 项目；模板保留，便于再次安装。镜像可复用，不必删除。重装生成新凭据，需要在 OJ 后台重新填写。不要直接删除整个 `seaweedfs/`：旧版本可能曾把 OJ 加密主密钥放在这里。

OJ 加密主密钥属于 OJ，自动保存在文件根目录 `.secrets/storage-config.key`，必须与数据库一起备份；旧的 `seaweedfs/secrets/storage-config.key` 会校验后自动迁入 OJ 目录，原文件保留。重置脚本保留该旧密钥，不修改数据库文件记录或测试数据版本。

如果旧主密钥已丢失：优先恢复备份；只有全部旧动态配置都没有文件引用时，才能在后台切回本地、删除这些配置，再新增配置自动生成新主密钥。已有文件引用的配置继续受保护；新密钥无法解密旧凭据，重新安装也无法找回已删除对象。

## 排查与开发验证

启动失败先查看服务状态及 S3 日志：

```bash
docker compose -f seaweedfs/compose.yml --profile admin-ui ps --all
docker compose -f seaweedfs/compose.yml logs --tail 80 s3
```

Linux 报 `s3.json: permission denied` 时，用更新后的脚本重新执行 `start`，保留原凭据及数据。rootless Docker 或 UID 映射环境需要按实际映射调整文件所属组；读取检查失败时脚本会停止。

正常安装不运行测试。开发者可运行后端测试目录中的初始化、安全、性能、持久性工具；它们只应对隔离项目或明确授权的开发服务执行。测试报告位于忽略的 `target/test-output/`，私有测试凭据不保留在 Maven 构建目录。Java 存储权限及密钥恢复回归由 `StorageConfigSecurityTest`、`StorageMasterKeyStoreTest`、`StorageConfigDatabaseIT` 等验证；真实数据库测试使用独立临时表，不清理业务库。

Windows 开发环境的完整隔离部署、S3 安全及性能、50 MiB AWS SDK 和卸载重装验证：

```powershell
pwsh -NoProfile -File ymcloud-web/src/test/scripts/storage/verify_docker.ps1 -RunSdk
```

该回归另外需要 Python 3 和 Maven；Linux 的对应入口为测试目录中的 `verify_linux.sh --docker`，需要 Python 3 和已构建的 OJ Python 测试镜像。安装脚本本身不依赖这些测试工具。

官方资料：[S3 API](https://github.com/seaweedfs/seaweedfs/wiki/Amazon-S3-API)、[Docker 镜像](https://hub.docker.com/r/chrislusf/seaweedfs)。

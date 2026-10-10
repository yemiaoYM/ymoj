# SeaweedFS 使用说明

SeaweedFS 是 OJ 的可选文件存储服务。所有安装、配置和验证脚本都在本目录；OJ 默认使用本地 `files/public`、`files/private`，不安装 SeaweedFS 也可以启动。

## 1. 安装并启动

准备 Docker（已启动）、Docker Compose v2 和 PowerShell 7。在项目根目录执行：

```powershell
pwsh -NoProfile -File ./seaweedfs/initialize.ps1 -Start
```

脚本会生成随机访问密钥，启动服务，并创建公开文件桶 `ymcloud-public` 和私有文件桶 `ymcloud-private`。再次执行会复用密钥和存储桶，保留已有数据，已运行的容器不会重新创建。

默认 S3 地址：`http://127.0.0.1:18333`。需要其他端口时，首次安装添加 `-S3Port 18444`；已有配置不会被这个参数覆盖。参数示例见 `.env.example`。

## 2. 让 OJ 使用 SeaweedFS

先初始化 OJ 的配置加密密钥：

```powershell
pwsh -NoProfile -File ./seaweedfs/activate.ps1
```

生成的 `application-storage.local.yml` 默认由 OJ 加载，之后重启后端。如果从 `ymcloud-web` 子目录或其他目录启动，请把环境变量 `YMCLOUD_STORAGE_CONFIG` 设置为该文件的绝对路径。已有部署继续使用原 AES 主密钥，不要删除或重新生成。

使用超级管理员进入“常用设置 → 存储配置”，选择 SeaweedFS，填写：

| 配置项 | 填写内容 |
| --- | --- |
| 配置标识 | `seaweedfs`，已有文件继续使用原标识 |
| 连接地址 | `http://127.0.0.1:18333` |
| 公开文件桶 / 私有文件桶 | `ymcloud-public` / `ymcloud-private` |
| 地域 | `us-east-1` |
| Access Key / Secret Key | 本目录 `.env` 中的 `YMCLOUD_S3_ACCESS_KEY` / `YMCLOUD_S3_SECRET_KEY` |

保存后先“测试连接”，成功后再“设为默认”。安装脚本不会自动切换 OJ 的存储，也不修改数据库连接。公开文件通过当前网站访问；私有文件仍由 OJ 检查权限。

## 3. 打开管理页面

```powershell
pwsh -NoProfile -File ./seaweedfs/enable-admin.ps1
```

访问 `http://127.0.0.1:23646`，用户名 `admin`，密码在本目录 `secrets/admin.options` 的 `adminPassword` 中。管理界面和 S3 默认只开放给本机；远程部署需要另行配置网络和 HTTPS。

## 4. 查看和停止服务

```sh
docker compose -f seaweedfs/compose.yml --profile admin-ui ps
docker compose -f seaweedfs/compose.yml logs --tail 50 master volume filer s3
docker compose -f seaweedfs/compose.yml --profile admin-ui stop
```

恢复已配置的服务：

```sh
docker compose -f seaweedfs/compose.yml --profile admin-ui up -d --no-recreate --wait
```

未开启管理界面时，去掉 `--profile admin-ui`。数据保存在 Docker 持久卷中，不要使用 `down -v`；修改镜像或挂载后，需要在维护窗口内用普通 `up -d --wait` 更新容器。

## 文件和验证脚本

- `compose.yml`、`.env.example`：部署模板和参数示例。
- `initialize.ps1`、`bootstrap.ps1`、`enable-admin.ps1`：初始化、建桶、开启管理界面。
- `activate.ps1`：初始化 OJ 配置加密主密钥，也可用于 MinIO 等其他服务。
- `load-env.ps1`、`verify_*`、`benchmark.ps1`：开发验证工具，正常安装无需运行。
- [storage-details.md](storage-details.md)：访问权限、迁移和开发验证的详细说明。

可选检查：

```powershell
pwsh -NoProfile -File ./seaweedfs/verify_initialization.ps1
pwsh -NoProfile -File ./seaweedfs/verify_deployment.ps1
python ./seaweedfs/verify_storage.py --security --iterations 0
```

初始化检查只操作临时夹具；S3 检查会创建并清理自己的测试对象，仅对授权的开发服务执行。性能采样使用 `benchmark.ps1`，报告统一写到项目 `target/test-output/`。

Git 只保存模板、脚本和说明。`.env`、`application-storage.local.yml`、`secrets/` 和测试报告已忽略；密钥与数据应单独备份。当前容器和后端已使用根目录 `seaweedfs/`，旧 `docker/storage/` 已清理；旧配置和历史记录保留在被忽略的 `secrets/legacy-backup-*` 中，正常运行不依赖备份。

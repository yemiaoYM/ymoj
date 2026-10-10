# 文件存储与测试数据版本

第一阶段包含：可配置对象存储、文件元数据、不可变测试数据版本和历史数据迁移入口。判题仍运行在现有进程中，后续独立 Worker 与 go-judge 接入另行实施。

## 驱动配置与访问边界

新安装的配置文件只有 `local`，`ymcloud.storage.active` 固定为 `local`，首次启动即可使用本地文件存储。其他服务由超级管理员在后台新增、测试后手动设为默认；后台启用后以数据库 `storage_config_state.active_key` 为准，多个实例读取相同选择。每个文件记录自己的 `storage_profile`、Bucket、Object Key、大小和 SHA-256。切换默认配置后仍按记录中的旧配置读取，因此已有配置名及其 endpoint、桶、凭据必须与历史对象匹配，不能把 `seaweedfs` 的 endpoint 改成另一套空存储冒充迁移。

| 服务商 | 驱动 | 配置入口 |
| --- | --- | --- |
| 本地存储 `local` | 本地文件 | 启动配置 `ymcloud.file.base-dir`；后台只读显示 |
| SeaweedFS | AWS SDK for Java v2 / S3 | 后台新增，填写地址、公开/私有桶、Access Key、Secret Key |
| MinIO | AWS SDK for Java v2 / S3 | 后台新增，填写对应服务地址、双桶和凭据 |
| 阿里云 OSS | 阿里云原生 OSS SDK | 后台新增，填写 OSS 地址、双桶和凭据 |
| 其他 S3 | AWS SDK for Java v2 / S3 | 后台新增，填写对应服务地址、双桶和凭据 |

启动配置不预置远程服务或远程凭据。后台可以新增不同标识的同类驱动配置；保存后处于备用状态，成功测试当前配置版本后才能手动设为默认。SeaweedFS/MinIO 默认使用 path-style；其他 S3 可配置虚拟主机式访问。阿里云走原生 OSS SDK，不假设其兼容所有 AWS S3 行为。远程 endpoint 必须使用 HTTPS，HTTP 只允许回环地址开发环境。

公开与私有文件使用不同桶。两个桶在 SeaweedFS 上都要求有效签名，不给匿名身份或公开桶策略。公开文件由 Web `/files/public/{profile}/{key}` 检查数据库公开标志后流式返回，旧公开 URL 通过兼容入口继续访问。测试数据必须私有，只允许具备相应管理权限的业务接口或内部判题服务读取，不生成长期公网直链。

应用账户仅有指定两个桶的 Read/Write/List 权限，无建桶、删桶和全局 Admin 权限。单独的 bootstrap 账户只供建桶及部署测试使用，不能填写为后台配置的应用凭据。对象存储凭据通过后台保存为加密数据，数据库连接凭据沿用受控运行配置；密钥不写入源码、日志、测试报告或提交记录。

## 本地目录规则

新安装默认使用 `ymcloud.file.base-dir=${YMCLOUD_FILE_BASE_DIR:./files}`。源码运行时，相对目录按项目根目录解析，从 `ymcloud-web` 等子目录启动仍指向同一份 `files`；独立 jar 部署时，相对目录按 jar 所在目录解析。本地存储初始化时创建公开与私有目录 `public`、`private`，无需手动创建。显式绝对路径保持原位置。

判题工作目录默认为文件根目录下的 `tmp`，配置为 `ymcloud.docker.host-working-dir=${YMCLOUD_JUDGE_WORK_DIR:${ymcloud.file.base-dir}/tmp}`；`YMCLOUD_JUDGE_WORK_DIR` 可单独覆盖。dev/prod 继承主配置的目录规则，不再预置开发机或服务器的绝对目录。Web 文件访问、测试数据服务、判题工作目录与迁移 CLI 共用相同目录解析规则。

已有部署继续读取原本地文件时，显式指定真实旧目录，例如：

```powershell
$env:YMCLOUD_FILE_BASE_DIR = '/srv/ymcloud/files'
# 仅当判题工作目录需要与文件根目录分离时设置：
$env:YMCLOUD_JUDGE_WORK_DIR = '/srv/ymcloud/files/tmp'
```

目录配置变化本身不会自动移动、复制或删除历史文件，也不会重写历史数据库记录或改变后台已选择的远程配置。已有部署可以继续用绝对路径覆盖读取原目录；若明确将原文件复制到新默认根目录，应逐个核对源与目标的 SHA-256，并保留原目录，已有目标哈希不一致时拒绝覆盖。这是显式运维操作，不是目录初始化的自动迁移；历史源文件缺失时不猜测或替换文件。

## 项目后台文件管理与存储配置

菜单位于“常用设置 → 文件管理 / 存储配置”，两个子菜单均不设置图标，沿用项目已有管理页面样式。文件页支持名称、访问范围、业务类型、存储配置筛选及分页；可以上传公开或私有通用文件、按权限下载、复制当前网站的公开网址、删除无业务关联的通用文件。头像、封面、业务附件和测试数据保留在相应业务页面维护，不能在通用入口直接删除；测试数据的版本清单和已有评测引用继续保留。

文件列表先在 SQL 中过滤访问权限，再计算总数和分页，不向无权用户返回私有元数据。公开 URL 无需登录；测试数据即使被错误标记为公开，也不会由公开入口提供。后台下载使用附件、`nosniff`、`no-store` 响应。已存在且可访问的旧头像 URL 同样支持后台下载，本机缺失的历史源文件不猜测或替换。

| 能力 | 权限 |
| --- | --- |
| 通用文件列表 | `file:file:list` |
| 通用文件下载 | `file:file:download`，私有文件还需 `file:file:private` |
| 通用文件上传 | `file:file:upload`，私有文件还需 `file:file:private` |
| 无关联通用文件删除 | `file:file:remove`，私有文件还需 `file:file:private` |
| 测试数据列表和下载 | `problem:testcase:query`，通用私有权限不能替代 |
| 存储配置查看 | `file:storage:list` |
| 存储配置新增、编辑、删除、测试、启用 | 对应 `file:storage:*` 具体操作权限，且用户 ID 必须为 1 |

超级管理员固定为可用的 ID 1 账号，自动拥有全部具体权限；普通角色的通配符不生效。增量脚本给 ADMIN 分配通用文件权限和存储查看权限，给已有测试数据查询权限的角色补充文件管理菜单入口，不额外授予通用私有文件或存储维护权限。角色授权仍可在现有角色管理页调整。

配置页第一行固定显示中文名称“本地存储”，保留本地存储供回退。列表不显示来源、引用和访问密钥列，历史引用保护仍由后端执行。新安装的后端只有真实 `local` 配置；界面另显示 MinIO、SeaweedFS、阿里云 OSS 和其他 S3 的静态待配置示例，方便选择服务商。示例中的 `xxxx` 仅是界面展示或表单占位符，不是真实 profile 或凭据，不写数据库，也不能测试、设为默认或删除。填完整信息并保存后才成为真实备用配置；已有真实配置优先显示，数据库默认选择继续保留。

新增表单提供上述远程服务商；基本设置填写名称、标识、服务商、连接地址、两个独立桶、访问标识（Access Key）和访问密钥（Secret Key），更多设置填写地域、路径式访问和备注。弹窗使用固定尺寸及 Element Plus `el-scrollbar`，底部操作区固定，长表单在内容区滚动。

先创建桶并授权应用账户，再在后台保存为备用配置，执行测试连接，成功后手动设为默认。测试执行双桶随机对象的上传、下载、哈希检查及删除，失败不会切换默认配置。更新备用配置使旧测试结果失效；更新当前默认配置必须先自动测试成功。已有文件引用的配置不能修改服务商、地址、地域、桶和路径式访问，仍可轮换凭据。当前使用或已被引用的配置不可删除，配置文件中的入口不能从后台删除。

如果历史文件引用的配置已经缺失，只能以原来完全相同的标识恢复，且该标识必须同时不存在于数据库和配置文件。保存前对全部引用逐个执行只读校验：公开或私有桶必须对应原记录，对象大小和完整 SHA-256 必须匹配。任何引用缺失、大小或哈希不一致、读取或关闭失败都会拒绝保存，不上传、覆盖或删除历史对象。校验成功后只保存为备用配置，仍需测试并手动设为默认；这项恢复入口不解除已有配置的编辑位置保护和删除引用保护。

后台配置保存于 `storage_config`，Access Key 和 Secret Key 使用 AES-256-GCM，配置标识与字段名绑定认证密文，每次随机 nonce；接口不返回明文或密文，编辑时留空保留原值。主密钥为 Base64 编码的 32 字节随机值，配置属性 `ymcloud.storage.config-encryption-key`，基础配置支持环境变量 `YMCLOUD_STORAGE_CONFIG_ENCRYPTION_KEY`，也可使用下面的初始化脚本生成受限本地配置。缺少主密钥时本地存储仍可工作，但不能写入动态配置；已有动态配置不能以无密钥方式读取。

`seaweedfs/activate.ps1` 是通用的本地存储与 AES 主密钥初始化工具，无需安装 SeaweedFS，也不读取其 `.env`。脚本创建并保护 secrets 目录，首次生成并重复使用 Git 忽略的 `seaweedfs/secrets/storage-config.key`，限制为当前用户及 SYSTEM，随后写入受限本地配置。**必须独立备份这把主密钥，数据库备份无法替代；不要重新生成来覆盖已加密数据。** 脚本发现旧本地配置已有主密钥而独立密钥文件缺失时会中止，要求恢复。凭据请求解析失败只记录异常类型，避免原始 JSON 密钥出现在解析错误日志中。

MyBatis 默认使用 `org.apache.ibatis.logging.nologging.NoLoggingImpl`，避免 SQL 参数日志输出配置密文及其他敏感参数。不要为了排查连接问题启用包含原始凭据、请求正文或 SQL 参数的日志。

已有业务库执行增量 `sql/migrations/20261010_file_management.sql`（先执行对象存储迁移），新增两张配置表、必要索引和菜单。显式测试工具 `FileManagementMigrationTool apply` 在执行前保存菜单/角色授权快照并校验重跑幂等性，不重建 MySQL，不重置旧对象或历史评测；SQL 和文档仍须逐文件审批后才允许提交。

## 目录与安装入口

所有 SeaweedFS 部署模板、配置示例、脚本和说明统一位于根目录 `seaweedfs/`。OJ 的配置加密初始化工具及存储验证脚本也在这里，正常安装不运行测试。真实配置 `.env`、`application-storage.local.yml` 和 `secrets/` 已忽略；本机报告和临时测试夹具写入同样被忽略的 `target/test-output/`。

正常拉取项目得到部署模板和脚本，不会带上本机密钥、对象数据、报告或 Docker 持久卷。默认本地存储无需安装 SeaweedFS，也无需执行密钥初始化。需要后台保存远程配置时：

```powershell
pwsh -NoProfile -File ./seaweedfs/activate.ps1
```

应用配置生成到 `seaweedfs/application-storage.local.yml`，主配置通过 `optional:file:${YMCLOUD_STORAGE_CONFIG:./seaweedfs/application-storage.local.yml}` 导入。模块或独立部署启动时可用 `YMCLOUD_STORAGE_CONFIG` 指定绝对路径。详见 [使用说明](README.md)。

仅在选择自建 SeaweedFS 时执行：

```powershell
pwsh -NoProfile -File ./seaweedfs/initialize.ps1 -Start
```

安装需要 Docker Compose v2 和 PowerShell 7，不调用 Python、Node.js 或测试脚本。Compose 定义固定版本的四个服务、持久卷、网络和资源限制；脚本生成受限账户并调用官方 `weed shell` 初始化两个桶。重复执行复用已有凭据和桶，已运行容器不强制重建。部署说明、标准 Docker 命令和可选 Admin UI 入口见 [使用说明](README.md)。

默认 S3 地址 `http://127.0.0.1:18333`，双桶 `ymcloud-public`、`ymcloud-private`。安装不会启用 Web 的远程存储；超级管理员在后台填写应用账户，保存、测试连接，再手动设为默认。当前已有数据库选择仍优先。Admin UI 默认关闭，开发环境可显式启用，地址 `http://127.0.0.1:23646`；用户名 `admin`，独立密码保存在受限的 `seaweedfs/secrets/admin.options`。

`activate.ps1` 生成版本化判题队列配置及 `openTelemetry=${YMCLOUD_JDBC_TELEMETRY:DISABLED}` 连接属性。部署同一判题链路的生产者与消费者必须使用相同的 `YMCLOUD_JUDGE_EXCHANGE`、`YMCLOUD_JUDGE_TASK_QUEUE`、`YMCLOUD_JUDGE_RESULT_QUEUE`；该入口不修改原在线消费者。JDBC 追踪开关不改变数据库地址、账户或 TLS，也不能证明请求延迟已解决。

## 数据库与历史迁移

使用已有 MySQL，先独立备份数据库和本地文件。执行增量 `sql/migrations/20261010_object_storage.sql` 后再执行 `20261010_file_management.sql`；二者不应替换为包含 DROP TABLE 的旧全量 schema。SQL、配置、文档提交前须取得对应具体文件审批，迁移目录保持忽略。

显式工具 `com.ymcloud.web.StorageMigrationTool` 支持 `plan`、`apply`、`verify`：盘点与备份、执行增量迁移、只读验证分别独立运行。它使用 Spring 运行配置与统一本地目录规则，不启动 Web、MQ 或定时任务；报告保存到 `target/migration-backup/`。存在异常时保存报告并非零退出，不把缺失、未配对或哈希不一致的数据自动发布。已有提交保留原结果，无法还原的版本维持未知。

应先完成相同版本的编译和打包，保留测试类，并用打包产物的库运行 CLI。把数据库凭据留在受控配置内，不写命令行。历史文件迁移前盘点文件记录及实际目录，迁移后逐个核对大小与 SHA-256；保留源文件、旧 URL 和仍被评测引用的版本。切换目录前显式复制与校验，默认目录初始化不会自动搬迁。

头像等历史绝对路径可能属于另一部署节点，不能仅改数据库路径冒充迁移。公开兼容只服务真实存在的原文件；缺失文件保持异常，不通过相似文件替代。HTTP 验证工具 `verify_application.py` 的 `--allow-legacy-exceptions` 是显式保留例外选项，不能排除测试数据或已迁移对象的校验错误。

维护回退需停止新提交、排空任务，恢复匹配的数据库、运行配置和文件备份。不能删除仍被历史文件引用的 profile 或把它改指空桶；旧数据的垃圾回收另行实施。主密钥、数据库和对象存储分别备份，对象写入与数据库事务无法共享单一原子事务。

## 开发验证

验证工具统一位于 `seaweedfs/`，基本命令见 [使用说明](README.md)。测试脚本不参与安装，默认不会执行性能、安全攻击、持久性重启或后台自动启用操作。运行明确选择的 SDK 与数据库回归：

```powershell
. ./seaweedfs/load-env.ps1
$env:YMCLOUD_RUN_S3_TESTS = 'true'
$env:YMCLOUD_STORAGE_DB_IT = 'true'
mvn -pl ymcloud-web -am test '-Dtest=*Test,StorageConfigDatabaseIT,TestDataVersionDatabaseIT,!WebTest,!DockerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

原 `WebTest`、`DockerTest` 涉及真实邮件、共享 Redis 写入或不完整判题消息，不属于上述可控回归。真实数据库测试使用唯一测试表并清理；S3 测试仅操作自身随机对象。浏览器恢复配置必须另外显式开启 `YMCLOUD_STORAGE_ADMIN_SETUP`，普通回归保持关闭。

`verify_initialization.ps1` 只使用临时目录和模拟 Docker 验证首次初始化、重复执行和缺失密钥保护。`verify_deployment.ps1` 对运行容器做只读检查。`verify_storage.py` 使用 Python 3.10+ 标准库，`benchmark.ps1` 另需 Docker 采样；安全和性能检查会创建唯一前缀和专用测试桶，结束后只清理自己的对象。持久性检查 `--persistence-write` / `--persistence-check` 需要手动安排服务重启，普通验证不重启服务。

`verify_application.py` 是依据迁移报告执行的只读 HTTP 验证工具。真实后台与浏览器回归使用 Java `FileManagementRuntimeIT`，需要已部署开发后端、前端、数据库、Redis，可用的 ID 1 管理员、无管理权限的普通账号，以及至少一个题目测试数据文件。浏览器脚本使用 Node.js、Playwright 和已安装的 Edge；显式设置 `YMCLOUD_FILE_ADMIN_HTTP_IT=true`、`YMCLOUD_FILE_ADMIN_BROWSER_IT=true`，并提供 `YMCLOUD_BROWSER_NODE`、`YMCLOUD_PLAYWRIGHT_MODULE`，运行 `mvn -pl ymcloud-web -am test '-Dtest=FileManagementRuntimeIT' '-Dsurefire.failIfNoSpecifiedTests=false'`。普通回归保持 `YMCLOUD_STORAGE_ADMIN_SETUP=false`，不切换存储、不改变用户授权，只创建并清理自己的文件、配置和短期会话。`configure_seaweed.cjs` 会启用配置，仅用于另行授权的管理员恢复场景。

SDK、HTTP、S3 独立性能指标分别记录，不能用本机短测表示生产容量。MinIO 和阿里云真实兼容性需要各自服务及凭据，不能用 SeaweedFS 的别名代替。旧 Docker 沙箱的实际语言测试与资源限制仍由对应测试验证；独立 Worker 和 go-judge 不属于当前阶段。

已知开发环境记录包括原缺失头像返回业务 500，以及约 19 秒的间歇请求延迟，均未在目录整理中修复。历史安装与性能记录已完整保存在本机忽略的 `target/test-output/storage-before-directory-layout.md`；公开项目文档不包含本机 PID、迁移异常 ID 或一次性报告路径，实际结论以独立验证报告为准。

当前五个开发存储容器已使用根目录 `seaweedfs/compose.yml`，S3 和 Admin UI 的凭据只读挂载来自 `seaweedfs/secrets/`；镜像和全部数据卷保持原身份。本机后端已重新启动并加载 `seaweedfs/application-storage.local.yml`，原密码、S3 凭据和 AES 主密钥保持不变。切换前后逐个校验已有对象的大小和 SHA-256，并核对文件、版本和存储配置数据库记录。旧 `docker/storage/` 已清理，原配置和历史记录完整保留在被忽略、受访问权限保护的 `seaweedfs/secrets/legacy-backup-*` 中；正常运行不依赖备份。

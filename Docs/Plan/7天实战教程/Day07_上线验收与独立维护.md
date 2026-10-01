# Day 07：上线验收与独立维护

今天的目标是你能独立维护 QuePhoto Java 后端，并拿出可核对的验收记录。服务器运行着进程只是其中一项；真实内容维护、备份恢复、应用回滚、接口契约和学习成果都要分别验证。

预计投入 4 小时：复盘 20 分钟、三个真实作品及接口验收 70 分钟、缺陷修复 40 分钟、回滚演练 35 分钟、学习考核 25 分钟、运维交接 20 分钟、缓冲 30 分钟。它是挑战目标，遇到权限、恢复或迁移风险时按实际情况顺延，不压缩验证来赶日期。

本教程只安排实施任务，没有代表你执行上线。项目代码根继续为 `D:\Project\QuePhoto_java`；服务器部署根为 `/opt/quephoto`。原小程序继续使用原数据路径，本轮不切换、不提审，也不会自动同步新后端的数据。

## 1. 用证据决定今天先做什么（20 分钟）

打开 Day 05、Day 06 记录，逐项填表。不要先写“基本完成”，再倒推证据。

| 要确认的问题 | 应有的证据 | 缺失时的动作 |
|---|---|---|
| 哪个版本在服务器上？ | 应用镜像 ID、JAR SHA256、版本标签 | 先记录当前版，暂停回滚演练 |
| 当前 Schema 是哪个版本？ | `flyway_schema_history` 查询 | 先查迁移，再谈兼容 |
| 管理链路安全吗？ | HTTPS 或 SSH 隧道、未认证返回 401 | 修复后才能继续真实写入 |
| 数据恢复过吗？ | 独立库、数量/关联/API 校验、耗时 | 今天优先完成 Day 06 恢复 |
| OSS 图片是真的吗？ | 本人确认的真实图片、URL 能访问 | 先更换 demo 路径再验收 |
| 有上一个可用版本吗？ | 可重新加载的 baseline 镜像及配置 | 没有就先建立 baseline |

服务器 Linux Bash，只读检查：

```bash
cd /opt/quephoto
sudo docker compose ps
sudo docker compose images
sudo docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client quephoto --execute='SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
sudo docker compose logs --since=15m --tail=100 app
```

日志只记录必要诊断，不能贴出凭据、完整请求头或管理员登录请求体。证据中的令牌统一删除，而不是只遮住末尾几位。

先说出一次请求经过的路径：HTTP 客户端 → 隧道/HTTPS → Nginx → Security → Controller → Service → Mapper → MySQL。再说出返回路径和异常在哪里变成 400/401/404/409。说不完整，打开对应类确认后再继续。

## 2. 准备三个真实作品的运营清单（10 分钟）

用自己愿意长期保留的摄影作品，避免为了测试向旧 OSS 图片目录写入或覆盖文件。每个作品至少有一张 `work` 图片，其中一张作为封面；有现场图时才添加 `scene`，不是为了覆盖枚举而虚构内容。

在私有记录中写下以下信息，真实 OSS Key 不属于公开 API 响应，但本人运维记录可以受控保存：

| 项目 | 作品 A | 作品 B | 作品 C |
|---|---|---|---|
| 标题 | 本人填写 | 本人填写 | 本人填写 |
| 拍摄时间与精度 | 例如 month | 例如 day | 未知时都为 null |
| 地点 | 可空 | 可空 | 可空 |
| 图片 Key 清单 | 真实、已确认 | 真实、已确认 | 真实、已确认 |
| 合法 work 封面 | 至少一张 | 至少一张 | 至少一张 |
| 标签选择 | 每组至多一个 | 同左 | 无标签也可 |
| 服务器作品 ID | 创建后填写 | 创建后填写 | 创建后填写 |

同一 ObjectKey 只能登记在一个作品内，不要为了凑三个作品复用同一 Key。拍摄时间是 `LocalDateTime` 表达的墙上时间，未知精度不能伪造为真实的日/月；审计时间 `createdAt/updatedAt` 才表示实际时刻。

## 3. 独立完成一次内容闭环（35 分钟）

先不看 AI 生成的答案，只使用 Day 05 的 `docs\openapi.yaml` 与请求集合。准备 20 分钟自己操作，实在卡住再看本节提示；验证时间另留 15 分钟。

Windows PowerShell 中保留 Day 06 的 SSH 隧道。HTTP 客户端的 base URL 为 `http://127.0.0.1:18080`；只有 HTTPS 分支已验收时才换为真实 HTTPS 域名。管理员密码、令牌继续放私有请求环境，不进入 Git。

按顺序操作每个作品：

1. `POST /api/auth/login`：得到有效期有限的 `accessToken`；记录状态码，不保存令牌到公开记录。
2. `POST /api/admin/portfolios`：创建 `draft`，记录返回的服务器 ID；不要先在本地建好后照搬 ID。
3. `GET /api/admin/portfolios/{id}`：确认标题、精度、可空字段与提交一致。
4. `GET /api/portfolios/{id}`：应为 404，证明草稿隔离。
5. 用真实服务器 ID 生成离线清单；按照 Day 06 的受控路径上传并显式运行 importer。
6. 再次读取管理详情，取得真实图片 ID；封面设置用该作品的 `work` 图片 ID。
7. 如需要，`PUT /api/admin/portfolios/{id}/tags` 设置合法标签；仅选择已存在的标签。
8. `PUT /api/admin/portfolios/{id}/images/{imageId}` 调整顺序、尺寸等可编辑字段；不能提交 objectKey 变更。
9. 再 GET 管理详情，构造完整元数据更新 DTO，带齐 title、description、location、shotAt、shotTimePrecision、status，将 status 改为 `published`。
10. 公开列表及详情应能查到它；打开 `coverImageUrl`、`images[].imageUrl`，确认是真实照片。
11. 修改一个描述，再读取确认；保持图片/标签未发生意外变化。
12. 完整 PUT 将 status 改为 `draft`；公开详情应为 404，管理详情仍存在。
13. 再发布，验证重新公开，且没有新增重复图片。

为什么必须先 GET 再构造更新请求：本项目 PUT 是完整替换可编辑字段，不是局部 PATCH。不要把管理响应整个原样回传，响应里的 ID、图片数组、审计时间不是元数据 PUT 字段；按请求 schema 选取字段。

导入时在服务器 Linux Bash 执行：

```bash
cd /opt/quephoto
sudo docker compose run --rm --no-deps importer
```

每次先核对 `/opt/quephoto/imports/import-images.json` 内容已替换为当前作品，且文件仍为 `root:10001 440`。服务器明明返回了另一个 ID，就以服务器实际值为准，不能为了对上教程手改数据库。

预期证据：至少三个真实作品可通过公开 API 查询，其中一个作品完成过“发布→修改→下架→重新发布”；使用正常 API 和离线导入完成全部业务维护，没有手写 INSERT/UPDATE 绕过 Service。

如果标签树为空：检查 Day 05 的 `V2__initial_tag_catalog.sql` 是否已确认并由 Flyway 执行；使用 API 返回的实际 ID。标签写 API 属于延期范围，不能临时暴露不受保护的管理接口。真实作品可暂不带标签，但初始目录及标签规则仍须验证，不能把本地样例作品一起灌入生产。

## 4. 完成 P0 验收矩阵（25 分钟）

将下面表格复制到计划目标 `D:\Project\QuePhoto_java\docs\acceptance-day07.md`。每一行写“通过/失败/未做 + 实际证据”，不能只勾一个总框。

自动化测试使用本地隔离 MySQL 8.4；生产服务器执行只读检查与本人可接受的内容维护。破坏性、并发及失败导入场景优先在隔离环境重放，不能拿生产数据冒险。

| 编号 | 测什么 | 怎么做 | 通过标准 |
|---|---|---|---|
| A01 | 空库迁移与重复启动 | 隔离空库启动，再启动一次 | 5 业务表与 Flyway 表正确，不重复建表 |
| A02 | 未认证访问 | 不带令牌请求所有 admin 路径 | 401，无数据变化 |
| A03 | 无效认证 | 错密码、过期/错误签名令牌、触发限流 | 拒绝访问，401/429 与契约一致 |
| A04 | 草稿隔离 | 列表、筛选、详情查询草稿 | 列表无草稿，详情 404 |
| A05 | 发布/封面规则 | 无封面发布、scene 封面、跨作品封面 | 均拒绝，原状态保持 |
| A06 | 标签约束 | 同组双选、重复 ID、不存在 ID、空数组 | 前三者拒绝，空数组清空 |
| A07 | 导入原子性/幂等 | 隔离库第二张 Key 失败、同清单重放 | 失败零写入，重放不重复 |
| A08 | 字段边界 | 非法分页、日期精度组合、尺寸组合、枚举 | 400 与字段错误符合契约 |
| A09 | 分页和排序 | 固定数据翻页、未知日期、多类型图片 | totalCount 正确，不重漏，work 在 scene 前 |
| A10 | 运营闭环 | 上一节逐步重放 | 不手改 DB 即可完成维护 |
| A11 | 契约一致 | 核对 12 个 method + path | 请求、响应、错误码、鉴权齐全 |
| A12 | 三套真实内容 | 检查真实公开详情及照片 URL | 至少三个真实作品、真实图片可读 |
| A13 | 持久化 | 重启前后比较作品与关联 | 数据一致、无演示种子 |
| A14 | 网络和凭据 | 端口、Git、日志、管理链路检查 | 隧道/HTTPS；3306/8080 不公开；秘密不泄露 |
| A15 | 恢复与回滚 | 独立库恢复及本日应用回滚 | 证据可查，数据保持，兼容成立 |
| A16 | 独立学习 | 本日闭卷修改和解释 | 能自己完成修改、验证并说明原因 |

服务器不必再跑一整套耗时单元测试；部署的是本地已经验证过的同一发布包。新改代码或发现不一致时，重跑受影响测试并重新打包；不要只在服务器改 JAR 或配置后忘记同步源码。

验收时继续检查三条容易误判的规则：

- 公开 DTO 不含 `objectKey`；但公开图片 URL 可能推断 Key，这不提供图片保密能力。
- 元数据下架使公开 API 返回 404，不会撤回已经知道的公开图片 URL。
- P0 没有上传 policy、任意 Key 登记、标签写入和删除接口；未实现路由不能假装 200 成功。

## 5. 修复缺陷时，完成一次真正的学习（40 分钟）

从失败记录中选一个**最小、可验证、不会改 Schema**的问题，例如某输入校验缺失、某错误响应与文档不一致。若没有实际缺陷，就进行后文的本地学习练习，不为展示能力改生产业务。

操作顺序：

1. 写出一个能稳定复现的问题请求，记录期望和实际结果。
2. 沿 Controller → DTO/Service → Mapper 路径定位；不要全仓库到处改。
3. 先写出你的解释：“哪个条件漏了，所以什么输入经过了哪个分支”。
4. 只改需要改的类与相关测试，不同时升级 Spring Boot、改数据库和换镜像。
5. 运行受影响测试，再执行 `mvnw verify`；失败就留在本地修复。
6. 保存新的版本标识，构建候选镜像；继续下一节的候选发布与回滚。

Windows PowerShell，项目根：

```powershell
Set-Location 'D:\Project\QuePhoto_java'
git status --short
.\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { throw '验证失败，停止发布' }
```

提交前检查暂存范围，只纳入本次源码、测试与无秘密文档。不要把真实请求环境、备份、`.env`、AccessKey、私钥、JWT 或下载的照片提交。共享工作区存在他人修改时，只提交自己确认的变更。

超过 20 分钟还无法定位：记录最小复现、受影响范围和第一条根因，再找帮助；超过 40 分钟仍不能可靠修复，则保留 baseline 运行，将该项标为失败。未经验证的候选版本不替代可用版本。

## 6. 先有 baseline，才能演练“回到上一版”（35 分钟）

### 6.1 确定演练边界

首次发布只有一个版本时，没有天然存在的“上一版”。Day 06 应已建立 `quephoto:day06-baseline`；先验证它能登录、查询真实作品，再把它作为已知可用版保存。

本次演练只覆盖**应用镜像回滚**，不修改数据库结构、不恢复覆盖生产库、不删除数据卷、不变更 OSS。若候选版包含不兼容 Schema 迁移，就不在生产演练：移到隔离库验证，设计向前兼容变更后再发布。

提前告知自己/实际使用者一个几分钟维护窗口，暂停新的内容写入，完成最新备份。当前只有本人管理，可以记录“已停止自己的写操作”，无需创建复杂维护系统。

服务器 Linux Bash：

```bash
cd /opt/quephoto
sudo /opt/quephoto/bin/backup.sh
sudo docker image inspect quephoto:day06-baseline --format '{{.Id}}'
sudo cp --preserve=mode /opt/quephoto/.env /opt/quephoto/releases/day06-baseline.env
```

确认 baseline 镜像包、镜像 ID、当时配置和 Flyway 版本都有记录。秘密文件单独受保护，备份中只记录它们的位置；不要在普通发布记录里复制秘密内容。

### 6.2 制作候选版

如上一节修复了真实缺陷，将本地通过验证的 JAR 作为候选版。没有业务改动时，在 `pom.xml` 的版本号或构建元数据中标注 `day07-candidate`，重新打包，保持 Schema 不变；这可以演练部署动作，但必须如实标注“候选只修改版本标识”，不能声称验证了新业务功能。

Windows PowerShell，`$JavaBase` 使用 Day 06 记录的同一 digest；新开会话需要重新从记录读取，不盲目拉最新 JRE。先按 Day 05 第 6 节重新设置测试库环境变量并启动本地 MySQL，确认 `verify` 连接的是 `quephoto_test`。

```powershell
Set-Location 'D:\Project\QuePhoto_java'
$ServerAddress = Read-Host '输入服务器 IP 或 SSH 主机名'
$SshLogin = Read-Host '输入 SSH 用户名'
$JavaBase = Read-Host '粘贴 Day 06 已记录的 JRE repo@sha256:digest'
if (-not $JavaBase.Contains('@sha256:')) { throw '必须使用已记录的摘要' }
.\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { throw '候选验证失败' }
docker build --platform linux/amd64 --build-arg "JAVA_BASE=$JavaBase" -t quephoto:day07-candidate .
if ($LASTEXITCODE -ne 0) { throw '候选镜像构建失败' }
docker save --output '.\target\release\quephoto-day07.tar' quephoto:day07-candidate
Get-FileHash '.\target\release\quephoto-day07.tar' -Algorithm SHA256
scp '.\target\release\quephoto-day07.tar' "${SshLogin}@${ServerAddress}:quephoto-day07.tar"
```

服务器 Linux Bash，在 SSH 用户家目录：

```bash
sha256sum quephoto-day07.tar
sudo install -m 600 quephoto-day07.tar /opt/quephoto/releases/quephoto-day07.tar
sudo docker load --input /opt/quephoto/releases/quephoto-day07.tar
sudo docker image inspect quephoto:day07-candidate --format '{{.Id}}'
```

核对传输 SHA256、镜像 ID、CPU 架构。不要把新候选标成 baseline 覆盖旧镜像。

### 6.3 发布候选，验证，再回到 baseline

`sudoedit /opt/quephoto/.env`，只将第一行改为：

```dotenv
APP_IMAGE=quephoto:day07-candidate
```

保留 MySQL、Nginx 原 digest 不变。服务器 Linux Bash：

```bash
cd /opt/quephoto
sudo docker compose config --quiet
sudo docker compose up -d --no-deps app
sudo docker compose ps
sudo docker compose logs --tail=80 app
```

等 app healthy 后重启 Nginx，使其重新解析可能变化的 app 容器 IP：

```bash
sudo docker compose restart nginx
curl --fail --silent --show-error 'http://127.0.0.1:18080/api/portfolios?page=1&pageSize=20'
```

通过隧道验证登录、一个已发布详情和管理员草稿详情；记录状态码、真实作品数量、实际镜像 ID。候选失败时，不继续创建内容。

执行回滚：再次 `sudoedit /opt/quephoto/.env` 将 `APP_IMAGE` 改回 `quephoto:day06-baseline`，重复 `up -d --no-deps app`、等待健康、`restart nginx`、业务检查。数据库服务和卷始终保留。

回滚成功的标准不是看到旧标签，而是：运行中容器实际镜像与 baseline 一致；真实作品、图片关联、标签数量未变；登录和查询正常；最新数据仍存在。版本标签可能指向错误镜像，因此需要同时核对实际镜像 ID。

```bash
app_id=$(sudo docker compose ps -q app)
sudo docker inspect --format '{{.Image}}' "$app_id"
sudo docker image inspect quephoto:day06-baseline --format '{{.Id}}'
```

如果候选已修复必须的缺陷，在 baseline 演练验证后，再按同样步骤发布候选并复测；最终运行哪一版写入记录。回滚不等于永久停留在旧版。

### 6.4 Schema 不兼容时怎么办

应用回滚不会撤销 Flyway 迁移。旧程序不知道新表通常问题较少，但删列、改列语义、收紧约束可能直接使旧版失效；不能只比较迁移版本号就认定兼容。

发现不兼容：暂停写入 → 保留生产现场与备份 → 用备份恢复到另一个库 → 选择兼容程序验证 → 明确评估新增数据如何保留 → 再制定切换步骤。这是故障恢复工作，不是本次 35 分钟演练的一部分。不要在生产上临时手写向下迁移，也不要把数据库卷删除后重建。

## 7. 完成两次有边界的故障练习（可用缓冲 10–15 分钟）

主线验收未完成时，不额外扩大故障注入。仅执行下面低影响练习，提前暂停本人的管理写操作：

| 练习 | 怎么做 | 预期 | 恢复方式 |
|---|---|---|---|
| 关闭本地隧道 | 在隧道窗口 Ctrl+C，再请求 API | 本地连接失败，服务器数据不受影响 | 按 Day 06 重建 SSH 隧道 |
| 重启应用 | `sudo docker compose restart app` | 短暂不可用，恢复后数据不变 | 等待健康，必要时检查日志、重启 Nginx |

只有在隔离测试环境才做：错误数据库密码、模拟 OSS HEAD 失败、导入中途失败、错误签名 JWT、并发发布/封面修改。生产不做断电、删卷、截断表、修改已有迁移、删除 OSS 对象等“演练”。

学习点：你要能区分网络不可达、HTTP 401、HTTP 404、HTTP 500 和 Nginx 502。把所有失败都称作“后端挂了”，就无法决定该查 SSH、Security、路由、业务还是上游连接。

## 8. 不依赖复制粘贴的 Java 考核（25 分钟）

先关掉提示，选择一个小练习，在本地分支完成；练习不必自动部署。重点是你理解修改经过哪些层，而不是代码行数。

### 练习 A：校验和错误响应（8 分钟）

任务：定位标题的长度/空白校验，写出一个空白标题请求、一个超过 100 字符请求，解释为什么应为 400。自己修改一条错误消息，并验证仍以契约中的 `errors.title` 返回。

提示：DTO 的 Bean Validation 负责输入形状；去首尾空格与业务归一化需要明确顺序；统一异常处理负责错误格式。不要把校验只写在前端，也不要仅依赖数据库报错。

答案要点：能找到实际 DTO 和异常处理类，说明 `@NotBlank`、`@Size` 检查什么；改消息后对应请求和测试通过；没有改变公开 API 字段名。

### 练习 B：阅读 Mapper（5 分钟）

任务：不看现成答案，写出“某作品的所有图片按 work 在前、scene 在后，再按顺序及 ID 排列”的 SQL，并指出参数如何绑定。

提示答案：

```sql
SELECT id, portfolio_id, image_type, object_key, sort_order, width, height
FROM portfolio_image
WHERE portfolio_id = #{portfolioId}
ORDER BY CASE image_type WHEN 'work' THEN 0 ELSE 1 END, sort_order, id;
```

要解释：`#{portfolioId}` 是绑定参数；不用字符串拼接；数据库排序保证所有调用者一致。SQL 客户端直接执行时应使用具体测试 ID，而不是把 MyBatis 占位符原样贴进去。

### 练习 C：事务与导入（5 分钟）

任务：画出“HEAD 预检查→事务→锁作品→登记图片→设置封面→提交”，说明第二张图片失败时为何不能留下第一张的部分登记。

提示答案：网络预检查尽量放事务前；事务中仍要重查作品状态及归属；一批 DB 写操作共同提交或回滚。事务不能撤销之前的 OSS 上传，上传成功但登记失败的对象需要记录核对；不能凭“事务回滚”宣称对象也被删除。

### 练习 D：鉴权与配置（7 分钟）

任务：解释为何有 SSH 隧道仍需要 JWT；列出验证令牌的至少四个要点；指出生产 BCrypt 哈希在哪里读取。

提示答案：隧道保护传输和入口，JWT 识别管理请求身份；校验签名/允许算法、exp、issuer、audience，并按固定管理员权限授权。BCrypt 用于密码验证；JWT 签名不是加密。哈希来自私有文件，经 configtree 成为 `admin.password-hash`，不是硬编码默认密码。

评分：每题“独立完成 / 看提示完成 / 仍不会”。未独立完成不代表项目失败，但需要进入第二周学习清单；不要把 AI 生成过该功能算作你会实现。

## 9. 写出能让明天的自己照着操作的手册（20 分钟）

完善计划目标 `D:\Project\QuePhoto_java\docs\operations.md`。只写已经验证过的实际步骤；保留 Day 06 模板中的路径，但把版本号、备份时间、访问方式替换为实际记录。

至少包含以下九项：

1. **运行信息**：系统/架构、Compose 项目名 `quephoto`、应用镜像标签和 ID、基础镜像 digest、Schema 版本、最后验收时间。
2. **安全访问**：SSH 用户及主机别名、隧道命令、HTTP 客户端 base URL；不写私钥或口令。HTTPS 未完成就明确记录。
3. **新增作品**：登录→创建草稿→控制台上传→受控清单→显式导入→设置标签/封面→发布→公开检查。
4. **编辑与下架**：GET 当前管理详情→按请求 schema 构造完整 PUT→验证；不提供物理删除流程。
5. **备份巡检**：脚本路径、cron 执行时区、最近成功时间、保留至少 7 份、异地副本路径、最近恢复演练耗时。
6. **发布与回滚**：本地 verify→唯一版本镜像→SHA256→备份→Schema 兼容检查→切换→复测→保留上版。
7. **故障定位**：先分辨 SSH/网络、Nginx、应用、数据库、OSS；写下对应最短检查命令。
8. **凭据维护**：只写私有文件位置和负责人；数据库改密码、JWT 换密钥、OSS 凭据轮换分别需要验证什么。
9. **边界**：P0 无管理网页、无签名直传、无物理删除；原小程序未切换；数据库恢复不包含 OSS 文件恢复。

可复制的每日巡检清单：

```text
日期和实际操作者：
当前应用镜像 ID / Schema：
容器状态及最近错误：
最近成功备份时间 / 大小 / 异地副本：
磁盘与内存是否正常：
一个公开作品和一张真实图片是否可访问：
是否有本次发布/维护的异常：
发现问题后的下一步与责任人：
```

RPO 24 小时、RTO 2 小时是初始运行目标，不是承诺。填入真实恢复耗时；备份只在服务器本盘时，服务器损坏仍可能丢失全部副本。更频繁录入内容后应提高备份频率。

## 10. 最终交付与下一周安排

本周可接受的结果有两个访问形态：

| 实际状态 | 可以写的结论 | 不能写的结论 |
|---|---|---|
| SSH 隧道运营通过 | Java 后端已部署到服务器，本人可安全维护真实内容 | 公网 API 已完成、小程序已经迁移 |
| HTTPS 运营通过 | Java 后端 HTTPS API 已可用，本人可管理真实内容 | 原小程序已自动接入或无需后续适配 |

正式交付前检查：

- [ ] 至少三个真实作品、API 完整闭环、照片 URL 可读。
- [ ] 12 个 P0 method + path 与 OpenAPI、请求集合一致。
- [ ] 关键认证、草稿隔离、发布、标签和导入测试通过。
- [ ] 数据库与应用端口不公开；秘密不在 Git、日志或普通文档。
- [ ] 独立库恢复和应用回滚已实际验证，有耗时与证据。
- [ ] 当前版、上版、Schema、配置位置、备份位置明确。
- [ ] Java 学习考核标注真实水平，不用“看过教程”替代实践。
- [ ] 失败项、未做项、延期项写清，没有隐藏在“基本完成”中。

第二周按优先级选择一个方向，不同时开三条线：先修复未通过的 P0；然后补本人不会的 Java/SQL/测试知识；P0 稳定后再选签名直传，或管理前端/现有小程序接口适配。前端以后以冻结的 API 为准，本周已有的 OpenAPI 和真实响应就是它的起点。

最后给自己写三句话：我现在能独立完成哪项维护；我仍不能独立解释哪项 Java 行为；下一次发布前我必须检查什么。七天能交付一个小后端并建立学习路径，不意味着已掌握整门 Java 或生产运维。

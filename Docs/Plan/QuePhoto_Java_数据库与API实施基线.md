# QuePhoto Java：数据库与 API 实施基线

日期：2026-10-01。配合[7 天计划书](D:/Project/QuePhoto_java/Plan/QuePhoto_Java_7天MVP计划书.md)使用。

这是待实现的设计，不是现有 Java 功能说明。完整 P0 是国庆按 28 小时编排的后端 MVP 目标，实际仅 21 小时时可能顺延；P1 为额外 6–10 小时的签名直传增强。前端暂不修改，后续按本文件冻结的接口适配。

## 1. 设计依据和冲突裁决

优先级：本次用户确定的范围 → 本文件的明确 MVP 决策 → 已确认 V1 API 契约 → 原数据库设计 → 旧教程与示例代码。旧代码与契约不一致时，不能默认旧代码正确。

| 事项 | 本次决策 | 依据/差异 |
|---|---|---|
| 业务主体 | 作品集 Portfolio，下挂多张图片 | [原建模](D:/Project/QuePhoto3.0/Docs/Plans/摄影作品小程序-数据库建模设计.md:25)，不增加 Album/User |
| 发布状态 | 新增 draft/published | [API:223](D:/Project/QuePhoto3.0/Docs/Plans/摄影作品平台_V1_API接口契约.md:223) 比旧数据库“不含状态”约定更新 |
| 管理员 | 单账号安全配置；不建管理员表 | [API:17](D:/Project/QuePhoto3.0/Docs/Plans/摄影作品平台_V1_API接口契约.md:17) 优先于旧教程 AdminUser 示例 |
| JSON | camelCase；枚举小写字符串；列表分页对象 | 旧 C# 裸数组和默认数字枚举不能继续作为前端契约 |
| 拍摄时间精度 | year/month/day/hour/minute | 不实现旧建模中的 second；API 与现有 C# 枚举只到 minute |
| 拍摄时间格式 | 本地墙上时间，无时区后缀；按精度展示 | 原样例无偏移，而旧 OAS 标成 date-time；新 OAS 用字符串 pattern/example 说明 |
| 图片更新 | 不允许改 objectKey | [API:257](D:/Project/QuePhoto3.0/Docs/Plans/摄影作品平台_V1_API接口契约.md:257)；拆分创建/更新 schema，修正旧 OpenAPI 复用错误 |
| 图片登记 | P0 为离线导入；P1 才开放授权登记 API | 明确缩减旧完整 V1，不实现任意 Key 远程登记 |
| 物理删除 | P0/P1 均延期，使用 draft 下架 | 原建模与 API 的 OSS/DB 删除顺序冲突；未来单独设计半失败恢复后再开放 DELETE |
| 分享图 | P0 不支持写入；shareImageUrl 使用封面回退 | 保留字段；本轮请求不接受非空 shareImageKey；独立上传后续做 |

原 OpenAPI 还缺若干响应 schema、后台分页参数和标签/上传请求体，不能直接据此生成完整客户端。D5 的任务是在新项目补全实际实现的契约，并清楚标记延期接口。

## 2. Java 工程结构

使用项目根目录的单 Maven 模块，按业务分包。基础工程已导入；以下业务目录仍按计划逐步实现，现有 Docs 和 practice 保留：

```text
QuePhoto_java/
├── pom.xml、mvnw、mvnw.cmd、.mvn/
├── src/main/java/com/quephoto/
│   ├── QuePhotoApplication.java
│   ├── portfolio/   # Controller、Service、Mapper、Entity、DTO、枚举
│   ├── tag/         # 标签树查询与作品标签设置
│   ├── auth/        # 登录、密码核验、JWT
│   ├── oss/         # 配置、URL 生成、对象 HEAD 核验；P1 签名
│   ├── importing/   # 显式非 Web 模式的图片清单导入命令
│   └── common/      # 异常响应、分页 DTO、安全配置
├── src/main/resources/
│   ├── application.yml、application-local.yml、application-prod.yml
│   ├── mapper/      # 必要的 MyBatis XML
│   └── db/migration/
├── src/test/
├── docs/openapi.yaml、docs/requests.http、docs/operations.md
├── examples/import-images.example.json
├── Dockerfile、compose.yaml、deploy/nginx.conf
└── .env.example    # 只含配置项和占位说明
```

调用路径：Controller 负责 HTTP/DTO；Service 负责业务规则和事务；Mapper 负责参数化 SQL。无需额外通用 Repository、Service 接口套实现类、多模块工程或微服务。

依赖范围：Spring MVC、Validation、Security/JWT、MyBatis Starter、MySQL 驱动、Flyway（含 MySQL 模块）、OSS SDK、Boot 测试依赖。按选定 Boot 版本生成并验证依赖；优先其依赖管理，不手工给所有传递依赖指定版本。第一周不加 Lombok、Redis、MyBatis-Plus 或动态 API 文档 UI 的额外依赖。

Flyway 是唯一 Schema 入口；不用 `ddl-auto=update`，不与自动 `schema.sql/data.sql` 混用。测试种子与生产内容分开，生产启动不能自动插入演示作品。官方依据：[Spring 数据库初始化](https://docs.spring.io/spring-boot/how-to/data-initialization.html)。

## 3. 数据库基线

MySQL 8.4、InnoDB、utf8mb4；snake_case 表/列名。持久化枚举使用可读字符串并加 CHECK，这是相对旧 TINYINT 枚举的有意调整。Java 层显式映射小写值，不用枚举 ordinal。

### 3.1 五张业务表

| 表 | 字段 |
|---|---|
| `portfolio` | `id BIGINT` 自增主键；`title VARCHAR(100) NOT NULL`；`description TEXT NULL`；`location VARCHAR(200) NULL`；`shot_at DATETIME NULL`；`shot_time_precision VARCHAR(10) NULL`；`status VARCHAR(10) NOT NULL DEFAULT 'draft'`；`cover_image_id BIGINT NULL`；`share_image_key VARCHAR(500) NULL`；`created_at/updated_at DATETIME(3) NOT NULL` |
| `portfolio_image` | `id BIGINT` 自增主键；`portfolio_id BIGINT NOT NULL`；`image_type VARCHAR(10) NOT NULL`；`object_key VARCHAR(500) NOT NULL`；`sort_order INT NOT NULL DEFAULT 0`；`width/height INT NULL`；`created_at DATETIME(3) NOT NULL` |
| `tag_group` | `id BIGINT` 自增主键；`name VARCHAR(50) NOT NULL`；`sort_order INT NOT NULL DEFAULT 0`；`created_at DATETIME(3) NOT NULL` |
| `tag` | `id BIGINT` 自增主键；`group_id BIGINT NOT NULL`；`name VARCHAR(50) NOT NULL`；`sort_order INT NOT NULL DEFAULT 0`；`created_at DATETIME(3) NOT NULL` |
| `portfolio_tag` | `portfolio_id BIGINT NOT NULL`；`tag_group_id BIGINT NOT NULL`；`tag_id BIGINT NOT NULL`；组合主键 `(portfolio_id, tag_group_id)` |

标题和标签名去首尾空格，空白名称拒绝；描述暂定上限 10,000 字符，是本次新增的输入边界。ID 延续 JSON 整数，不引入雪花 ID；服务端限制在 JavaScript 安全整数范围内，超过范围返回参数错误而不是向未来前端输出失真 ID。

### 3.2 必须保留的约束

| 约束 | 实现位置/意义 |
|---|---|
| `UNIQUE(tag_group.name)` | 一级标签名不重复；应用与数据库统一名称比较规则 |
| `UNIQUE(tag.group_id, tag.name)` | 同组二级标签名不重复 |
| `UNIQUE(tag.id, tag.group_id)` | 为组合外键提供目标候选键 |
| `portfolio_tag` 主键 `(portfolio_id, tag_group_id)` | 每作品、每组至多一个标签 |
| `(tag_id, tag_group_id)` → `tag(id, group_id)` | 防止标签实际归属组与关联记录不一致 |
| 图片 → 作品；标签 → 标签组；作品标签 → 作品 | 普通外键，清晰定义 RESTRICT；首周不暴露删除 |
| 封面 → 图片 | 只保证图片存在；“属于本作品且为 work”仍由 Service 校验 |
| `UNIQUE(portfolio_image.object_key)` | 本项目一张对象只登记一次；同 Key 不可挂到另一作品 |
| status、image_type、precision CHECK | 限制合法枚举；时间/精度必须同时空或同时有 |
| 宽高 CHECK | 两者都 NULL，或两者都是正整数 |

ObjectKey 列及对应唯一索引按大小写敏感的比较规则，例如 `utf8mb4_bin`；不要因默认大小写不敏感排序规则把两个不同 OSS Key 当成同一对象。新导入流程要求一对象归属一作品；若历史数据真实存在共享 Key，导入前报告，不静默复制或删除对象。

封面造成作品和图片互相引用：先建作品表（暂不加 cover FK），再建图片表，最后 `ALTER TABLE` 补封面 FK。创建业务顺序也相同：草稿空封面 → 登记图片 → 设置封面 → 发布。未来删除需先处理封面引用和 OSS 半失败，此处不提前实现。

初始索引：`portfolio(status, shot_at, id)`；`portfolio_image(portfolio_id, image_type, sort_order, id)`；`portfolio_tag(tag_id, portfolio_id)`；其余随唯一键/外键建立。先验证查询，不为几条数据引入缓存。

### 3.3 时间约定

- `shotAt` 是摄影内容的墙上时间，Java 用 `LocalDateTime`；不自动转成 UTC，不凭空添加 `Z`。首周按作者填写的拍摄当地时间理解，不支持跨时区换算。
- `year` 用该年 `01-01T00:00:00` 占位；`month` 用当月 1 日零点；`day` 清空时分秒；`hour` 清空分秒；`minute` 清空秒。后端统一规范化，输出遵循精度。
- 完全未知时，时间和精度均为 null。前端将来只显示已知部分，不能把占位的 1 月 1 日当成真实日期。
- `createdAt/updatedAt`、令牌过期时间是实际时刻，Java 用 `Instant`，数据库按 UTC 写入，JSON 使用 `Z`。数据库连接和审计字段转换需测试；每次写操作明确维护 `updatedAt`。

### 3.4 查询与事务

公开列表条件始终包含 `status = 'published'`；详情对 draft 和不存在均返回 404。排序显式表达未知日期最后：

```sql
ORDER BY (p.shot_at IS NULL) ASC, p.shot_at DESC, p.id DESC
```

图片排序不能直接按字符串字母序，否则 scene 会排在 work 前：

```sql
ORDER BY CASE image_type WHEN 'work' THEN 0 ELSE 1 END, sort_order, id
```

tagId 筛选使用 `EXISTS`，避免 Join 多张图片/多标签后重复计数。`totalCount` 与列表使用相同过滤条件；列表使用数据库 LIMIT/OFFSET，不先把全表加载到 Java 再分页。

以下操作在 Service 中使用事务：替换作品标签、导入图片并设封面、发布校验与状态更新。它们和图片类型/封面修改统一先锁定对应作品行，防止两个管理请求并发使已发布作品失去合法封面。SQL 参数使用 `#{...}`，不把用户字符串拼到 SQL；首周排序方式固定。

## 4. P0 API 清单

除登录和公开查询外，所有 `/api/admin/**` 必须鉴权。共 12 个 method + path 操作；未实现的接口不出现在“可用 API”清单中。

| 方法与路径 | 用途 | 关键行为 |
|---|---|---|
| `GET /api/portfolios` | 公开分页 | page 默认 1；pageSize 默认 20、范围 1–50；可选 tagId |
| `GET /api/portfolios/{id}` | 公开详情 | 仅 published；不暴露 objectKey 字段 |
| `GET /api/tag-groups` | 标签树 | 标签组及标签按 sortOrder/id 升序 |
| `POST /api/auth/login` | 管理员登录 | 返回 accessToken/expiresAt；错误凭据统一 401 |
| `GET /api/admin/portfolios` | 管理列表 | 分页；可选 status=draft/published；包括草稿 |
| `GET /api/admin/portfolios/{id}` | 管理详情 | 增加 status、coverImageId、objectKey、shareImageKey |
| `POST /api/admin/portfolios` | 创建草稿 | 强制初始 draft；不接受上传文件或非空 shareImageKey |
| `PUT /api/admin/portfolios/{id}` | 更新元数据/发布/下架 | published 前检查合法 work 封面；不能绕过校验 |
| `PUT /api/admin/portfolios/{id}/cover` | 设置封面 | 图片必须属于当前作品且为 work |
| `PUT /api/admin/portfolios/{id}/tags` | 替换标签 | 从 tagIds 查归属组；重复/同组多选拒绝；空数组清空 |
| `PUT /api/admin/portfolios/{id}/images/{imageId}` | 图片类型、顺序、尺寸 | 拒绝 objectKey 变更；必须属于当前作品；不能破坏已发布封面 |
| `GET /api/admin/tag-groups` | 管理标签树 | P0 复用公开树数据，保留未来管理扩展路径 |

不存在的路由返回 404；暂不实现的 POST 图片登记/上传 policy、标签写入、所有 DELETE 不得伪造成功。下架属于业务发布状态变化，不是删除数据库记录。

### 4.1 请求和返回示例

创建草稿：

```json
{
  "title": "城市雨夜",
  "description": "雨中的街头。",
  "location": "上海",
  "shotAt": "2026-09-01T00:00:00",
  "shotTimePrecision": "month",
  "status": "draft"
}
```

成功 `201`，返回 `{"id":1001}`。PUT 元数据是完整替换上述可编辑字段：必填字段缺失拒绝，可空字段显式传 null；先 GET 管理详情再构造编辑请求。发布与下架复用 PUT，只调整 status 并带齐其他字段。不得把“缺省字段”默默置空。

公开分页响应：

```json
{
  "items": [{
    "id": 1001,
    "title": "城市雨夜",
    "location": "上海",
    "shotAt": "2026-09-01T00:00:00",
    "shotTimePrecision": "month",
    "coverImageUrl": "https://images.example.com/photos/2026/10/uuid.jpg"
  }],
  "page": 1,
  "pageSize": 20,
  "totalCount": 1
}
```

公开详情沿用旧 API 的 `images[]` 与 `tags[]`，不用教程里的 `workImages/sceneImages` 两个数组。图片包含 `id/imageType/imageUrl/sortOrder/width/height`；标签包含 `groupId/groupName/tagId/tagName`。图片按前文规则排序，shareImageUrl 在 P0 回退到封面 URL。公开 DTO 和管理员 DTO 分开定义。

封面请求 `{"imageId":2001}`，P0 不支持 null 清除；如需修改封面，直接换成另一张合法 work 图片。更新图片请求只包含 `imageType/sortOrder/width/height`。标签请求 `{"tagIds":[12,25]}`。P0 保留 scene 枚举和同表读写，不做特殊场景图功能。

### 4.2 错误约定

使用 Java 的 `ProblemDetail` 或等价 DTO，加 `errors` 字段保留旧客户端可理解的验证结构；不需要模拟 ASP.NET 的具体类。

```json
{
  "title": "请求校验失败",
  "status": 400,
  "errors": { "title": ["作品集标题不能为空。"] }
}
```

200 查询/更新；201 创建；400 参数或发布条件不合法；401 无令牌/失效/凭据错误；403 已认证但无权限；404 不存在或公开访问草稿；409 唯一键冲突；429 登录限流；500 未预期失败。P1 OSS 授权/对象核验失败使用 502，不能用 200 加错误字符串掩盖失败。

## 5. P0 图片导入：正式的最小运营流程

此方案有意替代首周的签名上传 API。管理员拥有服务器和 OSS 运维权限，通过控制台上传；离线命令复用同一套 Service 规则登记。**它不是生产启动自动执行的种子脚本，也不是将任意 ObjectKey 放行的 HTTP 后门。**

清单示例（真实 Key 需要由本人替换）：

```json
{
  "portfolioId": 1001,
  "images": [
    {"objectKey":"photos/2026/10/uuid-a.jpg","imageType":"work","sortOrder":10,"width":null,"height":null},
    {"objectKey":"photos/2026/10/uuid-b.jpg","imageType":"scene","sortOrder":20,"width":null,"height":null}
  ],
  "coverObjectKey":"photos/2026/10/uuid-a.jpg"
}
```

计划命令入口示意，需 D4 实现后才能使用：

```text
java -jar quephoto.jar --spring.profiles.active=prod,import --spring.main.web-application-type=none --import.file=/secure/import-images.json
```

`prod,import` 同时加载生产配置与一次性导入执行器，缺少文件参数时直接失败退出；本地使用 `local,import`。Servlet 安全配置只在 Web 模式启用，避免非 Web 启动时缺少 HttpSecurity。正常 `prod` 启动不读取任何清单；命令执行后关闭 Spring 上下文和连接池。在同一 Compose 网络、挂载只读清单并注入受保护配置的临时容器运行；不在命令行传密码，不为导入开放数据库公网端口。D4 验证本地模式，D6 验证服务器上的同一命令。

导入步骤与边界：

1. 只允许已存在的 draft 作品；清单不能指定 Bucket、BaseUrl 或数据库连接。
2. 从固定 Bucket 和配置的允许前缀解析 ObjectKey；拒绝 URL、跨前缀 Key、清单重复 Key。旧目录需显式加入允许范围，不重命名旧对象。
3. 通过 OSS HEAD 核验所有对象存在、Content-Type 在 JPEG/PNG/WebP 白名单、大小 ≤30 MB；尺寸可为空，不把客户端填的尺寸当成自动提取结果。类型元数据不是内容鉴别：P0 上传者为可信管理员，自动图片解码检查不在首周范围。
4. 预检查完成后开启数据库事务，锁定作品并再次检查为 draft；拒绝已归属其他作品的 Key。
5. 同作品、同 Key 且登记信息完全一致时复用原记录；不同信息则报冲突，要求通过图片更新 API 修改，避免重试覆盖内容。
6. 整批插入、设封面一起提交；封面必须在该清单的 work 图片中。一项失败则整批无写入。数据库唯一约束兜底并发冲突。
7. 返回/打印作品 ID、图片 ID、实际新增数量，敏感配置不入日志；不自动发布，也不删除/覆盖任何 OSS 文件。

新图片 Key 采用 `photos/{year}/{month}/{uuid}.{extension}`。控制台上传前准备唯一文件名，避免覆盖现有对象。已有线上图片只引用经确认可复用的 Key。上传成功但登记失败会留下未引用对象，首周记录清单后人工核对，不自动清理原 Bucket。

## 6. P1 签名直传：有额外时间再做

P0 全部验收通过再开始，预算 6–10 小时：授权记录和策略 2h、登记校验与幂等 2h、上传脚本/契约 1h、失败场景与真 OSS 验证 1–3h，剩余为排障。

新增接口：`POST /api/admin/uploads/policy`、`POST /api/admin/portfolios/{id}/images`。保持旧路径和 camelCase；首批 purpose 仅 work/scene，share 延后。请求先有草稿 portfolioId，再申请固定 Key 的上传权限。

新增 Flyway 迁移和 `upload_grant` 技术表：UUID 主键、portfolio_id、purpose、object_key（唯一、大小写敏感）、content_type、max_bytes、expires_at、consumed_at、created_at。有效期默认 5 分钟；首版要求登记时也未过期，过期未登记时重新授权上传，原孤立对象记录待人工清理。以后有真实需求再拆上传与登记两个时限。

完整链路：鉴权 → 创建授权记录 → 签发 OSS POST V4 表单 → HTTP 工具/脚本直传 OSS → 后端登记时核验授权归属/用途/期限和 HEAD 结果 → 同一数据库事务消费授权并登记图片。网络超时重试同 Key，若已经成功登记到同作品/用途，则返回同一图片 ID；不重复创建。

POST policy 限制固定 Key、类型、大小、过期时间；签名格式使用官方 SDK/示例，不手写加密算法。`formFields` 视为不透明字段字典，实际使用 V4 返回的全部字段，不假定旧文档 V1 的 `OSSAccessKeyId/signature` 一定适用。客户端最后提交 file 字段。

P1 仍是单可信管理员上传，不是向普通用户开放投稿。不要把 MIME 检查称为可靠图片内容检测；未来开放不可信上传前另加内容校验。官方依据：[OSS POST V4 策略约束](https://help.aliyun.com/zh/oss/developer-reference/signature-version-4-recommend)。

## 7. 鉴权、配置与图片访问

- 单管理员用户名和 BCrypt 密码哈希从安全配置读取；没有默认生产密码，无注册/找回/多角色页面。Spring Security 使用现成 PasswordEncoder，不自己实现密码算法。[密码存储文档](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- JWT 有效期建议 60 分钟，无刷新令牌。使用维护中的编码/解码组件，固定算法并校验签名、exp、issuer、audience；密钥随机生成、环境注入。权限声明来自后端固定管理员身份，不信任请求体传入的角色。[JWT 验证文档](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- `/api/admin/**` 默认要求管理员身份；只对白名单公开 GET 和登录放行；不要只在个别 Controller 手动检查令牌。JWT 仅由 Authorization Bearer 提交，P0 不做浏览器 Cookie 会话。
- 单实例登录限流采用进程内有界计数或 Nginx 限速，文档记录实际值（建议每来源每分钟 5 次，适当允许短时突发）；错误账号与错误密码同样返回 401，限流 429。不为此引入 Redis。
- 配置项至少包括数据库连接/凭据、ADMIN_USERNAME、ADMIN_PASSWORD_HASH、JWT_SECRET/ISSUER/AUDIENCE、OSS_REGION/BUCKET/BASE_URL、允许前缀、凭据来源。`.env.example` 无真实值，生产配置不入 Git、日志或 `.http` 文件。
- Java 服务用最小权限 RAM 角色/凭据；P0 只需核验对象的读取权限。控制台上传使用本人受控账号。P1 再按实际签名上传所需权限扩展，不授予 Bucket 管理或 DeleteObject。
- 沿用既有 OSS 图片访问模式，不全局修改 ACL。公开读图片的 URL 可能暴露 Key；DTO 不带 objectKey 只是契约边界，不提供保密性。draft 仅保证元数据不公开，不能撤销已经知道的公开图片 URL。
- 若确实要求草稿图片保密，应使用独立私有目录/权限方案和签名读 URL，排入后续专项工作；不要在本轮擅自把现有公开 Bucket 改为私有而破坏已上线小程序。

## 8. 旧数据迁移与后续前端接入

旧 SQLite 文件先保留只读备份；不是所有数据都能当种子丢弃。现有 5 条作品中 2 条无图，3 个图片 Key 是 demo 路径。默认新 MySQL 从空 Schema 开始，导入本人确认的真实作品；旧数据如需迁移，先按行核对，不自动发布。

迁移检查：旧枚举 1/2 → work/scene；时间精度 1–5 → year…minute；检查封面归属、空值组合、重复 Key、标签约束；保留需要稳定的原 ID，并校准 MySQL AUTO_INCREMENT。没有合法图片/封面的旧作品保持 draft。导入后核对数量、关联、公开查询和图片实际可访问性。

未来前端适配清单：

1. 列表从可能的裸数组改成 `items/page/pageSize/totalCount`。
2. 枚举使用小写字符串，不使用 C# 数字枚举。
3. 公开详情只拿 URL，images 同数组通过 imageType 区分；分享图回退明确。
4. 拍摄时间按 precision 格式化；草稿详情 404；管理写请求携带 Bearer。
5. 配置真实 HTTPS API 域名，完成微信 request 域名和真机校验后再切换。

这次不复制现有小程序、不修改线上配置。新的 OpenAPI、请求集合和真实响应样例，是留给后续前端工作的交付接口。

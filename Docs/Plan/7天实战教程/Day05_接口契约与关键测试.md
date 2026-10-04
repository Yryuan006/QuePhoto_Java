# Day 5：把“我能调用”变成“接口有契约、错误有验证”

今天交付：一份对应 12 个 P0 接口的 OpenAPI、可重放的请求集合、关键自动化测试和最小操作说明。今天不写前端，也不增加签名上传。

先决条件：Day 2 的数据库与公开查询、Day 3 的真实登录鉴权、Day 4 的导入与发布链路均能本地运行。未完成就先补齐，不能靠修改测试期望把缺失功能变成通过。

导航：[教程目录](D:/Project/QuePhoto_java/Docs/Plan/7天实战教程/README.md) · [上一天](D:/Project/QuePhoto_java/Docs/Plan/7天实战教程/Day04_图片导入与作品发布.md) · [下一天](D:/Project/QuePhoto_java/Docs/Plan/7天实战教程/Day06_服务器部署与备份恢复.md)

## 0. 今天具体写哪些文件，按什么顺序写

**本篇是第5天待你完成的任务说明；下面的测试和契约示例供你逐段写入自己的工程，不表示已经生成文件或测试通过。** 今天的重点是学会用确定的输入判断输出是否正确。

| 顺序 | 目标文件 | 你具体要写什么 | 做到什么程度再继续 |
|---|---|---|---|
| A | `src/test/java/com/quephoto/importing/ImageImportCoordinatorTest.java` | 清单校验的单元测试 | 不连接MySQL和OSS也能测五种输入 |
| B | `src/test/resources/application-test.yml` | 专用测试库连接；凭据从TEST_DB变量取 | 确认只有quephoto_test权限，未激活local/prod |
| C | `src/test/java/com/quephoto/PortfolioApiTest.java` | 第7节的完整测试，再加入真实登录和发布失败用例 | 不关闭安全过滤器；能断言状态码和数据 |
| D | `Docs/requests.http` | 把第4节请求逐个保存，补齐管理分页/标签树/图片更新 | 每个变量知道从哪个响应取得 |
| E | `Docs/openapi.yaml` | 先写一个可校验的端点，再补全12个操作 | 每个ref有定义，响应与DTO一致 |
| F | `Docs/evidence/day05.md` | 测试命令、结果和未覆盖项 | 成功/失败/未做明确区分 |

**先学会读一个测试：**准备输入（Arrange）→ 执行一次操作（Act）→ 比较实际与预期（Assert）。测试名字写业务规则，例如“无封面不能发布”，不要只写 `test1`。

### 0.1 从一个不需要数据库的完整测试类开始

新建 `src/test/java/com/quephoto/importing/ImageImportCoordinatorTest.java`。以下构造器对应 Day04 第5.3节接好依赖之后的 Coordinator；如果你尚未完成该节，先补齐依赖，不通过删除断言让测试假通过。

```java
package com.quephoto.importing;

import com.quephoto.oss.OssImportProperties;
import com.quephoto.oss.OssObjectVerifier;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ImageImportCoordinatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    // 没有启动Spring；下面的占位配置只用于构造校验对象，不会连接OSS。
    private final ImageImportCoordinator coordinator = new ImageImportCoordinator(
            FACTORY.getValidator(),
            new OssImportProperties("test", "https://example.com", "test", "photos/", "unused", "unused"),
            mock(OssObjectVerifier.class), mock(ImageImportService.class));

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    private ImportManifest.ImageItem image(String key, String type, Integer width, Integer height) {
        return new ImportManifest.ImageItem(key, type, 10, width, height);
    }

    @Test
    void unknownDimensionsAndSecondImageCoverAreAllowed() {
        var input = new ImportManifest(1L, List.of(
                image("photos/scene.jpg", "scene", null, null),
                image("photos/work.jpg", "work", null, null)), "photos/work.jpg");
        assertDoesNotThrow(() -> coordinator.validateManifest(input));
    }

    @Test
    void oneMissingDimensionIsRejected() {
        var input = new ImportManifest(1L,
                List.of(image("photos/a.jpg", "work", 800, null)), "photos/a.jpg");
        assertThrows(IllegalArgumentException.class, () -> coordinator.validateManifest(input));
    }

    @Test
    void duplicateKeyIsRejected() {
        var item = image("photos/a.jpg", "work", null, null);
        var input = new ImportManifest(1L, List.of(item, item), "photos/a.jpg");
        assertThrows(IllegalArgumentException.class, () -> coordinator.validateManifest(input));
    }

    @Test
    void dotSegmentIsRejected() {
        var input = new ImportManifest(1L,
                List.of(image("photos/../a.jpg", "work", null, null)), "photos/../a.jpg");
        assertThrows(IllegalArgumentException.class, () -> coordinator.validateManifest(input));
    }

    @Test
    void sceneCannotBeCover() {
        var input = new ImportManifest(1L,
                List.of(image("photos/a.jpg", "scene", null, null)), "photos/a.jpg");
        assertThrows(IllegalArgumentException.class, () -> coordinator.validateManifest(input));
    }
}
```

`assertThrows` 的意思是“这个错误输入应该被拒绝”；如果程序没有抛异常，测试反而失败。`() -> ...` 是把待执行动作交给断言。`mock(...)` 在这里替代校验阶段不会调用的外部依赖，不能据此宣称OSS核验或数据库事务正常。

写完只跑这个类：

```powershell
.\mvnw.cmd '-Dtest=ImageImportCoordinatorTest' test
```

预期5个测试通过，且不要求数据库密码。若提示不存在这个测试类，检查文件是不是保存在 `src/test/java`，不要误放到 `src/main/java`。接着再做第6、7节的真实数据库测试。

### 0.2 写请求集合前先准备变量

| 变量 | 从哪里取得 | 常见错误 |
|---|---|---|
| `baseUrl` | 本地 `http://127.0.0.1:8080`，服务器隧道用18080 | 把图片域名当API域名 |
| `token` | 登录响应中的accessToken | 把整个登录JSON粘进去，或重复写Bearer |
| `portfolioId` | 创建草稿返回的id | 照抄示例1001 |
| `imageId` | 导入后GET管理详情的images[].id | 把作品ID当图片ID |
| `tagId` | GET标签树中某个二级标签的id | 使用标签组ID |

请求文件用 `{{变量名}}` 引用值。用户名、密码、token只写进客户端的私有环境，具体操作按你使用的IDE HTTP Client或API工具完成；不要把真实值写进可提交的 `.http` 文件。


## 1. 四小时怎么用

| 时间 | 动作 | 结果 |
|---|---|---|
| 0:00–0:30 | 核对路由和 DTO | 知道契约与实现哪里不一致 |
| 0:30–1:20 | 编写请求集合、跑完整流程 | 未来自己也能重复操作 |
| 1:20–2:00 | 补全 OpenAPI | 前端不必猜参数、错误和 null |
| 2:00–3:15 | 关键自动化与 MySQL 验证 | 核心规则可以重复验证 |
| 3:15–4:00 | 排错、文档、学习考核 | D6 能部署的稳定版本 |

自动化不必一口气覆盖全部组合。先确保鉴权、草稿隔离、发布校验和数据库约束有证据；发现失败后修实现，增加新的针对性用例。

## 2. 先理解三种不同的验证

**单元测试**只执行一个明确的规则，例如时间精度归一化，速度快、错误位置明确。**集成测试**让 Spring、SQL、数据库约束一起运行，验证组件之间的配合。**真实环境演练**检查 OSS、容器、网络、备份；Mock 不能代替它。

MockMvc 会在测试进程内模拟 HTTP 请求，不需要监听 8080，但可以经过真实 Controller、校验器和安全过滤器。开启 SpringBootTest 不等于所有外部系统都已经被测试到。

今天保持安全过滤器开启；不要使用 `addFilters=false` 后宣称“鉴权测试通过”。使用 Security 的模拟 JWT 辅助工具时，它只能验证已认证状态下的授权/业务行为，不能证明签名、过期、issuer/audience 的校验正确。官方依据：[MockMvc 配置](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.html)、[Security 测试说明](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2.html)。

## 3. 先列出真实路由，不依赖记忆

在 IDE 全项目搜索 `@GetMapping`、`@PostMapping`、`@PutMapping`、`@RequestMapping`。逐条填入下面清单；管理登录以外的管理接口都必须要求 Bearer。

| 方法 | 路径 | 成功响应 | 最重要的错误 |
|---|---|---|---|
| GET | `/api/portfolios` | 200 分页对象 | 400 分页非法 |
| GET | `/api/portfolios/{id}` | 200 公开详情 | 草稿/不存在 404 |
| GET | `/api/tag-groups` | 200 标签树数组 | 未预期失败 500 |
| POST | `/api/auth/login` | 200 token/expiresAt | 401 错误凭据、429 限流 |
| GET | `/api/admin/portfolios` | 200 管理分页 | 401、400 |
| GET | `/api/admin/portfolios/{id}` | 200 管理详情 | 401、404 |
| POST | `/api/admin/portfolios` | 201 `{id}` | 401、400 |
| PUT | `/api/admin/portfolios/{id}` | 200 更新后的管理详情 | 401、400、404 |
| PUT | `/api/admin/portfolios/{id}/cover` | 200 更新后的管理详情 | 401、400、404 |
| PUT | `/api/admin/portfolios/{id}/tags` | 200 更新后的管理详情 | 401、400、404 |
| PUT | `/api/admin/portfolios/{id}/images/{imageId}` | 200 更新后的图片 DTO | 401、400、404 |
| GET | `/api/admin/tag-groups` | 200 标签树 | 401 |

本教程统一上述 PUT 返回体；如果前一天暂时只返回空 200，今天补齐而不是让 OpenAPI 描述一个不存在的返回值。数据库唯一键冲突映射 409；部署入口限流可能返回 429。P0 没有任何 DELETE、上传 policy 或 HTTP 图片登记接口。

Day 1 的 `/api/hello` 完成教学使命后移除；不要把它放进业务路由清单。Day 6 使用容器内部端口探测判断进程是否监听，另用公开查询和管理员登录确认业务正常；本周不新增 `/health` API，不能拿端口正常代替业务验收。

## 4. 制作可重复使用的请求集合

目标文件：`D:\Project\QuePhoto_java\docs\requests.http`。下面是**可保存的请求模板**，`{{...}}` 为 HTTP 客户端变量；不是 PowerShell 语法。你使用 Postman 时按同样顺序建立集合。

```http
@baseUrl = http://127.0.0.1:8080

### 1. 登录：adminUsername/adminPassword 从不提交 Git 的私有环境读取
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{"username":"{{adminUsername}}","password":"{{adminPassword}}"}

### 2. 创建草稿，返回 id 后填入本地 portfolioId 变量
POST {{baseUrl}}/api/admin/portfolios
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "title":"API联调作品",
  "description":"用于验证新后端的内容维护流程",
  "location":"上海",
  "shotAt":"2026-10-01T00:00:00",
  "shotTimePrecision":"day",
  "status":"draft"
}

### 3. 查询管理详情
GET {{baseUrl}}/api/admin/portfolios/{{portfolioId}}
Authorization: Bearer {{accessToken}}

### 4. 没有导入图片/封面前，这次发布应该返回400
PUT {{baseUrl}}/api/admin/portfolios/{{portfolioId}}
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "title":"API联调作品",
  "description":"用于验证新后端的内容维护流程",
  "location":"上海",
  "shotAt":"2026-10-01T00:00:00",
  "shotTimePrecision":"day",
  "status":"published"
}

### 5. 按Day4在另一终端执行离线导入后，再查管理详情取得imageId
GET {{baseUrl}}/api/admin/portfolios/{{portfolioId}}
Authorization: Bearer {{accessToken}}

### 6. 设置封面
PUT {{baseUrl}}/api/admin/portfolios/{{portfolioId}}/cover
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{"imageId":{{imageId}}}

### 7. 用真实标签ID设置作品标签；空数组表示清空
PUT {{baseUrl}}/api/admin/portfolios/{{portfolioId}}/tags
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{"tagIds":[]}

### 8. 再执行请求4，此时应该200；随后公开查询
GET {{baseUrl}}/api/portfolios/{{portfolioId}}

### 9. 分页对象
GET {{baseUrl}}/api/portfolios?page=1&pageSize=20

### 10. 下架：完整PUT，其他字段保持一致
PUT {{baseUrl}}/api/admin/portfolios/{{portfolioId}}
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "title":"API联调作品",
  "description":"用于验证新后端的内容维护流程",
  "location":"上海",
  "shotAt":"2026-10-01T00:00:00",
  "shotTimePrecision":"day",
  "status":"draft"
}

### 11. 下架后这里必须404
GET {{baseUrl}}/api/portfolios/{{portfolioId}}
```

接着亲手补上管理分页、公开标签树、管理标签树、图片更新、tagId 筛选五类请求，并把成功响应保存成去除凭据的样例。你已在前面几天实现它们；今天的学习目标是用契约说明它们。

不要在集合中填真实密码和 token。私有环境文件加入 `.gitignore`；分享集合前检查 Authorization、登录 body、日志与响应。token 只保存在当前本地会话或私有变量中。

## 5. OpenAPI：明确字段而不是只列路由

目标文件：`D:\Project\QuePhoto_java\docs\openapi.yaml`，采用 OpenAPI 3.0.3，与原版本便于比较。先写一个端点再复制结构补齐 12 个端点。

下面是**局部结构示例，不是完整契约**；其中 `$ref` 所指 schema 都必须在完成后的文件中定义，不能留下悬空引用。

```yaml
openapi: 3.0.3
info:
  title: QuePhoto Java P0 API
  version: 0.1.0
servers:
  - url: http://127.0.0.1:8080
paths:
  /api/portfolios:
    get:
      summary: 获取已发布作品分页
      security: []
      parameters:
        - in: query
          name: page
          schema: { type: integer, minimum: 1, default: 1 }
        - in: query
          name: pageSize
          schema: { type: integer, minimum: 1, maximum: 50, default: 20 }
        - in: query
          name: tagId
          schema: { type: integer, format: int64, minimum: 1 }
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema: { $ref: '#/components/schemas/PortfolioPage' }
        '400':
          description: 参数错误
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/Problem' }
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    ShotAt:
      type: string
      nullable: true
      description: 拍摄当地墙上时间，无时区后缀；结合shotTimePrecision显示
      pattern: '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$'
      example: '2026-10-01T00:00:00'
```

真实错误处理若返回 `application/json` 而非 `application/problem+json`，应先统一实现和说明，两者不能在文档里互相假装。该示例选择后者；Day 3 的安全过滤器错误响应也要核对。

每条管理操作设置 `security: [{bearerAuth: []}]`；公开接口和登录明确无认证要求。不要把全局 Bearer 误应用到登录本身。

补齐 schema 时按此清单：

| Schema | 要写清的内容 |
|---|---|
| PortfolioPage / AdminPortfolioPage | items、page、pageSize、totalCount；不是裸数组 |
| PublicPortfolioDetail | description、images、tags、coverImageUrl、shareImageUrl、时间与地点；不含objectKey |
| AdminPortfolioDetail | 在公开字段上增加status、coverImageId、shareImageKey、图片objectKey |
| CreatePortfolioRequest | title必填/非空、长度；初始draft；可空字段规则 |
| UpdatePortfolioRequest | 六个可编辑字段必须出现，可空字段允许null，不能省略后清空 |
| UpdateImageRequest | imageType/sortOrder/width/height；禁止objectKey写入 |
| CoverRequest / TagsRequest | imageId合法；tagIds不得重复、每组最多一个 |
| LoginRequest / LoginResponse | username/password；accessToken/expiresAt；不提供样例真实令牌 |
| TagGroup / Tag | 名称、ID、排序、子数组 |
| Problem | title/status/errors；500不得泄露SQL与内部堆栈 |

**required 与 nullable 是两回事**：必填意味着字段必须出现，可空意味着出现后可以是 null。OpenAPI 3.0 用 `nullable: true`；不要混入 3.1 的类型联合语法。拍摄墙上时间没有时区，不能随意标成 RFC 3339 `date-time`；真正的 token expiresAt 使用带 Z 的 date-time。规范参考：[OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3.html)。

用 IDE 的 YAML/OpenAPI 校验检查缩进、重复键、悬空 `$ref`，再对照实际响应。文档语法有效不代表接口实现正确，两项都要查。

### 5.1 不知道从哪里写时，先保存这个无悬空引用的最小契约

下面是**只含登录接口的完整OpenAPI文件**，用于练习语法；它不是当天最终的12接口交付。你先保存到 `Docs/openapi.yaml`，通过IDE校验，再在同一份文件内扩展。

```yaml
openapi: 3.0.3
info:
  title: QuePhoto API 学习稿
  version: 0.1.0
servers:
  - url: http://127.0.0.1:8080
paths:
  /api/auth/login:
    post:
      summary: 管理员登录
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        '200':
          description: 登录成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        '401':
          description: 用户名或密码错误
components:
  schemas:
    LoginRequest:
      type: object
      required: [username, password]
      properties:
        username:
          type: string
        password:
          type: string
    LoginResponse:
      type: object
      required: [accessToken, expiresAt]
      properties:
        accessToken:
          type: string
        expiresAt:
          type: string
          format: date-time
```

按这个顺序扩展：

1. 看 `AuthDtos`，补用户名/密码限制及400、401、429错误结构；密码72字节规则用文字说明，不能简单写成72字符。
2. 加公开列表：先看 `PageResponse` 与 `PublicPortfolioDtos.ListItem`，逐项写items/page/pageSize/totalCount。
3. 加公开详情和标签树：数组的元素单独建schema，`$ref`指向它。
4. 加管理查询和写操作：定义Bearer安全方案，只在受保护操作上引用；公开接口和登录保持可匿名调用。
5. 完整写六字段PUT请求：required表示字段必须出现；nullable表示允许显式null，两者能同时成立。
6. 对照第3节表格数出12个method+path；再实际发送请求核对响应。一个路径有GET和PUT时算两个操作。

这一步是在写别人能够照着调用的说明书。不要把Java类名当schema内容，也不要写一个不存在的成功返回体。[OpenAPI 3.0.3规范](https://spec.openapis.org/oas/v3.0.3.html)


## 6. 自动化测试准备：永远先隔离数据库

### 加测试依赖

在现有 pom 的 dependencies 中确认包含以下**局部依赖**，不要重复添加已有项目：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Boot 4 的 MockMvc 自动配置包名与不少旧教程不同，使用下方示例的 `org.springframework.boot.webmvc.test.autoconfigure`。测试依赖版本交给 Boot 管理，不手工拼一套 JUnit/Mockito 版本。

### 专用库和配置

在 MySQL 中新建 `quephoto_test`，使用只对该测试库有权限的账号；不要把测试指向 `quephoto_dev` 或生产库。连接后先 `SELECT DATABASE()`，确认是测试库。

创建 `D:\Project\QuePhoto_java\src\test\resources\application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/quephoto_test?connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true
    username: ${TEST_DB_USERNAME}
    password: ${TEST_DB_PASSWORD}
  flyway:
    enabled: true
oss:
  base-url: https://images.example.com
```

本文件不包含真实 OSS 权限：普通 Web 查询只生成 URL，OSS HEAD 客户端由 import 模式单独启用。测试只激活 `test`，不要同时激活 `local` 或 `prod`。

PowerShell 中设置账号并通过交互输入测试数据库密码，避免把密码字面量留进命令历史：

```powershell
Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$env:TEST_DB_USERNAME = 'quephoto_test'
$testPassword = Read-Host '测试数据库密码' -AsSecureString
$env:TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $testPassword).Password
```

第一行只清除当前终端沿用的 profile 环境变量，不修改任何配置文件；测试类用 `@ActiveProfiles("test")` 明确选择测试环境。之后人工启动本地 Web 应用时，仍显式指定 `local`。

下面测试会产生固定 ID 的数据，要求测试库除迁移历史/结构外没有业务数据。用 `@Transactional` 让每个测试结束自动回滚；不靠测试执行顺序清理状态。

## 7. 写一份真正检查公开隔离的测试

目标文件：`D:\Project\QuePhoto_java\src\test\java\com\quephoto\PortfolioApiTest.java`。

先查看 Initializr 生成的 `QuephotoApplicationTests.java`：如果它只有一个空的 `contextLoads()`，用下面这份测试替代这个空模板，避免全量测试时它另开一个没有 `test` 配置的上下文。若你已在那个文件里写了实际测试，应保留它们，并统一使用专用测试库和测试凭据；不要为了让构建通过而删掉失败的业务测试。下面的 `@SpringBootTest` 本身也验证了应用上下文能否启动。

以下是**完整测试类示例**，以本教程约定的五表/字段、配置项和 API 为前提；先核对你前四天实现是否一致，再编译。它加载真实 Spring 与 MySQL，未连接实际 OSS。

```java
package com.quephoto;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortfolioApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void testCredentials(DynamicPropertyRegistry registry) {
        // 明确的测试凭据，绝不能用于部署。
        registry.add("admin.username", () -> "test-admin");
        registry.add("admin.password-hash", () ->
            new BCryptPasswordEncoder().encode("test-only-password"));
        registry.add("jwt.secret", () ->
            Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("jwt.issuer", () -> "quephoto-test");
        registry.add("jwt.audience", () -> "quephoto-test-api");
    }

    @BeforeEach
    void fixtures() {
        jdbc.update("""
            INSERT INTO portfolio
              (id,title,shot_at,shot_time_precision,status,created_at,updated_at)
            VALUES
              (9101,'published-date','2026-10-01 00:00:00','day','published',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
              (9102,'published-unknown',NULL,NULL,'published',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)),
              (9103,'draft-hidden',NULL,NULL,'draft',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """);
        jdbc.update("""
            INSERT INTO portfolio_image
              (id,portfolio_id,image_type,object_key,sort_order,created_at)
            VALUES
              (9201,9101,'work','photos/test/9101.jpg',10,UTC_TIMESTAMP(3)),
              (9202,9102,'work','photos/test/9102.jpg',10,UTC_TIMESTAMP(3))
            """);
        jdbc.update("UPDATE portfolio SET cover_image_id=9201 WHERE id=9101");
        jdbc.update("UPDATE portfolio SET cover_image_id=9202 WHERE id=9102");
    }

    @Test
    void adminRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/portfolios"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicListContainsOnlyPublishedAndSortsUnknownLast() throws Exception {
        mvc.perform(get("/api/portfolios")
                .param("page", "1").param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value(9101))
            .andExpect(jsonPath("$.items[1].id").value(9102));
    }

    @Test
    void publicDraftDetailIsNotFound() throws Exception {
        mvc.perform(get("/api/portfolios/9103"))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidPageSizeIsRejected() throws Exception {
        mvc.perform(get("/api/portfolios").param("pageSize", "51"))
            .andExpect(status().isBadRequest());
    }
}
```

为什么不用随便拿生产数据测？测试必须拥有可解释的输入，才能对 `totalCount=2` 作确定判断。这里固定 ID 只存在专用测试库，每个测试回滚后消失；正式应用仍用自增 ID。

为什么插入 SQL 里先出现 published、后设置封面？这是构造测试夹具，不是业务发布接口；数据库外键只能检查存在性，业务状态校验仍必须由 Service 完成。验证 Service 发布行为需要下一节的真实请求测试。

运行位置为 Windows 工程根目录：

```powershell
Set-Location 'D:\Project\QuePhoto_java'
.\mvnw.cmd '-Dtest=PortfolioApiTest' test
```

预期四个测试通过。失败时查看 `target/surefire-reports`，先区分“应用启动失败”还是“断言失败”；不要第一反应就是改 expected 值。

### 7.1 在同一个测试类里补一个“真实登录→业务拒绝”的方法

第7节的类证明了匿名拒绝和公开隔离，但尚未证明正确的账号能够登录。打开同一个 `PortfolioApiTest.java`，先补入以下import和字段：

```java
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.junit.jupiter.api.Assertions.assertEquals;
```

字段放在类中，与 `mvc`、`jdbc` 平级：

```java
@Autowired JsonMapper json;
```

再把这个**完整测试方法**放进类的最后一个 `}` 之前：

```java
@Test
void realLoginCanReadDraftButCannotPublishWithoutCover() throws Exception {
    String loginBody = mvc.perform(post("/api/auth/login")
            .contentType("application/json")
            .content("{\"username\":\"test-admin\",\"password\":\"test-only-password\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").exists())
        .andReturn().getResponse().getContentAsString();

    String token = json.readTree(loginBody).get("accessToken").asText();
    String authorization = "Bearer " + token;

    mvc.perform(get("/api/admin/portfolios/9103").header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"));

    mvc.perform(put("/api/admin/portfolios/9103")
            .header("Authorization", authorization)
            .contentType("application/json")
            .content("""
                {"title":"draft-hidden","description":null,"location":null,
                 "shotAt":null,"shotTimePrecision":null,"status":"published"}
                """))
        .andExpect(status().isBadRequest());

    assertEquals("draft", jdbc.queryForObject(
            "SELECT status FROM portfolio WHERE id=9103", String.class));
}
```

为什么用9103？它由本类 `fixtures()` 创建，明确是没有图片和封面的草稿，所以“发布必须失败”是确定的。为什么还查数据库？只断言400不能说明数据未被误改。这个用例验证发布条件和拒绝后的状态；要证明“插入一半后整批回滚”，还要做第8节的导入失败测试。

**你接下来按同样结构写两个方法：**

1. 已发布作品9101的封面9201改成scene：请求400；查询9201仍为work。
2. 9101设9202为封面：9202属于9102，请求400；查询9101的cover_image_id仍为9201。

每个方法先写请求，再写状态码断言，最后写数据库断言。字段名与 Day04 DTO 一致。不要直接复制上面的方法而只改测试名。

正确登录会消耗真实登录限流次数。当前工程是**进程内全部登录尝试合计每分钟5次**；当你扩展到多个用例时，可在同一测试类 `@BeforeAll` 登录一次、保存token，结合 `@TestInstance(PER_CLASS)`，或隔离测试上下文。不要把偶发429误当业务断言失败，更不要为通过测试删除生产限流逻辑。


## 8. 再补这些关键场景，才算覆盖本周风险

下表是**要添加的测试步骤**，不是已经写好的测试。先做前四项，再完成其余与 Day 4 新逻辑直接相关的项。

| 场景 | 如何构造与验证 | 为什么需要 |
|---|---|---|
| 真 JWT 成功链路 | POST登录得到真实token→放入Bearer→GET管理草稿200；不打印token | 防止只用模拟认证而跳过实际解码 |
| 过期/错误签名/issuer/audience | 用测试密钥及真实编码器分别生成只改变一个条件的token，断言401 | 每次只改变一个变量，才能定位验证点 |
| 无封面发布 | 登录→POST草稿→PUT完整字段status=published，断言400；DB仍draft | 防止状态变化早于校验 |
| 跨作品/scene封面 | 创建另一作品和scene图片，用其ID设当前封面，断言400且旧值不变 | 外键本身不能表达这项规则 |
| 同组标签冲突 | 建1组2标签，PUT两个ID，断言400且旧关联不变；空数组可清空 | 业务与组合主键都要验证 |
| 缺字段与null | 完整PUT显式null成功；省略必需字段400 | 防止前端遗漏字段导致静默清空 |
| 导入整批失败 | fake OSS元数据核验器让第二张失败，调用Spring管理的导入服务；比对前后图片数 | 验证失败不留下半批内容 |
| 导入重试 | 相同清单执行两次，ID和数量一致；改属性则冲突 | 网络/进程重试是正常运营场景 |
| DB约束 | 在测试事务中直接插同组双标签、错误组合外键或一边null的宽高，断言失败 | 确认MySQL8.4约束确实生效 |

对导入事务做自动化时，不要让测试方法自身的 `@Transactional` 隐藏“生产 Service 根本没开事务”的问题：单独建一个无测试外层事务的测试，调用真实 Spring Service，在失败后用新查询验证零部分写入，再清理测试数据。网络 HEAD 可用 fake 替换；生产用的事务与 Mapper 不能全部 mock 掉。

更严格的并发锁测试可随后补充；至少检查发布/图片修改/设封面/导入都遵循同一作品行锁顺序，避免某条写路径绕过约束。

单元测试方面，亲手给时间规范化写两例：year → 1月1日零点；未知时间+非空precision → 拒绝。只测试公开业务行为，不给每个 getter 写测试。

## 9. 常见失败的定位顺序

| 现象 | 优先原因 | 处理 |
|---|---|---|
| `AutoConfigureMockMvc` 找不到 | 用了旧包名或少了Boot4测试starter | 核对导入和pom，不随意降级整套依赖 |
| 测试连不上MySQL | TEST_DB变量、库名或账号权限错 | 先用客户端连接quephoto_test再跑Spring |
| totalCount不是2 | 测试库残留真实业务数据 | 检查库名和夹具；不要把预期改成当前脏数据数量 |
| 管理接口变403 | CSRF/角色映射与Bearer约定不一致 | 看安全链配置，别关闭所有过滤器来躲避 |
| expired token仍200 | 测试模拟jwt跳过解码或验证器被覆盖 | 使用真实编码器/Authorization header重新测 |
| 幂等导入仍新增 | 只靠Java预查，没有DB唯一键 | 核对object_key唯一约束与冲突处理 |
| 测试通过真实使用仍错 | Mock范围过大或缺真实环境验证 | D6保留实际MySQL/OSS/网络演练 |

查看请求或日志时只记录状态码、路径和必要字段，不记录 Authorization 和密码。截图失败响应前先去掉敏感信息。

## 10. 为服务器准备少量真实标签，而不是演示作品

Day 2 的样例 SQL 只用于本地练习，不能带着样例作品在生产启动时执行。P0 又没有标签写 API，因此今天需要明确一份**可进入生产的初始标签目录**；它是有意选择的业务配置，不是摄影作品测试数据。

先由你确认首批名称，例如“风格：纪实、人像”。下例写入计划文件 `D:\Project\QuePhoto_java\src\main\resources\db\migration\V2__initial_tag_catalog.sql`，正式应用过后只能追加新迁移，不能重改 V2：

```sql
INSERT INTO tag_group (name, sort_order, created_at)
SELECT '风格', 10, UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM tag_group WHERE name = '风格');

INSERT INTO tag (group_id, name, sort_order, created_at)
SELECT g.id, '纪实', 10, UTC_TIMESTAMP(3)
FROM tag_group g
WHERE g.name = '风格'
  AND NOT EXISTS (SELECT 1 FROM tag t WHERE t.group_id = g.id AND t.name = '纪实');

INSERT INTO tag (group_id, name, sort_order, created_at)
SELECT g.id, '人像', 20, UTC_TIMESTAMP(3)
FROM tag_group g
WHERE g.name = '风格'
  AND NOT EXISTS (SELECT 1 FROM tag t WHERE t.group_id = g.id AND t.name = '人像');
```

为什么不用固定 ID：本地样例可能已有同名标签，不同数据库的自增序列也不同。上面的迁移按唯一业务名称找到组，仅插入缺少的条目；既有同名标签不会被覆盖。运行后使用标签树 API 取得实际 ID，不把教程里的 12/25 当成每个环境通用的 ID。

在本地空库与已有 Day 2 样例的开发库各验证一次 V2：标签目录存在、不重复、作品数量不变。测试库也会自动应用这份迁移，但本文 PortfolioApiTest 的作品计数不受影响。服务器仍然从零作品开始，Day 6/7 通过正常流程录入真实作品。

## 11. 把今天成果留给未来前端和明天的自己

`D:\Project\QuePhoto_java\README.md` 至少包含：JDK/MySQL版本、构建/启动命令、配置项说明、链接到OpenAPI/请求集合、P0延期功能。

`D:\Project\QuePhoto_java\docs\operations.md` 至少包含：创建草稿、图片控制台上传、清单导入、发布/下架顺序；重复导入怎么处理；公开图URL并不提供草稿保密；服务器部署部分留待D6填入实际值。

- [ ] 12 个接口的路径、状态码和返回体一致。
- [ ] OpenAPI 没有悬空引用和“只有description”的关键响应。
- [ ] 请求集合可以从零重放完整内容维护流程。
- [ ] 使用真实 JWT 检查了成功、失败、过期等关键分支。
- [ ] MySQL 约束、发布校验、导入事务和幂等有结果记录。
- [ ] 测试没有访问生产数据库、改动现有小程序或删除 OSS 对象。
- [ ] `.\mvnw.cmd test` 后 `.\mvnw.cmd package` 成功，失败项已修复或明确记录。

闭卷练习：有人说“我把pageSize改成500也能拿到数据，接口更灵活了”。请指出它违反哪条契约，补一个失败用例，再修到返回400。另一题：如果只验证401请求却没有成功登录测试，是否能证明鉴权链完整？答案：不能，它可能只是在拒绝所有人。

明天部署的是今天已经验证的产物和契约。P0没有通过时，先修复，不把额外空闲时间投入P1上传功能。

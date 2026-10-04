# Day 04：图片导入与作品发布

今天完成最重要的业务闭环：管理员在 OSS 控制台上传图片 → 本地清单核验并登记 → 选择封面/标签 → API 发布 → 公开查询 → 下架。做完后，一套真实摄影作品已经能由 Java 后端维护。

本文只指导你实现代码；当前 Markdown 中的示例不是已经可运行的功能。所有“局部”片段需要按步骤补充构造器、类型和现有项目异常类。项目根固定为 `D:\Project\QuePhoto_java`，包为 `com.quephoto`，依赖继续沿用 Java 21 / Boot 4.0.x / MyBatis 4.0.x / MySQL 8.4。

## 0. 今天从哪里开始：按“文件 → 方法 → 验证”动手

**2026-10-04 补充：下面是给你自己实施的任务说明。代码块是教学参考，不代表源码已经修改、功能已经完成。** 建议一次只做下面一行，验证通过再做下一行。

对当前工程的检查显示：`ImportManifest`、OSS 配置和 HEAD 核验类已写入工程；`ImageImportCoordinator` 正在写校验；管理员列表和创建草稿已有代码，管理详情与编辑方法仍需补齐。这里描述文件内容，不代替运行验收。

| 顺序 | 打开/新建什么 | 具体写什么 | 停下来检查什么 |
|---|---|---|---|
| A | `importing/ImageImportCoordinator.java` | 先只写 `validateManifest`：基础字段、宽高、重复Key、前缀、封面 | 手工走两个图片的例子；封面在第2张也能通过 |
| B | `portfolio/PortfolioMapper.java` 与 `resources/mapper/PortfolioMapper.xml` | 增加锁作品、按Key查图片、插图、查图片、改封面的方法及SQL | 方法名等于XML的id，参数名一致 |
| C | 新建 `importing/ImageImportService.java` | 在一个事务中锁作品、判断draft、复用或插入、最后设置封面 | 抛异常会离开方法；没有catch后继续成功的路径 |
| D | 回到 Coordinator | 构造器注入核验器和Service；增加“校验→所有HEAD→事务方法”的调用 | HEAD循环结束后才能开始事务 |
| E | 新建 `importing/ImportRunner.java`，修改启动类 | 读JSON、调用Coordinator、输出摘要、保存退出码、关闭上下文 | 打包后命令执行一次即退出 |
| F | DTO、PortfolioService、AdminPortfolioController | 补管理详情/编辑，再写封面、图片、标签、发布规则 | 先带token查到管理详情，再测试修改 |
| G | `.local/import-images.json` 和HTTP请求 | 用真实图片导入、发布、下架，记录失败输入 | 状态码、图片数量、封面ID和公开可见性都符合预期 |

表里的 Java 相对路径以 `src/main/java/com/quephoto/` 为起点；`resources/` 以 `src/main/` 为起点。**今天继续修改同一个工程，不新建第二个 Spring Boot 项目。**

读代码时先回答三个问题：这个方法接收什么？成功返回什么？在哪些条件下抛异常？暂时不熟的语法在下面逐项解释。每组相互依赖的类补齐后，在项目根 PowerShell 执行 `./mvnw.cmd -DskipTests compile`，看到 `BUILD SUCCESS` 再进入下一组；编译通过还不代表SQL或真实OSS已验证。

### 0.1 先分清一次导入中谁负责什么

```text
JSON文件 → Runner读成ImportManifest
         → Coordinator检查输入、逐张HEAD
         → Service开启事务
         → Mapper执行SQL
         → MySQL提交成功
         → Runner输出摘要并退出
```

`Controller` 是 HTTP 入口，`Runner` 是命令入口，两者都应调用业务方法。`Row` 是数据库查询结果；`DTO/record` 是输入或输出结构；不要因为都含有图片字段就混用。

### 0.2 第3天的缺项怎样接到今天

如果 `/api/admin/portfolios/{id}` 尚未实现，先按 Day03 的管理详情部分补齐，再进行本篇第7节。至少要有：

1. Mapper 按ID读取作品，包含 `status/coverImageId/createdAt/updatedAt`，并取得封面Key。
2. Service 查图片和标签，构造**管理员详情DTO**；作品不存在时抛404。
3. Controller 增加 `@GetMapping("/{id}")`，调用Service。
4. 同一路径增加元数据PUT，先锁作品，完整替换六个可编辑字段；本篇第7节再加入发布条件。

不要看到404就立即改数据库：先分清是路由尚未写、运行的是旧代码，还是该ID的作品确实不存在。

### 0.3 本文的代码怎么粘贴

- **完整文件**：按给出的package和类名新建文件，包含import、构造器、方法。
- **类内片段**：放到类的最后一个 `}` 之前，与其他方法平级；不要塞进另一个方法。
- **方法替换**：替换原同名方法，避免两个相同签名的方法导致编译失败。
- **XML片段**：放在现有 `<mapper>...</mapper>` 内，保留原查询语句，不另建第二个相同namespace的映射。

本篇后续代码采用一组统一名称：`findByIdForUpdate`、`findImageByObjectKey`、`insertImage`、`findImageById`、`setCover`、`applyVerifiedManifest`。若你已采用其他名称，可以保留，但接口、SQL、调用处必须一起对应。


## 1. 前置条件、范围与时间

先通过 Day 03：登录成功、后台受保护、草稿可创建/编辑、公开草稿返回 404。准备至少两张自己有权使用的真实图片、一套草稿 ID、本人确认的 OSS Bucket/region/图片域名。没有真实 OSS 凭据可以先写代码和隔离测试，但今天的真实图片闭环不能勾为完成。

| 时间 | 任务 | 学习重点 |
|---|---|---|
| 0:00–0:30 | 准备对象和清单、理解边界 | JSON、集合、SDK |
| 0:30–1:15 | OSS HEAD 与非 Web 启动 | 配置、异常、资源释放 |
| 1:15–2:15 | 事务导入与幂等 | 代理、事务、唯一约束 |
| 2:15–3:00 | 封面/图片/标签/发布 | 业务不变量、行锁 |
| 3:00–3:30 | 真实请求与失败重试 | 原子性与验证 |
| 3:30–4:00 | 修复、练习、记录 | 调试与复盘 |

今天是全周最紧的一天。只有 3 小时时，先完成“单张 work 图片导入 → 发布 → 下架”，标签替换、图片更新及相应验证标成顺延；这些仍属于 P0，须在 Day 05 之前补齐，不能悄悄删掉。没有额外时间就顺延完整 MVP 日期，不挤掉权限或事务验证。

本周不做浏览器上传页面、在线签名直传、任意 Key 的 HTTP 登记、文件覆盖或删除。OSS 上传使用本人控制台权限；Java 后端只核验对象，不负责上传/删除。

## 2. 第一步：看懂三份数据，再准备图片

**做什么：**区分图片二进制、数据库记录、公开 URL，避免以为“写一条 SQL 就上传了照片”。

**怎么做：**先写出下面的对应关系，在 OSS 控制台核对真实值。

| 东西 | 例子 | 放在哪里 |
|---|---|---|
| 图片文件 | 相机导出的 JPEG | OSS 对象存储 |
| ObjectKey | `photos/2026/10/uuid-a.jpg` | OSS 对象标识，DB中登记 |
| 数据库图片行 | imageId、portfolioId、type、order | MySQL `portfolio_image` |
| 图片 URL | 配置域名 + 正确编码的 Key | 响应 DTO 动态生成 |

新照片先在本机另存副本并取唯一文件名；可以执行 `[guid]::NewGuid().ToString()` 生成文件名部分，然后在控制台上传到 `photos/2026/10/`。不要覆盖同名对象，保留相机原图。控制台中核对 Key、Content-Type、大小和 region，不能把带域名的 URL 当 Key。

草稿仅保证元数据不公开：如果 Bucket/图片域名允许公开读，知道 URL 的人仍能访问图片。这个 MVP 不改变已有 Bucket ACL；需要草稿图片保密时，后续另做私有对象与签名读取。不要为首周重构把已上线小程序的图片突然改成私有。

创建示例文件 `D:\Project\QuePhoto_java\examples\import-images.example.json`，只放占位 Key，允许提交 Git：

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

复制为实际清单 `D:\Project\QuePhoto_java\.local\import-images.json`，该目录加入 `.gitignore`。把 portfolioId 换成 Day 03 API 返回的 ID，把 Key 换成控制台实际值。width/height 不知道就都写 null，不伪造尺寸。

**为什么：**上传与登记是两个独立动作。登记失败可能留下 OSS 文件；本周通过清单记录再人工核对，绝不自动删除旧 Bucket 的对象。

**应看到的结果：**两张真实图片存在，清单里的作品 ID 对应草稿；封面指定其中一张 work 图片，所有 Key 大小写与控制台完全一致。

## 3. 第二步：建立文件职责与输入校验

**做什么：**让离线命令复用业务规则，而不是把 SQL 散落在脚本里。

**怎么做：**在现有项目创建下面的类；不再建第二套 Entity、Mapper 或公开 DTO。

| 文件绝对路径 | 要实现的职责 |
|---|---|
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\importing\ImportManifest.java` | 清单 record 与输入结构 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\importing\ImportRunner.java` | 读文件、输出摘要、退出码 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\importing\ImageImportCoordinator.java` | 清单校验与事务前 HEAD |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\importing\ImageImportService.java` | 事务内锁定、幂等、插入、设封面 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\oss\OssImportProperties.java` | 导入专用配置 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\oss\OssImportConfig.java` | 官方 SDK Bean，仅 import profile |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\oss\OssObjectVerifier.java` | 只读 HEAD 核验 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\PortfolioMapper.java` | 追加图片查写、封面与标签 SQL |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\PortfolioService.java` | 后台管理规则及发布 |
| `D:\Project\QuePhoto_java\src\main\resources\application-import.yml` | 导入专用配置（如需） |
| `D:\Project\QuePhoto_java\docs\evidence\day04.md` | 成功、失败、重试证据 |

完整 `ImportManifest.java` 示例：

```java
package com.quephoto.importing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ImportManifest(
    @NotNull @Positive @Max(9007199254740991L) Long portfolioId,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid ImageItem> images,
    @NotBlank @Size(max = 500) String coverObjectKey
) {
    public record ImageItem(
        @NotBlank @Size(max = 500) String objectKey,
        @NotBlank String imageType,
        @NotNull Integer sortOrder,
        Integer width,
        Integer height
    ) {}
}
```

注意：从文件反序列化没有 Controller 的 `@Valid` 自动入口。Coordinator 注入 `jakarta.validation.Validator`，显式 `validator.validate(manifest)`，有 violation 就汇总错误并停止；否则这些注解只是装饰。

继续补业务校验，全部在 HEAD 和事务之前完成：

1. 清单文件最多 1 MiB，每批 1–100 张，这是本次 CLI 的操作边界，写进 operations。
2. `imageType` 只允许小写 work/scene；尺寸两者皆 null 或皆为正整数；sortOrder 是整数。
3. Key 长度≤500，必须以配置 `oss.allowed-prefix` 开头（默认 `photos/`，前缀以 `/` 结尾）；拒绝 URL、开头 `/`、反斜杠、控制字符及独立 `.`/`..` 路径段。
4. Key 是不透明且大小写敏感的 OSS 标识，不对它 `toLowerCase()`、URLDecode 或 `Path.normalize()`；禁止通过规范化把非法输入变成另一个对象。
5. 用 `HashSet<String>` 检测重复 Key，发现即失败。封面 Key 必须位于本清单，且该项 imageType=work。
6. 数据库可先做一次只读检查：作品存在且 draft；但这只是早报错，事务内仍须锁住再检查。

**为什么：**JSON 是输入，不是可信对象。静态校验先做能避免无意义网络请求；重复 Key 校验保证幂等语义可以清楚定义。

**应看到的结果：**错误 JSON、重复 Key、跨前缀 Key、scene 封面清单都会在写库前失败；还没有任何 HTTP 图片登记 API。

### 3.1 把“补业务校验”拆成具体代码

**你要改的文件：**`src/main/java/com/quephoto/importing/ImageImportCoordinator.java`。当前类已有 `Validator validator` 和 `String allowPrefix` 两个字段。先保留构造器；用下面两个完整方法替换同名方法。需要的import：

```java
import java.util.HashSet;
import java.util.stream.Collectors;
```

```java
public void validateManifest(ImportManifest manifest) {
    if (manifest == null) {
        throw new IllegalArgumentException("清单不能为null");
    }
    var violations = validator.validate(manifest);
    if (!violations.isEmpty()) {
        String message = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted().collect(Collectors.joining("; "));
        throw new IllegalArgumentException(message);
    }

    var seenKeys = new HashSet<String>();
    ImportManifest.ImageItem coverItem = null;
    for (var item : manifest.images()) {
        validateObjectKey(item.objectKey(), "objectKey");
        if (!"work".equals(item.imageType()) && !"scene".equals(item.imageType())) {
            throw new IllegalArgumentException("imageType只允许work或scene");
        }
        Integer width = item.width();
        Integer height = item.height();
        if ((width == null) != (height == null)) {
            throw new IllegalArgumentException("宽高必须同时为空或同时填写");
        }
        if (width != null && (width <= 0 || height <= 0)) {
            throw new IllegalArgumentException("宽高必须为正整数");
        }
        if (!seenKeys.add(item.objectKey())) {
            throw new IllegalArgumentException("清单中存在重复ObjectKey");
        }
        if (item.objectKey().equals(manifest.coverObjectKey())) {
            coverItem = item;
        }
    }
    // 注意：这部分在for循环外，否则第2张作为封面时会被提前拒绝。
    validateObjectKey(manifest.coverObjectKey(), "coverObjectKey");
    if (coverItem == null || !"work".equals(coverItem.imageType())) {
        throw new IllegalArgumentException("封面必须是本清单的一张work图片");
    }
}

private void validateObjectKey(String key, String field) {
    if (key.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")
            || key.startsWith("/") || key.contains("\\")
            || key.codePoints().anyMatch(Character::isISOControl)
            || !key.startsWith(allowPrefix)) {
        throw new IllegalArgumentException(field + "格式或允许前缀不正确");
    }
    for (String segment : key.split("/", -1)) {
        if (segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException(field + "不能包含.或..路径段");
        }
    }
}
```

**逐行理解这几个容易混淆的点：**

| 写法 | 用普通话解释 | 例子 |
|---|---|---|
| `(width == null) != (height == null)` | 只有一个为空才报错 | null/null通过；800/null失败 |
| `width != null && ...` | 已确认两个值同时存在后，才比较是否为正数 | 防止对null进行整数比较 |
| `!seenKeys.add(key)` | 已经见过这个Key | 首次add返回true，再次返回false |
| `coverItem = item` | 暂时记住找到的封面 | 不是立刻写数据库 |
| 循环后才检查封面 | 看完所有图片再判断是否找到了 | 封面可以排在第2张 |
| `throw` | 当前操作失败，停止继续执行 | 不能只打印错误然后继续HEAD |

**亲手验证：**依次构造“宽高都null”“只有一个null”“两张重复Key”“封面在第2张”“photos/../a.jpg”五份输入。先预测通过或失败，再执行；第5天将把这些情况写成单元测试。不要用真实OSS来调试纯输入校验。


## 4. 第三步：用 OSS 官方 SDK 做只读 HEAD

**做什么：**验证每个对象真实存在、类型和大小符合约定，不下载整张图片，也不调用 PutObject/DeleteObject。

**怎么做：**本教程明确使用 OSS Java SDK V1 的 `com.aliyun.oss` API，固定 `3.18.5`；V2 是另一套包/API，今天不混用。版本参考[官方发布记录](https://github.com/aliyun/aliyun-oss-java-sdk/releases/tag/3.18.5)。在 `pom.xml` 增加：

```xml
<dependency>
  <groupId>com.aliyun.oss</groupId>
  <artifactId>aliyun-sdk-oss</artifactId>
  <version>3.18.5</version>
</dependency>
```

V1 SDK 的[官方 Java 9+ 配置说明](https://help.aliyun.com/en/oss/developer-reference/oss-java-sdk/)涉及旧 `javax.xml.bind` 兼容依赖。若第一次真实 HEAD 出现 `javax/xml/bind` 或 `javax/activation` 的 NoClassDefFoundError，按官方说明补 `javax.xml.bind:jaxb-api:2.3.1`、`javax.activation:activation:1.1.1`、`org.glassfish.jaxb:jaxb-runtime:2.3.3` 并记录依赖树；不要把 SDK 需要的 javax 类机械改成 Boot 的 jakarta 类。项目其他 HTTP/Validation 代码仍用 jakarta。先跑真实 HEAD，再锁定实际依赖。

本地 `application-local.yml` 合并以下属性；已有 oss.base-url 就沿用，不重复定义：

```yaml
oss:
  base-url: ${OSS_BASE_URL}
  region: ${OSS_REGION:}
  endpoint: ${OSS_ENDPOINT:}
  bucket: ${OSS_BUCKET:}
  allowed-prefix: ${OSS_ALLOWED_PREFIX:photos/}
  access-key-id: ${OSS_ACCESS_KEY_ID:}
  access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
```

Web 服务只用 base-url 生成 URL，不强制需要 RAM 凭据。`OssImportProperties` 用 `@ConfigurationProperties("oss")` 绑定 region、endpoint、bucket、allowedPrefix、accessKeyId、accessKeySecret，在 import 配置中注册并 `@Validated @NotBlank`；`OssImportConfig` 标 `@Configuration @Profile("import")`。这样 Day 05 普通测试无需连真实 OSS。

凭据必须通过 Spring 属性读取，不能在 SDK 工厂里直接 `System.getenv`。这样本地环境变量与 Day 06 的 `/run/secrets` 配置文件可以共用代码。只读 RAM 权限限定在当前 Bucket/前缀，对象 HEAD 所需的读取权限即可；不授予 Bucket 管理、上传或删除权限。

`OssImportConfig` 类内局部代码，使用官方 V4 构造方式：

```java
@Bean(destroyMethod = "shutdown")
OSS ossClient(OssImportProperties p) {
    var options = new ClientBuilderConfiguration();
    options.setSignatureVersion(SignVersion.V4);
    options.setConnectionTimeout(5000);
    options.setSocketTimeout(10000);
    var credentials = CredentialsProviderFactory.newDefaultCredentialProvider(
            p.accessKeyId(), p.accessKeySecret());
    return OSSClientBuilder.create().endpoint(p.endpoint())
            .region(p.region()).credentialsProvider(credentials)
            .clientConfiguration(options).build();
}
```

类名来自 `com.aliyun.oss.OSS/OSSClientBuilder/ClientBuilderConfiguration`；`SignVersion` 在 `com.aliyun.oss.common.comm`；`CredentialsProviderFactory` 在 `com.aliyun.oss.common.auth`。V4 必须有真实 region，endpoint 用固定 Bucket 所属区域的 HTTPS 地址；不能从清单接收 endpoint 或 Bucket。[官方 SDK 初始化文档](https://help.aliyun.com/en/oss/developer-reference/oss-java-sdk/)

`OssObjectVerifier` 标 `@Component @Profile("import")`，注入 OSS 与配置；核验局部代码：

```java
ObjectMetadata metadata = oss.getObjectMetadata(properties.bucket(), objectKey);
String type = metadata.getContentType();
String mime = type == null ? "" : type.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
long size = metadata.getContentLength();
if (!Set.of("image/jpeg", "image/png", "image/webp").contains(mime)) {
    throw new IllegalArgumentException("图片Content-Type不受支持");
}
if (size <= 0 || size > 30L * 1024 * 1024) {
    throw new IllegalArgumentException("图片大小必须在0到30MiB之间");
}
```

`ObjectMetadata` 来自 `com.aliyun.oss.model`。`getObjectMetadata(bucket,key)` 对应 HeadObject，可读取类型和大小；不要用缺少 Content-Type 信息的简化元数据替代完整检查。[官方元数据说明](https://help.aliyun.com/zh/oss/developer-reference/manage-object-metadata-2)

区分三种失败：不存在（对象/Key错误）、403（凭据/权限/region配置）、网络超时（可重试的外部依赖失败）。向 CLI 输出错误分类与必要的 OSS requestId，不输出 AccessKey、签名或完整请求头；禁止 catch 后返回“成功”。HEAD 确认的是元数据，不证明文件内容一定是安全有效图片；本周上传者为可信管理员。

**为什么：**如果网络请求放进数据库事务，会让锁一直等网络。应先完成所有 HEAD，再开始短事务；OSS 不属于 MySQL 事务，数据库回滚不会撤销已上传对象。

**应看到的结果：**真实 Key HEAD 成功；故意错一个 Key 得到失败分类；没有新增图片行。关闭 Spring 上下文时 SDK 的 shutdown 会释放连接。

## 5. 第四步：实现短事务与可重试导入

**做什么：**一次清单要么全成功，要么不写；同一清单重复执行不多插入图片，也不覆盖已登记属性。

**怎么做：**采用两个 Spring Bean，明确分开网络和事务：

```text
ImportRunner
  → ImageImportCoordinator（读入/校验/逐项HEAD，无事务）
    → 注入的ImageImportService.applyVerifiedManifest（public + @Transactional）
      → 锁作品 → 复核draft → 查Key归属 → 插入/复用 → 设置封面 → 提交
```

Coordinator 与 ImportService 都加 `@Service @Profile("import")`。不要写成同一个对象里的 `this.applyVerifiedManifest(...)`；默认 Spring 事务通过代理进入才生效。[Spring 事务代理说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

在现有 `PortfolioMapper` 增加方法：`findImageByObjectKey`、`insertImage`、`setCover`、`findImageById`。Row 的 imageType 为 String，objectKey 唯一键是大小写敏感；数据库仍使用 Day 02 五表约束。

事务方法的局部教学代码如下。`conflict/badRequest/notFound` 表示项目统一业务异常创建方法，全部用 RuntimeException 子类；`sameRegistration` 按下文定义，不能用 `return true` 占位。

```java
@Transactional
public ImportResult applyVerifiedManifest(ImportManifest manifest) {
    PortfolioRow portfolio = mapper.findByIdForUpdate(manifest.portfolioId());
    if (portfolio == null) throw notFound("作品不存在");
    if (!"draft".equals(portfolio.getStatus())) throw conflict("只允许导入草稿");
    Map<String, Long> imageIds = new LinkedHashMap<>();
    int inserted = 0;
    for (var item : manifest.images()) {
        var existing = mapper.findImageByObjectKey(item.objectKey());
        if (existing != null) {
            if (!existing.getPortfolioId().equals(manifest.portfolioId())
                    || !sameRegistration(existing, item)) {
                throw conflict("ObjectKey已被其他登记占用或属性不同");
            }
            imageIds.put(item.objectKey(), existing.getId());
        } else {
            var row = toImageRow(manifest.portfolioId(), item);
            mapper.insertImage(row); // useGeneratedKeys回填id
            imageIds.put(item.objectKey(), row.getId());
            inserted++;
        }
    }
    Long coverId = imageIds.get(manifest.coverObjectKey());
    var cover = mapper.findImageById(coverId);
    if (cover == null || !cover.getPortfolioId().equals(manifest.portfolioId())
            || !"work".equals(cover.getImageType())) {
        throw badRequest("封面必须属于本作品且为work");
    }
    mapper.setCover(manifest.portfolioId(), coverId, LocalDateTime.now(ZoneOffset.UTC));
    return new ImportResult(manifest.portfolioId(), inserted, imageIds);
}
```

`ImportResult` 可定义为 `record ImportResult(long portfolioId,int insertedCount,Map<String,Long> imageIds)`。`sameRegistration` 要比较 portfolioId、objectKey、imageType、sortOrder、width、height；nullable 字段使用 `Objects.equals`。相同 Key 同作品同属性复用；任一属性不同都报冲突，让操作者显式使用图片更新 API，不能把导入重试变成隐式修改。

`toImageRow` 显式校验小写 work/scene 并存入 String，设置 UTC createdAt；本项目目前没有独立的图片类型枚举，下面给出匹配现有 Row 的实现。Key 在数据库唯一；并发插入冲突不要使用 `INSERT IGNORE`。让重复键异常传播出事务并回滚整个清单，CLI 报冲突，用户修正/重试即可。

即便 Coordinator 已查过 draft，事务内也必须复核：HEAD 期间另一请求可能已发布。同一作品的元数据更新、封面变更、图片更新、标签替换、发布和导入，全部先 `SELECT ... FOR UPDATE` 锁同一 portfolio 行，后续再操作图片/标签，锁顺序保持一致。

失败不能被事务内 `catch` 吞掉后继续 return。若确实使用受检异常，显式配置 rollbackFor；本教程业务异常都用 RuntimeException，外层 Runner 在事务已经退出后统一转退出码。

**为什么：**锁保证“检查成立”和“写入完成”之间没有另一请求改坏前提；唯一键处理不同作品同时登记同一 Key。原子性保证不会留下一半图片、错误封面的数据库状态。

**应看到的结果：**同清单第二次导入 insertedCount=0、图片 ID 一致；清单中一项属性冲突则整个事务回滚；导入不会自动把作品发布。

### 5.1 先把Mapper的声明和SQL配成一对

打开 `src/main/java/com/quephoto/portfolio/PortfolioMapper.java`，在接口内追加；已有同名方法时核对签名，不重复声明：

```java
PortfolioRow findByIdForUpdate(@Param("id") long id);
PortfolioImageRow findImageByObjectKey(@Param("objectKey") String objectKey);
PortfolioImageRow findImageById(@Param("id") long id);
int insertImage(PortfolioImageRow row);
int setCover(@Param("portfolioId") long portfolioId,
             @Param("imageId") long imageId,
             @Param("updatedAt") java.time.LocalDateTime updatedAt);
```

打开 `src/main/resources/mapper/PortfolioMapper.xml`，在 `</mapper>` 前追加以下完整SQL片段：

```xml
<select id="findByIdForUpdate" resultType="com.quephoto.portfolio.PortfolioRow">
    SELECT * FROM portfolio WHERE id=#{id} FOR UPDATE
</select>
<select id="findImageByObjectKey" resultType="com.quephoto.portfolio.PortfolioImageRow">
    SELECT * FROM portfolio_image WHERE object_key=#{objectKey}
</select>
<select id="findImageById" resultType="com.quephoto.portfolio.PortfolioImageRow">
    SELECT * FROM portfolio_image WHERE id=#{id}
</select>
<insert id="insertImage" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO portfolio_image
      (portfolio_id,image_type,object_key,sort_order,width,height,created_at)
    VALUES
      (#{portfolioId},#{imageType},#{objectKey},#{sortOrder},#{width},#{height},#{createdAt})
</insert>
<update id="setCover">
    UPDATE portfolio SET cover_image_id=#{imageId},updated_at=#{updatedAt}
    WHERE id=#{portfolioId}
</update>
```

**为什么这样对应：**方法 `findImageById` 对应 `<select id="findImageById">`；`@Param("id")` 对应 `#{id}`；返回Row对应 `resultType`。`useGeneratedKeys` 将新图片ID写回 `row.id`，否则后面可能拿着null设置封面。

`FOR UPDATE` 需要外层事务维持锁；离开事务就释放。先锁作品，再操作图片和标签，让同一作品的管理操作按相同顺序执行。

### 5.2 把上面的事务片段补成完整类

目标是你自己新建 `src/main/java/com/quephoto/importing/ImageImportService.java`。类壳如下，把上面的 `applyVerifiedManifest` 方法放在注释位置，再加入下面的辅助方法：

```java
package com.quephoto.importing;

import com.quephoto.portfolio.PortfolioMapper;
import com.quephoto.portfolio.PortfolioRow;
import com.quephoto.portfolio.PortfolioImageRow;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

@Service
@Profile("import")
public class ImageImportService {
    private final PortfolioMapper mapper;

    public ImageImportService(PortfolioMapper mapper) { this.mapper = mapper; }

    public record ImportResult(long portfolioId, int insertedCount, Map<String, Long> imageIds) {}

    // 在这里放上面的完整applyVerifiedManifest方法。

    private boolean sameRegistration(PortfolioImageRow row, ImportManifest.ImageItem item) {
        return Objects.equals(row.getObjectKey(), item.objectKey())
                && Objects.equals(row.getImageType(), item.imageType())
                && Objects.equals(row.getSortOrder(), item.sortOrder())
                && Objects.equals(row.getWidth(), item.width())
                && Objects.equals(row.getHeight(), item.height());
    }

    private PortfolioImageRow toImageRow(long portfolioId, ImportManifest.ImageItem item) {
        if (!"work".equals(item.imageType()) && !"scene".equals(item.imageType())) {
            throw badRequest("imageType只允许work或scene");
        }
        var row = new PortfolioImageRow();
        row.setPortfolioId(portfolioId);
        row.setObjectKey(item.objectKey());
        row.setImageType(item.imageType());
        row.setSortOrder(item.sortOrder());
        row.setWidth(item.width());
        row.setHeight(item.height());
        row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return row;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
```

这里沿用项目已有的 `ResponseStatusException`，不需要再猜测 `conflict()` 是哪个库的方法。`Objects.equals` 可以比较两个null；作品归属在外层判断，剩余登记字段由 `sameRegistration` 比较。`toImageRow` 只是给Java对象赋值，执行 `mapper.insertImage(row)` 才真正写库。

**检查点：**先完成类壳、事务方法、辅助方法和Mapper，再编译；这些是同一组依赖，不要为了暂时消除红线把方法改成固定返回true或null。

### 5.3 回到Coordinator接好调用

校验阶段完成后，给 `ImageImportCoordinator` 加 `@Service @Profile("import")`，把原构造器替换成下面的版本；类内保留第3节的两个校验方法。顶部补入 `OssImportProperties`、`OssObjectVerifier`、`Service`、`Profile` 的import：

```java
private final Validator validator;
private final String allowPrefix;
private final OssObjectVerifier verifier;
private final ImageImportService importService;

public ImageImportCoordinator(Validator validator, OssImportProperties properties,
        OssObjectVerifier verifier, ImageImportService importService) {
    this.validator = validator;
    this.allowPrefix = properties.allowedPrefix();
    this.verifier = verifier;
    this.importService = importService;
    if (allowPrefix == null || allowPrefix.isBlank() || !allowPrefix.endsWith("/")) {
        throw new IllegalStateException("oss.allowed-prefix必须非空且以/结尾");
    }
}

public ImageImportService.ImportResult run(ImportManifest manifest) {
    validateManifest(manifest);
    for (var item : manifest.images()) {
        verifier.verify(item.objectKey());
    }
    return importService.applyVerifiedManifest(manifest);
}
```

已有字段不要再声明一遍。构造器参数由Spring注入；不要保留一个无法自动注入普通String的旧构造器。`run` 本身不加事务，所有HEAD成功才进入另一个Bean的事务方法。


## 6. 第五步：让同一 JAR 以离线模式执行并退出

**做什么：**平时启动只提供 Web API；只有显式 `local,import` 或 `prod,import` 才读指定文件，执行完自动退出。

**怎么做：**`ImportRunner` 使用 `@Component @Profile("import")`，实现 `ApplicationRunner` 和 `ExitCodeGenerator`。用构造器注入 Coordinator、`tools.jackson.databind.json.JsonMapper`、Environment。执行步骤：

1. 检查 `spring.main.web-application-type` 确实为 none；未显式禁用 Web 就拒绝执行，不能带 import profile 继续提供服务。
2. 从配置属性 `import.file` 获取非空绝对路径，只读打开普通 JSON 文件；不用递归扫描目录，不默认读取工作目录。
3. 检查文件大小，`try (InputStream input = Files.newInputStream(path))` 后调用 `jsonMapper.readValue(input, ImportManifest.class)`。
4. 调用 Coordinator。成功输出一行 JSON 摘要并将 exitCode=0；失败输出脱敏分类，将 exitCode=1，输入错误也可以细分为2，记录实际约定。
5. `getExitCode()` 返回保存的值。无论成功失败，都让主函数负责关闭 Spring 上下文与连接池。

### 6.1 新建Runner：下面给出完整文件，按段理解后再写入

目标：`src/main/java/com/quephoto/importing/ImportRunner.java`。先写字段和构造器，再写run，最后写退出码方法。这个例子依赖你已完成第5.3节的Coordinator.run方法。

```java
package com.quephoto.importing;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Profile("import")
public class ImportRunner implements ApplicationRunner, ExitCodeGenerator {
    private static final int MAX_BYTES = 1024 * 1024;
    private final ImageImportCoordinator coordinator;
    private final JsonMapper json;
    private final Environment environment;
    private int exitCode;

    public ImportRunner(ImageImportCoordinator coordinator, JsonMapper json, Environment environment) {
        this.coordinator = coordinator;
        this.json = json;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type"))) {
                throw new IllegalArgumentException("导入必须显式指定 --spring.main.web-application-type=none");
            }
            String file = environment.getProperty("import.file");
            if (file == null || file.isBlank()) {
                throw new IllegalArgumentException("缺少 --import.file=清单路径");
            }
            if (!Path.of(file).isAbsolute() || !Files.isRegularFile(Path.of(file))) {
                throw new IllegalArgumentException("import.file 必须是已有普通文件的绝对路径");
            }
            byte[] bytes;
            try (var input = Files.newInputStream(Path.of(file))) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("清单不能超过 1 MiB");
            var manifest = json.readValue(bytes, ImportManifest.class);
            var result = coordinator.run(manifest);
            System.out.println(json.writeValueAsString(result));
        } catch (OSSException ex) {
            fail(3, "OSS 核验失败 code=" + ex.getErrorCode() + " requestId=" + ex.getRequestId());
        } catch (ClientException ex) {
            fail(3, "OSS 客户端或网络失败，请检查 endpoint、region 和网络后重试");
        } catch (ResponseStatusException ex) {
            fail(2, ex.getStatusCode().value() + " " + ex.getReason());
        } catch (JacksonException ex) {
            fail(2, "JSON 结构错误，请核对字段名称、类型及括号");
        } catch (IOException ex) {
            fail(2, "清单无法读取，请检查路径和文件权限");
        } catch (IllegalArgumentException ex) {
            fail(2, ex.getMessage());
        } catch (DataAccessException ex) {
            fail(4, "数据库操作失败，事务已回滚；检查连接、唯一约束或并发冲突");
        } catch (Exception ex) {
            fail(1, "导入发生未预期错误，类型=" + ex.getClass().getSimpleName());
        }
    }

    private void fail(int code, String message) {
        exitCode = code;
        // 不打印 SDK 请求头、凭据、完整异常对象或连接串。
        System.err.println("IMPORT_FAILED " + message);
    }

    @Override
    public int getExitCode() { return exitCode; }
}
```

读文件的 `try (...)` 会自动关闭流；最多读取1 MiB加1字节，用最后一个字节判断超限。输入异常、OSS异常和数据库异常在外层分类，事务中的异常不被吞掉。例子约定0成功、2输入/业务错误、3 OSS失败、4数据库失败、1其他错误；配置绑定等启动期异常也应非零退出。输出中不包含凭据或完整SDK请求。

### 6.2 再调整main，并核对终端配置

修改 `D:\Project\QuePhoto_java\src\main\java\com\quephoto\QuePhotoApplication.java`，下列为完整 main 方法，保留已有类注解：

```java
public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(QuePhotoApplication.class, args);
    boolean importing = Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("import");
    if (importing) {
        int code = SpringApplication.exit(context);
        System.exit(code);
    }
}
```

上面的main片段还需要在文件顶部补入 `org.springframework.context.ConfigurableApplicationContext` 和 `java.util.Arrays` 的import。

`SpringApplication.run` 会执行 Runner 后才返回；`SpringApplication.exit` 汇总 `ExitCodeGenerator` 并关闭上下文。不要在正常 Web 路径无条件 close；也不要在 Runner 里直接 `System.exit(0)`，否则可能来不及释放 SDK、连接池。[Boot 应用退出文档](https://docs.spring.io/spring-boot/reference/features/spring-application.html)

本地注入只读 RAM 配置时避免把秘密写到历史。以下示例要求**当前导入窗口已经配置** Day03 的数据库/JWT/管理员环境变量；`start-local.cmd` 在另一个进程启动的服务不会把变量传回这个窗口。缺少这些配置时，先按 Day03 配置当前窗口，不要输出全部环境变量排错。`Get-Credential` 的用户名栏输入 AccessKeyId、密码栏输入 Secret：

```powershell
Set-Location 'D:\Project\QuePhoto_java'
$env:OSS_REGION = Read-Host 'OSS Region，例如cn-hangzhou'
$env:OSS_ENDPOINT = Read-Host '对应OSS HTTPS Endpoint'
$env:OSS_BUCKET = Read-Host 'Bucket名'
$env:OSS_BASE_URL = Read-Host '已确认的图片HTTPS基础地址'
$env:OSS_ALLOWED_PREFIX = 'photos/'
$qpOssCred = Get-Credential -Message '只读RAM：用户名填AccessKeyId，密码填Secret'
$env:OSS_ACCESS_KEY_ID = $qpOssCred.UserName
$env:OSS_ACCESS_KEY_SECRET = $qpOssCred.GetNetworkCredential().Password
Remove-Variable qpOssCred
.\mvnw.cmd '-DskipTests' package
if ($LASTEXITCODE -ne 0) { throw '编译或打包失败，停止导入' }
java -jar '.\target\quephoto.jar' '--spring.profiles.active=local,import' '--spring.main.web-application-type=none' '--import.file=D:\Project\QuePhoto_java\.local\import-images.json'
$LASTEXITCODE
```

今天专用测试库尚未配置，先跳过测试执行来生成用于本地人工验收的 JAR；这不代表测试已通过。不能把这个选项带入 Day 06 的发布构建。Day 05 完成隔离测试配置后必须执行完整 `verify`，并把今天的事务失败场景加入验证。

预期最后退出码 0；日志有类似 `{"portfolioId":1001,"insertedCount":2,"imageIds":{"photos/...":2001}}` 的摘要；命令自动回到提示符，没有启动 HTTP 端口。再执行相同导入命令，insertedCount=0。缺 `--import.file` 应非0退出，不读取示例清单。

正常启动只用 `java -jar .\target\quephoto.jar --spring.profiles.active=local`，不应打印导入摘要或新增图片。服务器 Day 06 使用 `prod,import`，配置来自服务器受保护文件，清单只读挂载，在同一 Compose 网络运行；不开放 MySQL 公网端口。

**为什么：**显式一次性命令可审计、可重试，避免生产启动不小心重复导入。非 Web 模式仍复用同一套事务和配置。

**应看到的结果：**普通 Web 启动无导入；离线命令成功/失败有可靠退出码且释放资源；非 Web 模式不报缺少 HttpSecurity。

## 7. 第六步：完成三种管理修改和发布规则

**做什么：**让后台能在合法状态之间移动，而不是允许某个接口破坏另一个接口的承诺。

**怎么做：**在 Day 03 `AdminPortfolioController` 增加三个 PUT，继续通过注入的 `PortfolioService` 调用。各 public 方法都 `@Transactional`，第一步总是 `findByIdForUpdate(portfolioId)`。

| API | 请求 | 成功响应约定 |
|---|---|---|
| `PUT /api/admin/portfolios/{id}/cover` | `{"imageId":2001}` | 200，更新后管理详情 |
| `PUT /api/admin/portfolios/{id}/tags` | `{"tagIds":[12,25]}` | 200，更新后管理详情 |
| `PUT /api/admin/portfolios/{id}/images/{imageId}` | `{"imageType":"work","sortOrder":10,"width":null,"height":null}` | 200，更新后管理员图片 DTO，含 objectKey |

每个请求单独建 record，放在 `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\dto\`，分别命名 `CoverRequest.java`、`TagReplaceRequest.java`、`ImageUpdateRequest.java`。要求字段必须出现；nullable 尺寸允许显式null。ID正数且不超过JS安全整数；tagIds最多100个且元素非null，不接受objectKey。

**设置封面算法：**锁作品 → 查图片 → 图片不存在/不属本作品返回400或404并在契约固定（本教程不存在404，跨作品400）→ 类型非work返回400 → 更新 cover_image_id 与 updated_at → 返回详情。null清封面不支持；要换就换另一张合法 work。

**更新图片算法：**锁作品 → 图片不存在404、归属不符400 → 校验小写type、sortOrder、成对尺寸 → 若它是已发布作品的封面，禁止改为scene → 参数化UPDATE允许的4列 → 更新作品updated_at → 返回更新后的管理员图片 DTO（id、imageType、imageUrl、objectKey、sortOrder、width、height）。objectKey是只读字段，发送该字段直接400；不能“忽略字段然后返回成功”。

**替换标签算法：**锁作品 → `HashSet` 检查重复ID → 按ID批量查tag → 数量不等则有不存在ID，返回400 → 用groupId检测同组多选 → 全部校验通过才删除该作品关联行并插入新集合 → 更新updated_at。客户端不传groupId，由数据库中的tag.group_id决定。空数组合法，删除本作品所有关联。只删除关联行，不删除tag、tag_group或OSS对象。

标签替换方法核心局部片段：

```java
Set<Long> uniqueIds = new HashSet<>(request.tagIds());
if (uniqueIds.size() != request.tagIds().size()) throw badRequest("标签ID重复");
List<TagRow> tags = uniqueIds.isEmpty() ? List.of() : tagMapper.findByIds(uniqueIds);
if (tags.size() != uniqueIds.size()) throw badRequest("包含不存在的标签");
Set<Long> groups = new HashSet<>();
for (TagRow tag : tags) {
    if (!groups.add(tag.getGroupId())) throw badRequest("同一标签组只能选择一个标签");
}
mapper.deletePortfolioTags(portfolioId);
for (TagRow tag : tags) {
    mapper.insertPortfolioTag(portfolioId, tag.getGroupId(), tag.getId());
}
```

空集合时不生成 `IN ()`。批量 SQL 用 MyBatis `<foreach>` 与 `#{id}` 参数绑定，不能把 join 后的字符串直接拼SQL。组合外键与 `(portfolio_id,tag_group_id)` 主键仍是最后防线。上述片段必须位于已经锁作品的事务方法中，别直接贴到 Controller。

**发布/下架算法：**替换 Day 03 元数据更新中“暂拒published”的分支。锁作品后，若请求目标为 published：coverImageId 必须非null；查封面图片并验证属于当前作品且imageType=work；满足后连同完整元数据一次UPDATE。draft无需图片即可保存。每次目标为published都校验，不能只在首次发布检查。

草稿允许临时无合法封面（例如先调整了图片类型），但不能发布；已发布作品任何修改都不能失去合法work封面。需要将当前封面改为scene，先换另一张work为封面，或者先下架再调整。事务提交后公开查询才能看到完整状态。

**为什么：**发布规则是贯穿所有写入口的不变量。“发布接口有检查”不足以防止稍后的图片类型修改破坏封面。

**应看到的结果：**跨作品封面、scene封面、同组标签、已发布封面改scene全部失败且原内容不变；下架后公开详情404。

### 7.1 先写一个最小完整操作：设置封面

**前提：**管理详情的 `AdminPortfolioDtos.Detail` 和 `service.adminDetail(id)` 已按第0.2节补好。下面示范怎样把“需要一个PUT”落到DTO、Controller和Service，不要求你同时写完所有管理接口。

第一步，新建 `src/main/java/com/quephoto/portfolio/dto/CoverRequest.java`，完整文件如下：

```java
package com.quephoto.portfolio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record CoverRequest(
        @NotNull @Positive @Max(9007199254740991L) Long imageId
) {}
```

`Long` 能表达null；`@NotNull` 不允许缺失或null；`@Positive` 要求正数。JSON中的 `imageId` 对应record中的同名字段，record的读取方法叫 `imageId()`。

第二步，在 `AdminPortfolioController` 的类内追加完整方法，并在文件顶部import `CoverRequest`。现有的 `@PutMapping/@PathVariable/@RequestBody/@Valid/@Min/@Max` 按IDE提示补齐import：

```java
@PutMapping("/{id}/cover")
public AdminPortfolioDtos.Detail cover(
        @PathVariable("id") @Min(1) @Max(9007199254740991L) long id,
        @Valid @RequestBody CoverRequest request) {
    return service.setCover(id, request);
}
```

第三步，在 `PortfolioService` 类内追加下面的完整方法。顶部import相同的 `CoverRequest`，其余类型已在当前Service中使用：

```java
@Transactional
public AdminPortfolioDtos.Detail setCover(long id, CoverRequest request) {
    PortfolioRow portfolio = mapper.findByIdForUpdate(id);
    if (portfolio == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在");
    }
    PortfolioImageRow image = mapper.findImageById(request.imageId());
    if (image == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
    }
    if (!java.util.Objects.equals(image.getPortfolioId(), id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不属于当前作品");
    }
    if (!"work".equals(image.getImageType())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面必须是work图片");
    }
    mapper.setCover(id, image.getId(), LocalDateTime.now(ZoneOffset.UTC));
    return adminDetail(id);
}
```

第四步，构建并重启服务，用本人草稿ID和work图片ID发PUT。成功应200、返回详情中的 `coverImageId` 更新；换成其他作品的图片ID应400。这个步骤通过后，再按相同三层结构做图片和标签修改。

### 7.2 另外两个操作怎样拆开写

**图片更新：**先新建完整请求record；下面四个字段必须出现在JSON里，宽高允许显式null。

```java
package com.quephoto.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ImageUpdateRequest(
        @JsonProperty(required = true) @NotBlank @Pattern(regexp = "work|scene") String imageType,
        @JsonProperty(required = true) @NotNull Integer sortOrder,
        @JsonProperty(required = true) Integer width,
        @JsonProperty(required = true) Integer height
) {}
```

在Service中按上面的设封面方法先锁作品、查图片、核对归属。然后复用第3节的宽高判断；只有一边为空或非正数都400。写库前加入这个**类内方法中的局部片段**：

```java
if ("published".equals(portfolio.getStatus())
        && java.util.Objects.equals(portfolio.getCoverImageId(), image.getId())
        && !"work".equals(request.imageType())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已发布封面不能改为scene");
}
```

这里 `portfolio`、`image` 是你刚查出的Row，`request` 是方法参数。之后把请求的四个值写入image，再调用Mapper；SQL只更新 `image_type/sort_order/width/height`，WHERE同时限定 `id` 与 `portfolio_id`。作品的 `updated_at` 也要更新。返回管理员图片DTO，不直接返回整个Row。

**标签替换：**新建 `TagReplaceRequest.java`，完整文件：

```java
package com.quephoto.portfolio.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record TagReplaceRequest(
        @NotNull @Size(max = 100)
        List<@NotNull @Positive @Max(9007199254740991L) Long> tagIds
) {}
```

Service先锁作品，再执行本节前面的标签校验片段。如果你卡在 `tagMapper.findByIds`，在 `TagMapper` 接口增加 `List<TagRow> findByIds(@Param("ids") java.util.Set<Long> ids);`，在其XML内追加：

```xml
<select id="findByIds" resultType="com.quephoto.tag.TagRow">
    SELECT id,group_id,name,sort_order FROM tag
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

`TagMapper` 仍在 `com.quephoto.tag` 包；`@Param` 从 `org.apache.ibatis.annotations.Param` 导入。在PortfolioService新增 `private final TagMapper tagMapper` 并加入**现有构造器**赋值，不同时保留两份构造器。顶部import `TagMapper/TagRow/Set/HashSet/List`。

PortfolioMapper还需声明删除关联和插入关联的方法，参数分别叫 `portfolioId` 以及 `portfolioId/groupId/tagId`，用 `@Param` 明确命名。对应XML片段：

```xml
<delete id="deletePortfolioTags">
    DELETE FROM portfolio_tag WHERE portfolio_id=#{portfolioId}
</delete>
<insert id="insertPortfolioTag">
    INSERT INTO portfolio_tag(portfolio_id,tag_group_id,tag_id)
    VALUES(#{portfolioId},#{groupId},#{tagId})
</insert>
```

空集合时跳过findByIds，但仍删除旧关联；空数组因此是“清空标签”。非空集合先查完、检查完，再删旧数据；同组两个标签失败后，原标签必须还在。整个过程置于同一个 `@Transactional` 方法中，最后返回 `adminDetail(portfolioId)`。

**这一小节的练习：**参照封面Controller，自己补图片和标签的PUT方法。只负责接参数并调Service，把SQL和业务判断留在Service/Mapper。写完逐条验证，不一次发十个请求再找哪条失败。


## 8. 第七步：走一次真实闭环，再主动制造失败

**做什么：**用真实照片证明可用，并证明失败不会损坏数据。

**怎么做：**服务正常 local Web 启动，按 Day 03 安全登录步骤建立 `$qpHeaders`，不要粘贴 token 到脚本文本。下面用真实草稿ID，先读取管理详情，再明确列出 PUT 可编辑字段，不能把整个详情直接PUT（其中有只读字段）。

```powershell
$qpBase = 'http://127.0.0.1:8080'
$qpId = [long](Read-Host '已完成导入的草稿ID')
$qpDetail = Invoke-RestMethod -Uri "$qpBase/api/admin/portfolios/$qpId" -Headers $qpHeaders
$qpWorkId = ($qpDetail.images | Where-Object imageType -eq 'work' | Select-Object -First 1).id
$qpCoverBody = @{ imageId = $qpWorkId } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId/cover" -Headers $qpHeaders -ContentType 'application/json' -Body $qpCoverBody
$qpWrite = @{
  title=$qpDetail.title; description=$qpDetail.description; location=$qpDetail.location
  shotAt=$qpDetail.shotAt; shotTimePrecision=$qpDetail.shotTimePrecision; status='published'
}
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId" -Headers $qpHeaders -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes(($qpWrite | ConvertTo-Json)))
$qpPublic = Invoke-RestMethod -Uri "$qpBase/api/portfolios/$qpId"
$qpPublic | ConvertTo-Json -Depth 8
Invoke-WebRequest -Method Head -Uri $qpPublic.coverImageUrl
```

预期：发布200；公开详情200，有 images/tags，图片type小写，work排在scene前；无objectKey/status/shareImageKey等后台字段；shareImageUrl回退coverImageUrl；真实图片URL可读。若远端不接受HEAD，浏览器打开图片确认，不因此跳过Java使用SDK的HEAD核验。

调用标签树取得实际 ID 后测试替换；不要照抄12/25假设数据库有这些行：

```powershell
Invoke-RestMethod -Uri "$qpBase/api/admin/tag-groups" -Headers $qpHeaders
$qpTagIds = @([long](Read-Host '任选一个实际二级标签ID'))
$qpTagsBody = @{ tagIds = $qpTagIds } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId/tags" -Headers $qpHeaders -ContentType 'application/json' -Body $qpTagsBody
$qpImageBody = @{ imageType='work'; sortOrder=5; width=$null; height=$null } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId/images/$qpWorkId" -Headers $qpHeaders -ContentType 'application/json' -Body $qpImageBody
$qpWrite.status = 'draft'
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId" -Headers $qpHeaders -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes(($qpWrite | ConvertTo-Json)))
curl.exe --silent --output NUL --write-out '%{http_code}' "$qpBase/api/portfolios/$qpId"
```

最后应404；管理员详情仍200，图片数据仍在。测试标签数据来自 Day 02 的隔离本地fixture；生产标签如何初始化在 Day 05/06 的受审 SQL 中明确，本周不临时开放未设计的标签写入接口。

失败实验用专用草稿，不修改真实作品清单原件：

| 实验 | 操作 | 验证结果 |
|---|---|---|
| HEAD预检失败 | 第二张改为不存在Key | CLI非0；该批零写入 |
| 事务中途失败 | 第一项是新Key，第二项用已被另一作品占用Key | 第一项也回滚；封面不变 |
| 幂等重试 | 草稿用完全相同清单执行两次 | 第二次新增0，ID一致 |
| 属性冲突 | 同Key同作品但改变sortOrder | 非0；要求显式图片更新 |
| 已发布作品导入 | 先发布，再运行清单 | 非0；状态/图片不变 |
| 发布与修改冲突 | 已发布work封面改scene | 400；仍保留原work封面 |
| 错误标签组 | 选择同组两个实际ID | 400；旧标签集合不变 |
| 正常启动 | 不带import启动同一JAR | 无自动导入，无新增行 |

用管理详情记录前后图片数和封面ID；数据库只读核对可用 `SELECT COUNT(*) FROM portfolio_image WHERE portfolio_id = ...`。第一种实验只证明预检零写入；第二种才证明数据库事务中途失败的回滚，两种都要做。D5 把后者放进专用测试库自动化，不能在生产人为制造冲突。

HEAD与数据库之间无法阻止OSS控制台被人覆盖对象；本周操作约定是唯一Key、禁止覆盖/删除。它也无法保证未来图片URL永不失效，首周的成功证据要包含真实访问结果。

**为什么：**只有成功路径会掩盖最危险的部分导入。失败实验让你看到“事务”解决什么、不能解决什么。

**应看到的结果：**真实内容闭环与失败矩阵都有实际证据，不能只写“代码看起来没问题”。

## 9. 故障排查与学习练习

| 症状 | 原因方向 | 下一步 |
|---|---|---|
| HEAD403 | RAM权限/region/endpoint错误 | 核对固定配置和requestId，勿直接授予FullAccess |
| HEAD404但浏览器能打开 | URL路径与真实Key不一致，大小写/编码有误 | 从控制台复制Key，不复制整条URL |
| 非Web启动缺HttpSecurity | Day03安全配置未条件化 | 给SecurityConfig加Servlet条件 |
| 导入成功但进程不退出 | main未按import profile退出 | 检查ExitCodeGenerator与context关闭 |
| 只插入了前几张 | 事务被self-invocation绕开或异常被吞 | 分成Coordinator/Service两Bean，让异常传播 |
| 同清单重试冲突 | 登记后通过API改过属性 | 属于预期；更新清单使其反映当前登记 |
| 标签清空SQL语法错 | 空数组生成IN() | 空集合走直接清关联路径 |
| 第一次HEAD报javax类缺失 | Java21移除了SDK V1依赖的旧模块 | 按官方配置补兼容依赖并验证真实HEAD |
| URL能打开草稿图 | 对象本来公开可读 | 这是访问模型，不能声称元数据draft即图片保密 |

闭卷练习：

1. 用一句话解释数据库事务为何不能撤销控制台上传。提示：MySQL只管理自己的数据提交，OSS是另一个系统。
2. 把 `sameRegistration` 亲手写出来，并解释 null 宽高为何用 Objects.equals。提示：null不能调用equals；`Integer ==`也会受引用/缓存影响。
3. 为什么先 HEAD 后加锁，又要锁内重新检查 draft？提示：减少锁等待，但网络期间作品可能变化。
4. 将标签重复和同组多选两个错误分别构造出来。提示：前者是 `[12,12]`，后者是两个不同ID但groupId相同。
5. 解释“同清单重试不新增”和“允许清单覆盖已有属性”为什么不是同一语义。提示：幂等重试应复用既有结果，显式修改必须走明确的更新操作。

- [ ] CLI只在显式import模式执行，缺文件/错配置非0退出。
- [ ] 所有对象HEAD成功之后才进入事务，SDK连接最终释放。
- [ ] 同清单重试新增0；中途数据库冲突整批回滚。
- [ ] 所有写入口统一先锁作品，发布状态在锁内复核。
- [ ] 封面必须本作品work，已发布封面不能被改为scene。
- [ ] 标签替换拒绝重复/同组多选/不存在ID，空数组可以清空。
- [ ] objectKey不能通过图片更新API修改，没有任意Key登记HTTP接口。
- [ ] 真实作品完成导入→发布→读取图片→下架，公开草稿404。
- [ ] 能亲手解释事务代理、幂等、外部服务边界三个概念。
- [ ] 凭据未进日志/清单/Git；真实清单位于忽略目录。

交给 Day 05：记录实际路由与成功响应结构、导入退出码、两类回滚证据、当前安全/权限配置、未完成的P0项。提交的示例清单只能含占位Key，真实清单另行受控保存。下一天以这些真实行为写契约和测试，不再凭原 C# 示例猜接口。

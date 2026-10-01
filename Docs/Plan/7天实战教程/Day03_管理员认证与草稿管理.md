# Day 03：管理员认证与草稿管理

今天把昨日只读 API 变成能够管理内容的后端。结束时，你应能亲手完成“登录 → 创建草稿 → 编辑 → 后台查到 → 公开端查不到”，并解释密码核验、令牌验证、业务校验分别发生在哪里。

本文是开发教程，代码块是待你实现和验证的示例，不代表功能已存在。标为“局部”的代码不能单独编译；保留已有类的构造器、注解和方法，按说明补入。项目固定为 Java 21、Day 01 锁定的 Spring Boot 4.0.x、MyBatis Starter 4.0.x、MySQL 8.4，不在今天更换版本。

## 1. 开始前与时间安排

前一天必须通过：Flyway 能建 5 表；公开列表分页正确；草稿公开详情是 404；数据库枚举存小写字符串。尚未通过就先修 Day 02，不能用管理员接口掩盖公开查询问题。

| 用时 | 要交付什么 | 学习重点 |
|---|---|---|
| 0:00–0:25 | 画请求链路、加依赖和配置 | 构造器注入、Bean |
| 0:25–1:20 | BCrypt、JWT、后台保护 | 认证与授权、签名 |
| 1:20–2:30 | 管理查询、草稿创建与更新 | DTO、枚举、事务 |
| 2:30–3:30 | 请求验证、登录限流、错误响应 | 异常边界 |
| 3:30–4:00 | 排障、记录、服务器登录确认 | 可复现证据 |

这是 4 小时挑战目标，需要边做边学。实际只有 3 小时时先完成登录与草稿创建，把完整更新与负例验证明确顺延；不能用删除鉴权、跳过测试来声称完成。卡住 30 分钟就保存最小错误，由 AI 协助修一个问题，再亲手重做一次。

今天新增/修改文件（路径均在新项目，旧 C# 项目不修改）：

| 文件绝对路径 | 职责 |
|---|---|
| `D:\Project\QuePhoto_java\pom.xml` | 加安全与 JWT 依赖 |
| `D:\Project\QuePhoto_java\src\main\resources\application-local.yml` | 本地环境变量映射 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\AdminProperties.java` | 单管理员配置 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\JwtProperties.java` | 令牌配置 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\JwtConfig.java` | 编码器、解码器、密码核验器 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\AuthController.java` | 登录 HTTP 入口 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\AuthService.java` | 核验凭据、签发令牌 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\auth\LoginRateLimiter.java` | 单实例有界限流 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\common\SecurityConfig.java` | Web 请求权限边界 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\AdminPortfolioController.java` | 管理 API |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\PortfolioService.java` | 在 Day 02 类中增加写规则 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\PortfolioMapper.java` | 管理 SQL 与行锁 |
| `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\dto\PortfolioWriteRequest.java` | 元数据请求边界 |
| `D:\Project\QuePhoto_java\docs\evidence\day03.md` | 去凭据后的实际验证记录 |

## 2. 第一步：先画链路，再增加依赖

**做什么：**知道代码放在哪里，避免所有逻辑堆进 Controller。

**怎么做：**在纸上写两条链路，然后打开 Day 02 的 `PortfolioService`，确认它通过构造器接收 `PortfolioMapper`。

```text
登录：HTTP → AuthController → 限流 → AuthService → BCrypt.matches → JwtEncoder
写草稿：HTTP → SecurityFilterChain → JwtDecoder → Controller → Service → Mapper → MySQL
```

依赖注入就是 Spring 创建并连接对象。Service 的构造器参数声明“我需要 Mapper”，不自己 `new` 一个 Mapper。`final` 字段表示构造后不重新赋值；它不代表字段引用的对象永远不能变化。

在 `pom.xml` 的现有 `<dependencies>` 中增加以下局部片段，版本由 Day 01 的 Boot 父 POM 管理：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

```powershell
Set-Location 'D:\Project\QuePhoto_java'
.\mvnw.cmd dependency:tree '-Dincludes=org.springframework.security:*'
```

**为什么：**Resource Server 已提供 Bearer 提取、签名验证和认证失败处理。不编写 JWT 字符串拼接器或自己的验签 Filter。

**应看到的结果：**依赖树存在 Security、OAuth2 Resource Server、OAuth2 JOSE，并且没有混入旧 Security 5/6。此时默认安全配置可能让昨日接口暂时变成 401，下一步会明确权限。

## 3. 第二步：建立单管理员配置，安全生成本地凭据

**做什么：**把运行配置与代码分开。用户名、BCrypt 哈希、JWT 密钥来自外部配置，没有默认生产密码。

**怎么做：**在 `application-local.yml` 合并以下局部配置，保留 Day 02 数据库配置。这里的变量名也交给 Day 06 使用；生产最终可从受保护文件映射同名 Spring 属性。

```yaml
admin:
  username: ${ADMIN_USERNAME}
  password-hash: ${ADMIN_PASSWORD_HASH}
jwt:
  secret: ${JWT_SECRET}
  issuer: ${JWT_ISSUER:quephoto}
  audience: ${JWT_AUDIENCE:quephoto-admin}
```

下列非敏感的 JSON 行为配置合并到公共 `application.yml`，使 local、test、prod 和 import 都使用相同的字段规则；不要只放在 local：

```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: true
```

建立两个配置 record（分别放入表中对应文件；以下是完整类）：

```java
package com.quephoto.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("admin")
public record AdminProperties(@NotBlank String username,
                              @NotBlank String passwordHash) {}
```

```java
package com.quephoto.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @NotBlank String audience) {}
```

`record` 用来保存不可变的数据，自动生成构造器和 `username()` 等访问方法。注册方式统一在 `JwtConfig` 上写 `@EnableConfigurationProperties({AdminProperties.class, JwtProperties.class})`，不要重复定义相同配置 Bean。

创建 `D:\Project\QuePhoto_java\tools\HashPassword.java`，这是独立命令行辅助类，不放进 Web 服务源码。完整内容：

```java
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class HashPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("请在真实终端运行");
        char[] raw = console.readPassword("Password: ");
        try {
            String value = new String(raw);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 12 || bytes > 72) throw new IllegalArgumentException("使用12–72字节密码");
            System.out.println(new BCryptPasswordEncoder(12).encode(value));
        } finally {
            Arrays.fill(raw, '\0');
        }
    }
}
```

用 Windows Terminal/PowerShell 运行，不要把密码替换到脚本文字或 Java 文件中：

```powershell
.\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$qpClasspath = (Get-Content '.\target\runtime-classpath.txt' -Raw).Trim()
java --class-path $qpClasspath '.\tools\HashPassword.java'
$env:ADMIN_USERNAME = Read-Host '管理员用户名'
$qpHash = Read-Host '粘贴刚生成的BCrypt哈希' -AsSecureString
$env:ADMIN_PASSWORD_HASH = [System.Net.NetworkCredential]::new('', $qpHash).Password
$qpRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$qpBytes = New-Object byte[] 32
$qpRandom.GetBytes($qpBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($qpBytes)
$qpRandom.Dispose()
$env:JWT_ISSUER = 'quephoto'
$env:JWT_AUDIENCE = 'quephoto-admin'
```

原始密码只在交互输入中出现；哈希也按敏感配置保存。不要打印 JWT 密钥，亦不要在 `.http`、截图、Git、AI 对话中记录密码或 token。本地环境变量只在当前窗口及其子进程有效，启动服务要在这个窗口。第二天打开新窗口时重新安全载入；更换 JWT 密钥会让旧 token 失效。

**为什么：**BCrypt 是带盐的慢密码哈希，JWT 密钥用于签名，两者不是同一东西。Base64 只是密钥字节的文本编码，不是加密。

**应看到的结果：**运行时能读取管理员配置，缺少配置会在启动时明确失败；仓库中只有 `${...}` 占位，没有真实值。[Spring 密码存储文档](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

## 4. 第三步：让官方组件签发与验证 JWT

**做什么：**固定 HS256，验证签名、过期时间、issuer、audience，并固定管理员权限。

**怎么做：**在 `JwtConfig.java` 中建 `@Configuration` 类，加入前面的 `@EnableConfigurationProperties`。以下是类内局部代码，IDE 根据类名补 import；主要包为 `org.springframework.security.oauth2.jwt`、`org.springframework.security.oauth2.core`、`org.springframework.security.oauth2.jose.jws.MacAlgorithm`，密钥类型来自 `javax.crypto`。

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}

@Bean
SecretKey jwtKey(JwtProperties p) {
    byte[] bytes = Base64.getDecoder().decode(p.secret());
    if (bytes.length < 32) throw new IllegalStateException("JWT密钥必须至少32字节");
    return new SecretKeySpec(bytes, "HmacSHA256");
}

@Bean
JwtEncoder jwtEncoder(SecretKey key) {
    return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
}

@Bean
JwtDecoder jwtDecoder(SecretKey key, JwtProperties p) {
    var decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> requiredClaims = token -> {
        boolean ok = token.getExpiresAt() != null
                && token.getAudience().contains(p.audience());
        return ok ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "令牌声明无效", null));
    };
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(p.issuer()), requiredClaims));
    return decoder;
}
```

这里显式要求 `exp` 存在，再由默认验证器检查时间；不能只验证“有签名”。默认允许的时钟偏差需要在测试中考虑：过期负例至少过期 2 分钟，不用刚过期 1 秒的样本推断验签失败。

上述 Builder 对应 Security 7.0 API；版本来自项目 Boot BOM。若 IDE 找不到 `withSecretKey`，先看 `dependency:tree`，不要临时抄另一个 JWT 库。官方：[编码器](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html)、[解码器算法配置](https://docs.spring.io/spring-security/site/docs/7.0.x/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html)。

在 `AuthService` 中用构造器接收 `AdminProperties`、`JwtProperties`、`PasswordEncoder`、`JwtEncoder`。登录算法按顺序写：

1. 核验输入非空并限制密码 UTF-8 长度最多 72 字节；不要输出入参。
2. 无论用户名是否正确，都调用一次 `passwordEncoder.matches(raw, configuredHash)`，减少两种错误的明显时间差。
3. 用户名或密码有任何一项不匹配，抛项目业务异常，统一 401“用户名或密码错误”。
4. 使用当前 `Instant`、固定 subject `admin`、固定 scope `admin`、配置 issuer/audience 和 60 分钟过期签发。
5. 返回 `LoginResponse(String accessToken, Instant expiresAt)`，不返回密码/哈希。

签发局部片段：

```java
Instant now = Instant.now();
Instant expiry = now.plusSeconds(3600);
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(jwtProperties.issuer()).subject("admin")
        .audience(List.of(jwtProperties.audience()))
        .issuedAt(now).expiresAt(expiry).claim("scope", "admin").build();
JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
return new LoginResponse(token, expiry);
```

`AuthController` 仅提供 `POST /api/auth/login`，接收 `@Valid @RequestBody LoginRequest`。请求 record 字段是 `@NotBlank String username`、`@NotBlank String password`，客户端不能传 `role` 或 `scope`。响应 200。密码是 String，无法承诺 JVM 内存立刻擦除；本阶段重点是短暂使用且禁止记录。

**为什么：**认证回答“是谁”，授权回答“可做什么”。JWT 载荷可被读到，不能把密码放在 payload；签名只能验证来源与未被改动。[官方 JWT 验证说明](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

**应看到的结果：**合法登录返回 token 和带 `Z` 的 expiresAt；错误密码、错误用户名返回同样的 401。

## 5. 第四步：在入口统一保护后台

**做什么：**为整个 `/api/admin/**` 建权限规则，同时保证明天非 Web 导入不依赖 HttpSecurity。

**怎么做：**`SecurityConfig.java` 标记 `@Configuration` 和 `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)`。后者来自 `org.springframework.boot.autoconfigure.condition`。以下为类内局部配置：

```java
@Bean
SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .authorizeHttpRequests(a -> a
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/portfolios", "/api/portfolios/*",
                "/api/tag-groups").permitAll()
            .requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
            .anyRequest().denyAll())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    // 在这里追加下述 JSON 401/403 handler。
    return http.build();
}
```

`DispatcherType` 使用 `jakarta.servlet.DispatcherType`。Day 01 的 `/api/hello` 是练习路由，今天不放入白名单；尚未实现 `/health`，不要在配置中假定它存在。这里只支持 Authorization Bearer，不使用 Cookie 登录，因此采用无状态策略并关闭 CSRF；以后换成 Cookie 认证要重新评估。

补充两个安全错误处理器：认证失败 `AuthenticationEntryPoint` 返回 401，权限不足 `AccessDeniedHandler` 返回 403。都用 `application/problem+json` 和 Day 02 相同的 `title/status/errors` 结构，401 同时保留 `WWW-Authenticate: Bearer`。它们位于 MVC 之前，不能只依赖 `@RestControllerAdvice`。

Boot 4 的 JSON 写入注入 `tools.jackson.databind.json.JsonMapper`，调用 `writeValue(response.getOutputStream(), Map.of(...))`；不要复制旧教程的 `com.fasterxml.jackson.databind.ObjectMapper` 包名。[Boot 4 JSON 文档](https://docs.spring.io/spring-boot/4.0/reference/features/json.html)

未知路径不得伪造成功：已有 token 访问不存在的管理 API 由 MVC 返回 404；对安全链拒绝的非白名单路径，错误处理器可根据本项目公开路径与 `/api/admin/**` 判断是否为未知资源并输出 404。记录此分支，Day 05 验证未知路径、不支持的写 API 与真正鉴权失败的区别。

**为什么：**Controller 少写一个检查不应让新后台入口裸露。`@ConditionalOnWebApplication` 防止明天 `web-application-type=none` 时构造 HttpSecurity 失败。JWT 与配置 Bean 不依赖 Servlet，可在同一 JAR 的导入模式中加载。

**应看到的结果：**无 token 管理请求 401；合法 token 管理请求通过；公开 GET 仍可匿名访问；没有跳到 HTML 登录页面。

## 6. 第五步：实现有明确输入边界的草稿写入

**做什么：**新增管理列表、详情、草稿创建和元数据完整更新。公开 DTO 继续沿用 Day 02，不把数据库对象直接返回。

**怎么做：**先确定下面的 HTTP 对照，再写 Controller 调 Service。

| API | Service 新方法建议 | 成功结果 |
|---|---|---|
| `GET /api/admin/portfolios` | `adminPage(page,pageSize,status)` | 200，`PageResponse<T>` |
| `GET /api/admin/portfolios/{id}` | `adminDetail(id)` | 200，管理详情 |
| `POST /api/admin/portfolios` | `createDraft(request)` | 201，`{"id":真实ID}` |
| `PUT /api/admin/portfolios/{id}` | `updateMetadata(id,request)` | 200，更新后的管理详情 |
| `GET /api/admin/tag-groups` | 复用 Day 02 `TagService.tree()` | 200，同公开标签树 |

管理列表仍返回 `items/page/pageSize/totalCount`；分页默认 1/20，pageSize 1–50。查询条件 status 可空或严格为 `draft/published`；查询 COUNT 与列表共享过滤条件。管理详情比公开详情多 `status/coverImageId/shareImageKey`，图片多 `objectKey`。仍使用 `images[]`、`tags[]`，不突然换成两个图片数组。

创建/更新共用下列完整请求 record。`@JsonProperty(required=true)` 作用于 record 构造参数，使完整 PUT 的可空字段“必须出现，但值可以是 null”；不要把它误解成 `@NotNull`。创建本周也带齐同一组字段，以便重放。

```java
package com.quephoto.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PortfolioWriteRequest(
    @JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
    @JsonProperty(required = true) @Size(max = 10000) String description,
    @JsonProperty(required = true) @Size(max = 200) String location,
    @JsonProperty(required = true) LocalDateTime shotAt,
    @JsonProperty(required = true) String shotTimePrecision,
    @JsonProperty(required = true) @NotBlank String status
) {}
```

Jackson 3 的注解仍在 `com.fasterxml.jackson.annotation`，与前面 `tools.jackson` 的数据绑定包不同。亲测两种请求：缺少 `location` 应 400；显式 `"location":null` 应成功。若不满足，先修绑定/缺字段检测，不能静默清空已有值。`shareImageKey/objectKey` 不是元数据字段；严格未知字段设置让这些输入直接 400。

在 `PortfolioService` 中实现以下算法，不把它们分散到 Mapper：

1. title 先 `strip()`，再检查非空及 100 字符边界；description 上限 10,000，location 上限 200。
2. 时间与精度同空或同有；有值时拒绝秒精度及 `Z`/偏移格式，只接受 `year/month/day/hour/minute`。
3. 按精度归一化：year → 1月1日零点；month → 当月1日零点；day → 零点；hour → 分秒0；minute → 秒0；同时清纳秒。
4. 创建只接受 draft，服务端也强制写 draft；传 published 返回 400，不能让客户端绕过封面规则。
5. 更新先锁作品；不存在 404。今天对目标 status=published 保守返回 400“请完成图片及发布校验后发布”；draft 更新/下架可用。Day 04 用完整发布规则替换此分支。
6. 所有写操作维护 UTC updatedAt；审计时间用 `LocalDateTime.now(ZoneOffset.UTC)` 写库，输出沿用 Day 02 转 `Instant`。

可新增业务 enum，但 `PortfolioRow.status/shotTimePrecision` 继续用 String。下面是可放在 `D:\Project\QuePhoto_java\src\main\java\com\quephoto\portfolio\PortfolioStatus.java` 的完整枚举：

```java
package com.quephoto.portfolio;

public enum PortfolioStatus {
    DRAFT("draft"), PUBLISHED("published");
    private final String value;
    PortfolioStatus(String value) { this.value = value; }
    public String value() { return value; }
    public static PortfolioStatus parse(String value) {
        for (PortfolioStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        throw new IllegalArgumentException("status只接受draft或published");
    }
}
```

在 Service 捕获这类输入解析错误并转项目 400 异常；不要让预期参数错误成为 500。持久化用 `row.setStatus(status.value())`，不用 `status.name()`、`ordinal()` 或 MyBatis 默认 enum 映射。`equals` 比较字符串内容，`==` 比较引用是否相同。

Mapper 增加锁查询（局部示例；字段映射沿用 Day 02；用 SQL/XML 二选一，别重复声明）：

```java
@Select("SELECT * FROM portfolio WHERE id = #{id} FOR UPDATE")
PortfolioRow findByIdForUpdate(@Param("id") long id);
```

写 `@Transactional` 的 `public updateMetadata(...)`，Controller 必须通过注入的 Service 调它。锁定 → 校验 → 参数化 UPDATE → 查询管理 DTO 都在事务内。更新列只列可编辑字段与 updated_at，不能从请求覆写 id、cover_image_id、created_at。`@Transactional` 来自 Spring，不用 `jakarta.transaction` 混写。

创建 INSERT 用 `@Options(useGeneratedKeys=true,keyProperty="id")` 回填可变 Row 的 Long id，SQL 不拼请求字符串。响应 ID 取真实生成值，任何 ID 都限制为 1–9,007,199,254,740,991。创建成功按 201 返回 `Map.of("id", row.getId())`。

**为什么：**DTO 是输入合同，Service 是业务规则，数据库约束是最后防线。行锁保证明天导入/发布/改封面能采用同一个并发规则。

**应看到的结果：**草稿 CRUD 中的“创建/读取/更新”可用；没有 DELETE；公开查询行为与 Day 02 一致。

## 7. 第六步：限流与请求重放

**做什么：**验证真实 HTTP 行为，并限制登录尝试。先实现保守的单实例全局限流：滚动 60 秒内最多 5 次登录尝试，包含成功和失败；仅一个管理员，今天不引入 IP 信任链或 Redis。Day 06 可在 Nginx 再加每来源限速，不能不记录实际策略。

**怎么做：**`LoginRateLimiter` 是单例 `@Component`，维护 `ArrayDeque<Instant>`；一个 `synchronized` 方法先移除 60 秒前记录，若 size≥5 则抛 429，否则追加当前时间。集合最多 5 个元素。登录核验前调用，拒绝时返回 `Retry-After: 60`。输入校验失败在 Controller 绑定前返回 400，不计为正常登录尝试；生产代理另外限制请求体大小。

在一个终端启动服务，另一个终端发请求。以下不会把真实密码写进命令历史；仍不要开启请求正文日志。

```powershell
# 终端A：沿用已注入DB和管理员配置的窗口
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

```powershell
# 终端B：本地HTTP，生产使用Day06的HTTPS或SSH隧道
$qpBase = 'http://127.0.0.1:8080'
$qpCred = Get-Credential -Message 'QuePhoto管理员登录'
$qpLoginJson = @{ username = $qpCred.UserName; password = $qpCred.GetNetworkCredential().Password } | ConvertTo-Json
$qpLogin = Invoke-RestMethod -Method Post -Uri "$qpBase/api/auth/login" -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($qpLoginJson))
$qpHeaders = @{ Authorization = "Bearer $($qpLogin.accessToken)" }
Remove-Variable qpLoginJson,qpCred
$qpDraft = @{
  title = '国庆街头'; description = '今天用Java保存的第一套作品'
  location = '上海'; shotAt = '2026-10-03T18:23:10'; shotTimePrecision = 'month'; status = 'draft'
}
$qpCreated = Invoke-RestMethod -Method Post -Uri "$qpBase/api/admin/portfolios" -Headers $qpHeaders -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes(($qpDraft | ConvertTo-Json)))
$qpId = $qpCreated.id
Invoke-RestMethod -Uri "$qpBase/api/admin/portfolios/$qpId" -Headers $qpHeaders
curl.exe --silent --output NUL --write-out '%{http_code}' "$qpBase/api/portfolios/$qpId"
```

预期：创建 HTTP 201，响应 `{"id":某个正整数}`；管理详情 status=draft；shotAt 归一化为 `2026-10-01T00:00:00`；公开详情 404。响应 JSON 字段顺序不影响结果，枚举大小写影响结果。

```powershell
$qpDraft.description = '已修改说明'
Invoke-RestMethod -Method Put -Uri "$qpBase/api/admin/portfolios/$qpId" -Headers $qpHeaders -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes(($qpDraft | ConvertTo-Json)))
curl.exe --silent --output NUL --write-out '%{http_code}' "$qpBase/api/admin/portfolios"
```

预期：更新 200；无 token 管理列表 401。`curl.exe` 明确调用可执行文件，避免 Windows PowerShell 的 `curl` 别名。

负例按顺序执行并在 `docs/evidence/day03.md` 记录状态码，不保存 token：

| 改动 | 期望 | 数据库要求 |
|---|---|---|
| title=`"   "` | 400，errors.title | 原内容不变 |
| shotAt 有值，precision=null | 400 | 原内容不变 |
| PUT 缺 location；另一次显式 null | 前者400，后者200 | 不发生隐式清空 |
| status=`"DRAFT"` 或 `"deleted"` | 400 | 原状态不变 |
| 创建 status=published | 400 | 没有多一条记录 |
| Authorization=`Bearer invalid` | 401 | 没有写入 |
| 同一窗口内第6次登录尝试 | 429 | 凭据未泄露 |
| 正确签名但过期/错issuer/错aud | 401 | 没有写入 |

最后一项写自动测试：在测试代码使用同一 `JwtEncoder` 生成测试声明，再交给真实 `JwtDecoder` 或 HTTP 验证。只给测试提供随机测试密钥，分别设置 expiresAt=当前时间−120秒、错误 issuer、错误 audience；不暴露测试签发 API。D5 将这些负例固化为回归测试。

**为什么：**“可以登录”只证明一条成功路径；错误签名/声明与越权拒绝才证明权限边界存在。

**应看到的结果：**完成登录→创建→更新→隔离闭环，错误输入无部分写入，5次阈值可复现。

## 8. 今天最常见的问题

| 现象 | 先检查什么 | 修法 |
|---|---|---|
| 所有请求302/HTML登录 | 仍启用了formLogin | 显式关闭并提供JSON安全错误 |
| token合法仍403 | scope是否固定admin、是否要求SCOPE_admin | 对齐默认权限映射，勿改成全部放行 |
| 合法登录401 | 哈希是否完整、是否被`$`插值破坏 | 用交互输入；部署用受保护原样文件 |
| token重启后失效 | 本地是否重新生成JWT_SECRET | 开发属预期；生产持久保存同一密钥 |
| 数据库enum映射异常 | Row字段是否擅自改enum | 保持String，Service显式value映射 |
| 事务不生效 | 是否在同类里`this.updateMetadata()` | 从Controller调用注入的Service代理 |
| 绑定失败成为500 | 缺异常映射或泄露库异常 | 按Day02错误模型转400/409，日志保留原因 |
| 导入模式以后缺HttpSecurity | SecurityConfig没有条件注解 | 只在Servlet Web启动时加载安全链 |

还要确认服务器可以通过 SSH 登录，把登录成功时间写入记录；不把安装 Java/MySQL/反向代理全塞进今天，部署主体在 Day 06。

## 9. 闭卷练习、提示与交接

先合上代码回答，再看提示。

1. 构造器注入为什么比 Controller 内 `new PortfolioService(...)` 容易测试？提示：依赖显式，可以在测试替换 Mapper；Spring 还能提供事务代理。
2. JWT 能否藏住作品标题或密码？提示：payload 可解码，机密信息不入 token；签名不等于加密。
3. 给 title 增加“不能包含换行”的业务校验，并用真实请求证明成功/失败两种情况。提示：放在 Service 公用校验里，错误映射 errors.title，别只在一个 Controller 做。
4. 画出请求在验签失败与业务参数错误时分别在哪层退出。提示：安全 Filter 在 MVC 前，Advice 主要处理 MVC 内异常。
5. 为什么 MyBatis 不直接保存 `PortfolioStatus.DRAFT`？提示：默认 enum 策略可能保存名称 `DRAFT`，与数据库/接口要求 `draft` 不同。

- [ ] 解释过密码、哈希、JWT 密钥和 token 的区别。
- [ ] `/api/admin/**` 统一鉴权，公开 GET 不需要 token。
- [ ] 错误凭据401、超频429、缺字段400均实测。
- [ ] 管理员能创建与修改草稿；公开端看不到该草稿。
- [ ] PUT 明确区分缺字段和显式null；状态小写，时间规范化。
- [ ] 更新先锁作品，数据库层与API的错误结构承接 Day 02。
- [ ] 仓库与验证记录没有真实密码、哈希、密钥或token。
- [ ] 已记录实际耗时、一个仍不理解的概念、顺延任务。

交给 Day 04：保存草稿 ID（不是 token）、测试作品的当前元数据、管理员登录办法、Mapper 的 `findByIdForUpdate`、明确的 String 枚举规则。明天会填上发布分支，不要提前手改 status=published 来绕过规则。

下一篇：[Day 04：图片导入与作品发布](D:/Project/QuePhoto_java/Plan/7天实战教程/Day04_图片导入与作品发布.md)。

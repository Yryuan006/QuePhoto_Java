# Day 02：数据库建模与公开查询

今天把“返回写死 JSON”改成“从 MySQL 查询真实关系数据”。完成后，数据库里应有五张业务表，三个公开查询接口可以使用，并且任何公开查询都看不到草稿。

本文件是开发教程，Spring Boot 基础工程已导入，数据库迁移仍待按本章执行。以 [数据库与 API 实施基线](D:/Project/QuePhoto_java/Plan/QuePhoto_Java_数据库与API实施基线.md) 为业务依据。所有代码目标均在本项目；原 C# 项目保持只读。

## 1. 开始前和今天的时间盒

先确认 D1 已能在终端运行 Java 21、Maven Wrapper 和 Spring Boot 4.0.x；`GET /api/hello` 返回 200。MySQL 8.4 已启动，能用数据库客户端登录，并且你知道这是本地开发实例。今天不安装 Spring Security，不创建管理写接口，不接真实 OSS 上传。

学习与开发交替进行：读完一个小段就动手，再用输出证明理解。下面的“完整文件”可以直接作为对应文件内容；标记为“局部”的代码只表示要加进已有类的部分。全文不是一份复制后自动出现完整项目的生成脚本，尤其 Java Bean 的机械 getter/setter 需要用 IDE 生成。

| 时间 | 要交付什么 | 当场能解释什么 |
|---|---|---|
| 00:00–00:20 | 画五表关系，认清字段与 DTO | 一对多、唯一键、可空值 |
| 00:20–01:05 | 加依赖，Flyway 创建空 Schema | 迁移为什么比手工建表可重复 |
| 01:05–02:00 | 列表查询从 SQL 走到 HTTP | Mapper、Service、Controller 如何协作 |
| 02:00–02:40 | 详情与标签树 | 一对多为何不直接拼成一个大 JOIN |
| 02:40–03:30 | 本地样例、错误请求、约束验证、闭卷练习 | 如何证明草稿隔离和分页正确 |
| 03:30–04:00 | 排障与证据整理 | 用最小请求复现问题 |

这是 4 小时挑战目标。只剩 3 小时时，先让迁移、列表、草稿详情 404 通过；标签树和未完验收如实记作未完成，明天先补，不能把删掉验证当作省时。

## 2. 先用业务读懂五张表

`portfolio` 是作品集；`portfolio_image` 是其图片；`tag_group` 是“题材、地点类型”等分类组；`tag` 是组内的选项；`portfolio_tag` 记录某作品在某组里选了哪一个标签。

先在纸上写出以下三条，而后才建表：

1. 一个作品能有多张图片，但一张图片只登记到一个作品。
2. 一个组有多个标签，一个作品在一个组里至多选一个标签。
3. 一个作品可以暂时没有封面；只有属于该作品且类型为 work 的图片才可作为发布封面。

前两条主要靠数据库约束保护。第三条的“图片存在”由外键保护，“属于该作品且为 work”在 D4 的 Service 里保护。不要误认为有一个封面外键，就已经实现全部发布规则。

今天没有任何生产写入口。本地开发样例会用 SQL 绕过业务入口，必须自行满足这些业务规则；这不是以后运营作品的方式。

## 3. 准确的文件清单

工程根目录固定为 `D:/Project/QuePhoto_java`，包名固定为 `com.quephoto`。按下面的完整路径创建或修改：

| 文件绝对路径 | 作用 |
|---|---|
| `D:/Project/QuePhoto_java/pom.xml` | 在 D1 工程增加数据库依赖 |
| `D:/Project/QuePhoto_java/src/main/resources/application.yml` | 公共 MyBatis/Flyway 设置，与 D1 内容合并 |
| `D:/Project/QuePhoto_java/src/main/resources/application-local.yml` | 本机数据库连接与图片 URL 基址 |
| `D:/Project/QuePhoto_java/src/main/resources/db/migration/V1__init_schema.sql` | 完整五表结构 |
| `D:/Project/QuePhoto_java/src/main/resources/mapper/PortfolioMapper.xml` | 公开作品查询 |
| `D:/Project/QuePhoto_java/src/main/resources/mapper/TagMapper.xml` | 标签树两条查询 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioRow.java` | 作品数据库行 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioImageRow.java` | 图片数据库行 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioTagRow.java` | 作品标签联查结果 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioMapper.java` | SQL 方法签名 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioService.java` | 公开查询和 DTO 组装，D3/D4 继续追加管理方法 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/PortfolioController.java` | 两个公开 HTTP 路由 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/portfolio/dto/PublicPortfolioDtos.java` | 公开列表、详情、图片、标签 DTO |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/common/dto/PageResponse.java` | 固定分页对象 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/common/ApiExceptionHandler.java` | 统一错误响应；若 D1 已建则补充 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/oss/ImageUrlService.java` | 从固定域名和 Key 生成 URL，不访问 OSS |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/TagGroupRow.java` | 标签组行 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/TagRow.java` | 标签行 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/TagMapper.java` | 标签查询签名 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/TagService.java` | 两个列表组装树 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/TagController.java` | 公开标签树路由 |
| `D:/Project/QuePhoto_java/src/main/java/com/quephoto/tag/dto/TagTreeDtos.java` | 标签树 DTO |
| `D:/Project/QuePhoto_java/examples/local-only-day02.sql` | 仅手动执行的开发样例，不打包到 classpath |
| `D:/Project/QuePhoto_java/docs/day02-evidence.md` | 记录请求、预期、实际，不放密码 |

这里的 `Row` 相当于“持久化 Entity”。我们使用 MyBatis，不需要 JPA 的 `@Entity`；也不在实体中放 `List<Image>` 导航属性。接口返回 DTO，不能直接返回数据库行。

## 4. 给工程接上数据库

### 4.1 加依赖，并理解“依赖”负责什么

在 `pom.xml` 现有 `<dependencies>` 中追加以下**完整依赖片段**；保留 D1 的 MVC、Validation、测试依赖与 Boot 4.0.x 的确切 parent 版本。

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <scope>runtime</scope>
</dependency>
```

MyBatis 帮你把结果行转换成 Java 对象，驱动负责和 MySQL 通信，Flyway 负责把结构迁移到目标版本。Boot 4 的 Flyway 自动配置使用专门 starter；只抄旧教程加 `flyway-core` 不够。MyBatis 官方目前列出的 4.0.0 可用于 Boot 4.0+，Java 21 满足要求。本周固定这套组合，不在学习过程中顺手升级到另一个大版本。[Boot 4.0 starter 列表](https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html)、[MyBatis Starter 官方要求](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)。

执行并观察是否只有一套 Boot/MyBatis/Flyway 依赖，没有下载失败或版本冲突：

```powershell
Set-Location 'D:/Project/QuePhoto_java'
./mvnw.cmd dependency:tree
```

### 4.2 建开发库，凭据不写进仓库

用已有数据库客户端以有建库权限的本地账号执行：

```sql
CREATE DATABASE IF NOT EXISTS quephoto_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
```

通过客户端账号管理创建独立开发用户 `quephoto_dev`，仅授予 `quephoto_dev.*` 的权限。密码由你在客户端设置并保存于本机秘密配置；不用数据库 root 作为应用账号。开发账号需要建表/改表权限以执行迁移，D6 再明确服务器账号范围。

在现有 `application.yml` 合并以下配置；不要重复写第二个顶层 `spring:`：

```yaml
spring:
  sql:
    init:
      mode: never
  flyway:
    locations: classpath:db/migration
    clean-disabled: true
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

`application-local.yml` 新增数据库部分：

```yaml
server:
  address: 127.0.0.1
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/quephoto_dev?connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
oss:
  base-url: ${OSS_BASE_URL:https://images.example.invalid}
```

`example.invalid` 是不可用的演示地址，D2 只检查 URL 格式；D4 必须换成确认过的真实图片访问基址并验证图像。连接配置将会话时区明确设为 UTC；`DATETIME` 本身没有时区，不能指望数据库替你理解拍摄时间。[Connector/J 时间配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。

在启动应用的同一个 PowerShell 窗口读取密码，避免把明文敲进历史：

```powershell
$dbCredential = Get-Credential -UserName 'quephoto_dev' -Message '输入本地数据库密码'
$env:DB_USERNAME = $dbCredential.UserName
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
$env:SPRING_PROFILES_ACTIVE = 'local'
```

环境变量只存在当前进程及子进程。别把其值写到截图或证据文件；另开终端后要重新设置。今天先完成下一节迁移文件，再启动应用。

## 5. 写完整的 V1 迁移

将以下内容写入 `V1__init_schema.sql`。这是**完整 SQL 文件**，不含建库、账号或演示数据。

```sql
CREATE TABLE portfolio (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  description TEXT NULL,
  location VARCHAR(200) NULL,
  shot_at DATETIME NULL,
  shot_time_precision VARCHAR(10) COLLATE utf8mb4_0900_bin NULL,
  status VARCHAR(10) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'draft',
  cover_image_id BIGINT NULL,
  share_image_key VARCHAR(500) COLLATE utf8mb4_0900_bin NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_portfolio_public (status, shot_at, id),
  KEY ix_portfolio_cover (cover_image_id),
  CONSTRAINT ck_portfolio_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
  CONSTRAINT ck_portfolio_description CHECK (
    description IS NULL OR CHAR_LENGTH(description) <= 10000
  ),
  CONSTRAINT ck_portfolio_status CHECK (status IN ('draft', 'published')),
  CONSTRAINT ck_portfolio_shot_pair CHECK (
    (shot_at IS NULL AND shot_time_precision IS NULL)
    OR
    (shot_at IS NOT NULL AND shot_time_precision IS NOT NULL
      AND shot_time_precision IN ('year', 'month', 'day', 'hour', 'minute'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE portfolio_image (
  id BIGINT NOT NULL AUTO_INCREMENT,
  portfolio_id BIGINT NOT NULL,
  image_type VARCHAR(10) COLLATE utf8mb4_0900_bin NOT NULL,
  object_key VARCHAR(500) COLLATE utf8mb4_0900_bin NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  width INT NULL,
  height INT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_image_object_key (object_key),
  KEY ix_image_order (portfolio_id, image_type, sort_order, id),
  CONSTRAINT fk_image_portfolio FOREIGN KEY (portfolio_id)
    REFERENCES portfolio (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_image_type CHECK (image_type IN ('work', 'scene')),
  CONSTRAINT ck_image_key CHECK (CHAR_LENGTH(object_key) > 0),
  CONSTRAINT ck_image_dimensions CHECK (
    (width IS NULL AND height IS NULL)
    OR
    (width IS NOT NULL AND height IS NOT NULL AND width > 0 AND height > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE portfolio ADD CONSTRAINT fk_portfolio_cover
  FOREIGN KEY (cover_image_id) REFERENCES portfolio_image (id)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE tag_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_tag_group_name (name),
  CONSTRAINT ck_tag_group_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE tag (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_tag_group_name (group_id, name),
  UNIQUE KEY uq_tag_id_group (id, group_id),
  CONSTRAINT fk_tag_group FOREIGN KEY (group_id)
    REFERENCES tag_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_tag_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE portfolio_tag (
  portfolio_id BIGINT NOT NULL,
  tag_group_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (portfolio_id, tag_group_id),
  KEY ix_portfolio_tag_filter (tag_id, portfolio_id),
  KEY ix_portfolio_tag_group_fk (tag_id, tag_group_id),
  CONSTRAINT fk_portfolio_tag_portfolio FOREIGN KEY (portfolio_id)
    REFERENCES portfolio (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_portfolio_tag_membership FOREIGN KEY (tag_id, tag_group_id)
    REFERENCES tag (id, group_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

逐条对照业务理解：

- `(portfolio_id, tag_group_id)` 是主键，才能阻止同组双选；若换成 `(portfolio_id, tag_id)`，不同标签会绕过限制。
- 组合外键 `(tag_id, tag_group_id)` 一起验证标签和归属组。只有两个分别存在的 ID 不足以证明它们属于同一组。目标组合候选键也显式建好。
- 循环关系分步建：先作品，再图片，最后补封面外键。创建作品时也先留空封面，不要关闭外键检查来绕过去。
- `utf8mb4_0900_bin` 保留 Key 的大小写差异及尾部空格差异；图片 `A.jpg` 和 `a.jpg` 是两个对象。本周标签名称也采用大小写敏感比较；应用只做去首尾空白，不私自转小写。
- SQL 的 `NULL` 不是 Java 的普通布尔 false；CHECK 对 UNKNOWN 可能放行。因此“宽有值、高为空”分支必须显式写出两个 `IS NOT NULL`。拍摄时间/精度同理。[MySQL CHECK 规则](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)。
- 时间精度的规范化、封面所属作品/类型、ID 的 JavaScript 安全整数上界在 Service 验证。不要把跨表查询塞进 CHECK，也不要给自增 ID 写 MySQL 不支持的 CHECK。
- 所有审计字段无隐式时间默认值；每次 INSERT 写 `UTC_TIMESTAMP(3)`，作品任何写操作显式维护 `updated_at`。本地 SQL 和以后应用写入都遵守同一约定。

启动并在数据库客户端观察：

```powershell
./mvnw.cmd spring-boot:run
```

```sql
SHOW TABLES;
SELECT version, description, success FROM flyway_schema_history;
SHOW CREATE TABLE portfolio_tag;
SHOW CREATE TABLE portfolio_image;
```

预期是五张业务表加一张 Flyway 历史表，V1 为 success。第二次启动应提示结构已最新，不再创建表。Flyway 是唯一结构入口；别另建 `schema.sql`、`data.sql` 或 Hibernate 自动建表配置。[Spring 数据库初始化](https://docs.spring.io/spring-boot/how-to/data-initialization.html)。

V1 一旦被共享或进入服务器，就不能通过编辑旧文件修改数据库。后续变更用 V2；迁移失败先读具体 SQL 错误，不用 `repair` 隐藏差异。MySQL DDL 可能部分成功，不能以为 Flyway 失败会回滚整份建表文件。

## 6. 分清 Row、DTO 和 Java 的 null

在每个 Row 文件声明 `package com.quephoto.portfolio;`（标签 Row 用 `com.quephoto.tag`），创建 `public class`、下表全部 `private` 字段和无参构造器。用 IDE 的 Generate → Getter and Setter 生成所有字段的访问方法；不要加 Lombok。

| Row 类 | 字段与 Java 类型 |
|---|---|
| `PortfolioRow` | `Long id, coverImageId;`；`String title, description, location, shotTimePrecision, status, shareImageKey, coverObjectKey;`；`LocalDateTime shotAt, createdAt, updatedAt;` |
| `PortfolioImageRow` | `Long id, portfolioId;`；`String imageType, objectKey;`；`Integer sortOrder, width, height;`；`LocalDateTime createdAt;` |
| `PortfolioTagRow` | `Long groupId, tagId;`；`String groupName, tagName;` |
| `TagGroupRow` | `Long id;`；`String name;`；`Integer sortOrder;` |
| `TagRow` | `Long id, groupId;`；`String name;`；`Integer sortOrder;` |

`coverObjectKey` 是封面联查产生的投影字段，不是 `portfolio` 表的新列。数据库字段 `shot_at` 在配置作用下映射到 `shotAt`。下面只展示 Bean 的机械写法，其余字段按表补齐：

```java
// 局部示例，不是完整 PortfolioRow
private Long coverImageId;
public Long getCoverImageId() { return coverImageId; }
public void setCoverImageId(Long coverImageId) { this.coverImageId = coverImageId; }
```

`Long`、`Integer` 能表达未知的 null；`long`、`int` 不能。宽高未知应返回 null，不能伪造 0。方法返回总数时可以用 `long`，因为 SQL COUNT 总会给出数值。

**本教程的唯一持久化枚举策略：Row 使用 String，Service 在需要业务分支时显式解析成 enum，写数据库/DTO 时显式取小写 code。** D2 只查询，直接保留数据库受 CHECK 保护的小写值。不要把数据库的 `published` 直接映射给 Java `PUBLISHED` 并期待默认 EnumTypeHandler 自动转换；更不要保存 ordinal。D3/D4 的 enum 是领域辅助类型，不替换 Row 字段。

拍摄时间 `shotAt` 保持 `LocalDateTime`，不加 Z。审计数据库值也是 `LocalDateTime`，但语义约定为 UTC，输出管理 DTO 时使用 `row.getCreatedAt().toInstant(ZoneOffset.UTC)`。不能用系统默认时区解释它，也不能对 `shotAt` 做这次转换。

创建以下两个**完整 DTO 文件**：

```java
// common/dto/PageResponse.java
package com.quephoto.common.dto;
import java.util.List;
public record PageResponse<T>(List<T> items, int page, int pageSize, long totalCount) {}
```

```java
// portfolio/dto/PublicPortfolioDtos.java
package com.quephoto.portfolio.dto;
import java.time.LocalDateTime;
import java.util.List;
public final class PublicPortfolioDtos {
    private PublicPortfolioDtos() {}
    public record ListItem(Long id, String title, String location,
        LocalDateTime shotAt, String shotTimePrecision, String coverImageUrl) {}
    public record Image(Long id, String imageType, String imageUrl,
        Integer sortOrder, Integer width, Integer height) {}
    public record Tag(Long groupId, String groupName, Long tagId, String tagName) {}
    public record Detail(Long id, String title, String description, String location,
        LocalDateTime shotAt, String shotTimePrecision, String coverImageUrl,
        String shareImageUrl, List<Image> images, List<Tag> tags) {}
}
```

`record` 自动提供构造器和同名访问方法，适合只传递数据。`PageResponse<T>` 的泛型表示“分页结构固定，元素类型可变”。嵌套 record 只是减少小文件数量，JSON 不会因此多一层；返回 `Detail` 就得到详情对象。

## 7. 先让公开列表完整走通

### 7.1 Mapper：只回答“怎么查”

`PortfolioMapper.java` **完整文件**：

```java
package com.quephoto.portfolio;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface PortfolioMapper {
    long countPublished(@Param("tagId") Long tagId);
    List<PortfolioRow> findPublishedPage(@Param("tagId") Long tagId,
        @Param("limit") int limit, @Param("offset") long offset);
    PortfolioRow findPublishedById(@Param("id") long id);
    List<PortfolioImageRow> findImages(@Param("portfolioId") long portfolioId);
    List<PortfolioTagRow> findTags(@Param("portfolioId") long portfolioId);
}
```

`@Mapper` 让 MyBatis 为接口生成运行时实现；你不用自己 `new` 一个 Mapper。多参数方法用 `@Param` 命名，让 XML 能稳定找到参数。

`PortfolioMapper.xml` **完整文件**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.quephoto.portfolio.PortfolioMapper">
  <sql id="publishedFilter">
    p.status = 'published'
    <if test="tagId != null">
      AND EXISTS (
        SELECT 1 FROM portfolio_tag pt
        WHERE pt.portfolio_id = p.id AND pt.tag_id = #{tagId}
      )
    </if>
  </sql>
  <select id="countPublished" resultType="long">
    SELECT COUNT(*) FROM portfolio p
    WHERE <include refid="publishedFilter"/>
  </select>
  <select id="findPublishedPage" resultType="com.quephoto.portfolio.PortfolioRow">
    SELECT p.id, p.title, p.location, p.shot_at, p.shot_time_precision,
           ci.object_key AS cover_object_key
    FROM portfolio p
    LEFT JOIN portfolio_image ci ON ci.id = p.cover_image_id
    WHERE <include refid="publishedFilter"/>
    ORDER BY (p.shot_at IS NULL) ASC, p.shot_at DESC, p.id DESC
    LIMIT #{limit} OFFSET #{offset}
  </select>
  <select id="findPublishedById" resultType="com.quephoto.portfolio.PortfolioRow">
    SELECT p.id, p.title, p.description, p.location, p.shot_at,
           p.shot_time_precision, ci.object_key AS cover_object_key
    FROM portfolio p
    LEFT JOIN portfolio_image ci ON ci.id = p.cover_image_id
    WHERE p.id = #{id} AND p.status = 'published'
  </select>
  <select id="findImages" resultType="com.quephoto.portfolio.PortfolioImageRow">
    SELECT id, portfolio_id, image_type, object_key, sort_order, width, height
    FROM portfolio_image WHERE portfolio_id = #{portfolioId}
    ORDER BY CASE image_type WHEN 'work' THEN 0 ELSE 1 END, sort_order, id
  </select>
  <select id="findTags" resultType="com.quephoto.portfolio.PortfolioTagRow">
    SELECT g.id AS group_id, g.name AS group_name,
           t.id AS tag_id, t.name AS tag_name
    FROM portfolio_tag pt
    JOIN tag t ON t.id = pt.tag_id AND t.group_id = pt.tag_group_id
    JOIN tag_group g ON g.id = t.group_id
    WHERE pt.portfolio_id = #{portfolioId}
    ORDER BY g.sort_order, g.id, t.sort_order, t.id
  </select>
</mapper>
```

观察 SQL 中五个有意的选择：

1. 列表和 count 引用同一个过滤片段；改筛选时不会忘记另一处。
2. `EXISTS` 只判断是否匹配标签，不放大作品行数；没有把图片或标签列表 JOIN 到分页主查询。
3. 只 JOIN 一张封面图片是多对一，不会把一个作品变成多行。
4. 日期未知最后，同日按 ID 逆序，固定数据翻页不会随机重排；OFFSET 分页不承诺在持续增删时绝对无重漏。
5. `#{...}` 是参数绑定。`${...}` 是字符串替换，不能用来接用户输入；今天排序固定。[MyBatis 参数与 XML](https://mybatis.org/mybatis-3/sqlmap-xml.html)。

### 7.2 URL 工具：只接受固定基址和原始 Key

`ImageUrlService.java` **完整文件**；不用 `URLEncoder` 编码整个路径，因为它面向表单且会把空格变成加号。

```java
package com.quephoto.oss;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
@Service
public class ImageUrlService {
    private final String baseUrl;
    public ImageUrlService(@Value("${oss.base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
            || uri.getQuery() != null || uri.getFragment() != null
            || uri.getUserInfo() != null
            || (uri.getPath() != null && !uri.getPath().isEmpty()
                && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("图片基址必须是无路径、查询和凭据的 HTTPS 域名");
        }
        this.baseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    public String url(String objectKey) {
        if (objectKey == null) return null;
        if (objectKey.startsWith("/") || objectKey.contains("://")) {
            throw new IllegalArgumentException("数据库图片 Key 非法");
        }
        return baseUrl + "/" + UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);
    }
}
```

这里生成链接，不证明对象存在。D4 的受控导入负责对象前缀、HEAD 核验及路径规范；已有图片 Key 作为未编码原始值保存，再编码一次。Key 为 `a b.jpg` 时应生成 `a%20b.jpg`，不要存已经 URL 编码的字符串。

### 7.3 Service：只在这里决定“能不能看”

`PortfolioService.java` **D2 的完整文件**；D3/D4 在本类追加管理方法，保留今天的公开过滤。

```java
package com.quephoto.portfolio;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.quephoto.common.dto.PageResponse;
import com.quephoto.oss.ImageUrlService;
import com.quephoto.portfolio.dto.PublicPortfolioDtos.*;
@Service
public class PortfolioService {
    private final PortfolioMapper mapper;
    private final ImageUrlService imageUrls;
    public PortfolioService(PortfolioMapper mapper, ImageUrlService imageUrls) {
        this.mapper = mapper;
        this.imageUrls = imageUrls;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ListItem> publicPage(int page, int pageSize, Long tagId) {
        long offset = ((long) page - 1) * pageSize;
        long total = mapper.countPublished(tagId);
        var items = new ArrayList<ListItem>();
        for (PortfolioRow row : mapper.findPublishedPage(tagId, pageSize, offset)) {
            items.add(new ListItem(row.getId(), row.getTitle(), row.getLocation(),
                row.getShotAt(), row.getShotTimePrecision(),
                imageUrls.url(row.getCoverObjectKey())));
        }
        return new PageResponse<>(items, page, pageSize, total);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail publicDetail(long id) {
        PortfolioRow row = mapper.findPublishedById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        var images = new ArrayList<Image>();
        for (PortfolioImageRow image : mapper.findImages(id)) {
            images.add(new Image(image.getId(), image.getImageType(),
                imageUrls.url(image.getObjectKey()), image.getSortOrder(),
                image.getWidth(), image.getHeight()));
        }
        var tags = new ArrayList<Tag>();
        for (PortfolioTagRow tag : mapper.findTags(id)) {
            tags.add(new Tag(tag.getGroupId(), tag.getGroupName(),
                tag.getTagId(), tag.getTagName()));
        }
        String cover = imageUrls.url(row.getCoverObjectKey());
        return new Detail(row.getId(), row.getTitle(), row.getDescription(),
            row.getLocation(), row.getShotAt(), row.getShotTimePrecision(),
            cover, cover, images, tags);
    }
}
```

构造器注入表示 Spring 负责提供 Mapper 和 URL 服务。增强 for 循环就是遍历集合；`var` 只是局部变量的编译期类型推断，不是动态类型。

只读事务让 count/list 或详情的多次查询在同一个 MySQL 一致性快照内读取；`readOnly` 是事务意图，不是权限控制。真正的公开隔离仍是 SQL 的 published 条件。方法从 Controller 调用才能经过 Spring 事务代理，别在同类内随意自调用来期待新事务生效。

详情先查公开作品，不存在就立即 404，不继续查图片和标签。草稿与不存在统一返回“作品不存在”，避免泄露管理状态。图片和标签分开查询，避免多张图片 × 多个标签产生笛卡尔积。

### 7.4 Controller：接参数，校验，交给 Service

`PortfolioController.java` **完整文件**：

```java
package com.quephoto.portfolio;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import com.quephoto.common.dto.PageResponse;
import com.quephoto.portfolio.dto.PublicPortfolioDtos.*;
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) { this.service = service; }
    @GetMapping
    public PageResponse<ListItem> list(
        @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(50) int pageSize,
        @RequestParam(name = "tagId", required = false)
            @Min(1) @Max(9007199254740991L) Long tagId) {
        return service.publicPage(page, pageSize, tagId);
    }
    @GetMapping("/{id}")
    public Detail detail(@PathVariable("id")
        @Min(1) @Max(9007199254740991L) long id) {
        return service.publicDetail(id);
    }
}
```

这里使用 Spring MVC 内建的方法参数校验，保留 Validation starter，不在 Controller 类上再加 `@Validated` 改走另一条代理路径。超大数字超过 int/long 解析范围也应是 400，而非 500。今日参数都来自 HTTP；后续离线导入直接调用 Service 时必须自行做输入校验。[Spring MVC 方法校验](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)。

Day 1 没有创建统一异常处理类。今天新建 `D:\Project\QuePhoto_java\src\main\java\com\quephoto\common\ApiExceptionHandler.java`，声明 `package com.quephoto.common;`，建立带 `@RestControllerAdvice` 的 `public class ApiExceptionHandler`，将以下两组**局部方法**放入类体。若你此前已自行建立，则合并到同一个类，不能重复注册同异常处理。导入 Spring HTTP/web bind 包及 `java.util.Map/List`：

```java
@ExceptionHandler(ResponseStatusException.class)
public ProblemDetail status(ResponseStatusException ex) {
    ProblemDetail p = ProblemDetail.forStatus(ex.getStatusCode());
    p.setTitle(ex.getReason() == null ? "请求失败" : ex.getReason());
    return p;
}
@ExceptionHandler({
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
})
public ProblemDetail badQuery(Exception ex) {
    ProblemDetail p = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    p.setTitle("请求校验失败");
    p.setProperty("errors", Map.of("query", List.of("请检查分页参数或 ID 的范围与类型。")));
    return p;
}
```

准确导入路径：`org.springframework.web.method.annotation.HandlerMethodValidationException`、`org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`、`org.springframework.web.server.ResponseStatusException`、`org.springframework.http.ProblemDetail`、`org.springframework.http.HttpStatus`、`org.springframework.web.bind.annotation.ExceptionHandler`。D3 再补请求体校验和业务错误；如果已有方法覆盖同一异常，合并它，别创建重复处理器。

启动后数据库为空时访问列表，应该已经得到 `{"items":[],"page":1,"pageSize":20,"totalCount":0}`。此时先证明链路正确，再加样例数据。

## 8. 用两次查询组装标签树

标签树保留没有子标签的组，返回数组，每个组包含 `id/name/sortOrder/tags`，标签包含 `id/name/sortOrder`。P0 只读，D3 的管理标签 GET 复用同一 Service。

`TagMapper.java` **完整文件**：

```java
package com.quephoto.tag;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TagMapper {
    List<TagGroupRow> findGroups();
    List<TagRow> findAllTags();
}
```

`TagMapper.xml` **完整文件**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.quephoto.tag.TagMapper">
  <select id="findGroups" resultType="com.quephoto.tag.TagGroupRow">
    SELECT id, name, sort_order FROM tag_group ORDER BY sort_order, id
  </select>
  <select id="findAllTags" resultType="com.quephoto.tag.TagRow">
    SELECT id, group_id, name, sort_order FROM tag ORDER BY sort_order, id
  </select>
</mapper>
```

`TagTreeDtos.java` **完整文件**：

```java
package com.quephoto.tag.dto;
import java.util.List;
public final class TagTreeDtos {
    private TagTreeDtos() {}
    public record TagItem(Long id, String name, Integer sortOrder) {}
    public record Group(Long id, String name, Integer sortOrder, List<TagItem> tags) {}
}
```

`TagService.java` 添加 `@Service`、通过构造器注入 `TagMapper mapper`；下面是**完整 tree 方法**，导入 `java.util.*` 和 `com.quephoto.tag.dto.TagTreeDtos.*`，事务注解与上一节相同：

```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public List<Group> tree() {
    var groups = mapper.findGroups();
    var tags = mapper.findAllTags();
    Map<Long, List<TagItem>> byGroup = new HashMap<>();
    for (TagRow tag : tags) {
        byGroup.computeIfAbsent(tag.getGroupId(), ignored -> new ArrayList<>())
            .add(new TagItem(tag.getId(), tag.getName(), tag.getSortOrder()));
    }
    var result = new ArrayList<Group>();
    for (TagGroupRow group : groups) {
        result.add(new Group(group.getId(), group.getName(), group.getSortOrder(),
            byGroup.getOrDefault(group.getId(), List.of())));
    }
    return result;
}
```

`computeIfAbsent` 表示“这组还没有列表就创建一个，再把标签加进去”。它不是数据库调用。组顺序由第一个查询决定，组内标签的相对顺序来自第二个查询；HashMap 的遍历顺序没有参与输出，所以不会打乱排序。

最后按 `PortfolioController` 的结构创建 `TagController`：构造器注入 `TagController`，`@RequestMapping("/api/tag-groups")`，一个 `@GetMapping public List<Group> tree() { return service.tree(); }`。请求空库应得到 `[]`。

观察 SQL 日志时，一次标签树请求应是两条 SELECT，不是每个组再发一条查询。不要为这点数据引入缓存。

## 9. 手动写入一次本地样例，验证真实关系

以下内容保存为 `examples/local-only-day02.sql`，只在新建的 `quephoto_dev` 开发库执行一次。文件位于 resources 外，不会在生产启动执行；DDL 迁移里不能包含它。

先执行 `SELECT DATABASE(); SELECT COUNT(*) FROM portfolio;`，确认数据库名称是 `quephoto_dev` 且业务表为空。若已有内容，另建独立练习库并用同一 V1 初始化，不能为重新练习清空已有作品。

```sql
USE quephoto_dev;
START TRANSACTION;
INSERT INTO tag_group (id, name, sort_order, created_at) VALUES
  (11, '题材', 10, UTC_TIMESTAMP(3)),
  (12, '地点类型', 20, UTC_TIMESTAMP(3)),
  (13, '暂未配置', 30, UTC_TIMESTAMP(3));
INSERT INTO tag (id, group_id, name, sort_order, created_at) VALUES
  (111, 11, '街拍', 10, UTC_TIMESTAMP(3)),
  (112, 11, '风景', 20, UTC_TIMESTAMP(3)),
  (121, 12, '城市', 10, UTC_TIMESTAMP(3));
INSERT INTO portfolio
  (id, title, description, location, shot_at, shot_time_precision,
   status, created_at, updated_at) VALUES
  (1001, '雨夜', '本地样例', '上海', '2026-09-01 00:00:00', 'month',
   'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (1002, '未知日期', NULL, NULL, NULL, NULL,
   'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (1003, '未公开作品', NULL, '杭州', '2026-10-01 00:00:00', 'day',
   'draft', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO portfolio_image
  (id, portfolio_id, image_type, object_key, sort_order, width, height, created_at)
VALUES
  (2001, 1001, 'work', 'photos/local-only/A.jpg', 20, 1200, 800, UTC_TIMESTAMP(3)),
  (2002, 1001, 'scene', 'photos/local-only/B.jpg', 0, NULL, NULL, UTC_TIMESTAMP(3)),
  (2003, 1002, 'work', 'photos/local-only/a.jpg', 10, NULL, NULL, UTC_TIMESTAMP(3));
UPDATE portfolio SET cover_image_id = 2001, status = 'published',
  updated_at = UTC_TIMESTAMP(3) WHERE id = 1001;
UPDATE portfolio SET cover_image_id = 2003, status = 'published',
  updated_at = UTC_TIMESTAMP(3) WHERE id = 1002;
INSERT INTO portfolio_tag (portfolio_id, tag_group_id, tag_id) VALUES
  (1001, 11, 111), (1001, 12, 121), (1002, 11, 112), (1003, 11, 111);
COMMIT;
```

为什么选这三条：1001 同时有两张图和两个标签，能暴露错误 JOIN 的重复计数；1002 没有拍摄时间，能验证未知最后；1003 的日期最新且匹配街拍标签，却仍然必须隐藏。两个大小写不同的 Key 都可保存，是 Key 比较规则的直接证据。样例图并不存在于 OSS，不能据此宣称图片已可访问。

数据库客户端可打开 SQL 文件再执行。如果使用 mysql 命令行，密码用交互提示输入：

```powershell
mysql --host=127.0.0.1 --user=quephoto_dev --password quephoto_dev
```

在 mysql 提示符执行 `SOURCE D:/Project/QuePhoto_java/examples/local-only-day02.sql;`。这是 mysql 客户端命令，不是在 PowerShell 直接运行 SOURCE。

## 10. 按预期结果验收，别只看“没有报错”

在应用运行时另开 PowerShell；`curl.exe` 明确使用 curl 可执行文件，避免 PowerShell 的同名别名：

```powershell
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?page=1&pageSize=1'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?page=2&pageSize=1'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?tagId=111'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios/1001'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios/1003'
curl.exe -i 'http://127.0.0.1:8080/api/tag-groups'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?page=0'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?pageSize=51'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios?tagId=abc'
curl.exe -i 'http://127.0.0.1:8080/api/portfolios/9007199254740992'
```

| 请求/观察点 | 预期 |
|---|---|
| 第 1 页，每页 1 条 | 200；items 只有 1001；totalCount=2 |
| 第 2 页，每页 1 条 | 200；items 只有 1002；totalCount 仍为 2 |
| 第 3 页，每页 1 条 | 200；items=[]；page=3；totalCount 仍为 2 |
| tagId=111 | 只有 1001；totalCount=1；1003 不出现 |
| tagId=999999 | 200 空分页，不是 404，也不能退回所有作品 |
| 1001 详情 | 一个 images 数组；2001 work 在 2002 scene 前，尽管 scene 的 sortOrder=0 |
| 1001 标签 | 两个标签，groupId/groupName/tagId/tagName 齐全 |
| 1001 分享图 | shareImageUrl 与 coverImageUrl 相同 |
| 1003、999999 详情 | 都为 404；响应不要说明前者是草稿 |
| 标签树 | 3 个组，按 sortOrder/id；第 3 组 tags=[] |
| page=0/pageSize=51/tagId=abc/超安全整数 ID | 400；带 title/status/errors 的错误对象 |
| 全部公开响应 | 没有 status、coverImageId、objectKey、shareImageKey |
| 拍摄时间 | 无 Z；month 精度仍保留完整占位时间，由将来前端按精度展示 |

DTO 不带 objectKey 并不意味着 URL 中无法看到 Key；图片保密不是这个字段拆分所提供的能力。今天只保证草稿元数据不可通过公开 API 读取。

再用数据库验证约束，**每个场景单独执行**，预期失败后执行 ROLLBACK，不依赖客户端遇错后的行为：

```sql
START TRANSACTION;
UPDATE portfolio_image SET height = NULL WHERE id = 2001;
-- 应被 ck_image_dimensions 拒绝，不能接受 1200/null。
ROLLBACK;

START TRANSACTION;
UPDATE portfolio SET shot_time_precision = NULL WHERE id = 1001;
-- 应被 ck_portfolio_shot_pair 拒绝，不能接受有时间无精度。
ROLLBACK;

START TRANSACTION;
INSERT INTO portfolio_tag VALUES (1002, 12, 111);
-- 组 12 存在、标签 111 存在，但 111 属于组 11；组合外键应拒绝。
ROLLBACK;

START TRANSACTION;
INSERT INTO portfolio_tag VALUES (1001, 11, 112);
-- 1001 在组 11 已选 111；主键应拒绝同组第二个标签。
ROLLBACK;
```

在 `docs/day02-evidence.md` 逐项写“预期/实际/请求或 SQL/结果摘要”。至少保存一个真实分页响应、一个 404、一个 400 和两个 CHECK 拒绝结果。不要复制密码或整个连接环境。

## 11. 常见卡点：先找哪一层

| 现象 | 先检查什么 | 应采取的动作 |
|---|---|---|
| Failed to configure a DataSource | local profile 和当前终端的 DB 环境变量 | 启动终端重设变量；别硬编码密码 |
| Communications link failure | MySQL 是否运行、3306 是否为本地监听 | 先用相同账号在客户端登录，再看 Java |
| Unsupported Database / 未执行迁移 | Flyway starter 与 flyway-mysql | 查 dependency:tree；不要换成手工建表 |
| Flyway checksum mismatch | 是否改了已执行 V1 | 查版本差异；共享迁移用 V2，不盲目 repair |
| Invalid bound statement | namespace、方法名、mapper-locations | 三处与 PortfolioMapper 完全对齐 |
| 参数 tagId not found | @Param 名称是否一致 | XML 与方法标注逐字比较 |
| 字段全部/部分为 null | getter/setter、snake_case 映射、SELECT 别名 | 先查 SQL 结果，再检查 Bean 属性 |
| 列表总数大于作品数 | 是否 JOIN 全部图片/标签 | 回到 EXISTS 与只 JOIN 封面的查询 |
| page=0 返回 200 | Validation starter、方法约束是否保留 | 不手写“收到错误仍返回空数组”补丁 |
| 请求没有错误但图片打不开 | 当前是 example.invalid / 假 Key | D2 预期如此；D4 才验证真实对象 |
| 测试 contextLoads 突然失败 | 加数据库后测试也需要数据源 | D5 配独立 test MySQL；今日运行时检查不可伪称全套测试通过 |

遇错超过 15 分钟，写一个最小复现：命令、最后一个 caused by、当前文件片段、期待结果。一次只改一个原因，不同时换驱动、版本、数据库和 SQL。无需公开敏感配置即可排障。

## 12. 闭卷练习：先遮住答案，做完再核对

1. 用 SQL 查 1001 的图片，让 work 在 scene 前；解释为什么不能只写 `ORDER BY image_type`。
2. 不看上文，计算 page=3、pageSize=20 的 offset，并说明强制转 long 写在乘法前的原因。
3. 解释为什么“标签 111 存在且组 12 存在”还不够，为什么 `portfolio_tag` 不能只用 `(portfolio_id, tag_id)` 做主键。
4. 写出代表未知宽高的合法数据和两种非法数据。解释 CHECK 的 UNKNOWN 为何危险。
5. 在 IDE 给 ListItem 增加 `String description`，把列表 SELECT、DTO 组装一起临时改通，查看响应，然后决定是否保留；本周冻结契约仍以原列表字段为准，练习结束恢复该实验。
6. 用一句话分别解释 Row、DTO、Mapper、Service、Controller；指出公开详情查草稿时在哪一步停止。

提示：第 1 题用 CASE；第 2 题先减 1；第 3 题考虑“一个作品选了同组两个不同标签”；第 5 题刻意让你体验契约变更会跨越哪几层，不只改 record。

<details>
<summary>参考答案（先尝试，再展开）</summary>

1. `SELECT * FROM portfolio_image WHERE portfolio_id=1001 ORDER BY CASE image_type WHEN 'work' THEN 0 ELSE 1 END, sort_order, id;`。字母顺序 scene 会在 work 前，和业务要求相反。
2. offset=40；`((long) page - 1) * pageSize` 防止中间 int 乘法先溢出再转 long。
3. 组合外键保证“这个标签属于这个组”，主键 `(portfolio_id, tag_group_id)` 保证“这个作品每组最多一项”。
4. 合法 null/null 或 1200/800；非法 1200/null、0/800。必须明确要求有值分支双方 IS NOT NULL，不能让比较结果 UNKNOWN 被接受。
5. 要同步 SELECT description、Row.getDescription、record 字段和 new ListItem 的构造器参数。若只加 DTO 字段，Java 编译器会提示构造器不匹配。
6. Row 表示持久化行；DTO 表示接口数据；Mapper 执行 SQL；Service 组装并实施业务边界；Controller 负责 HTTP 与入参。findPublishedById 返回 null 后，Service 直接抛 404，不调用图片/标签查询。

</details>

## 13. 今天必须通过的交付，以及明天接着改哪里

- [ ] 新空库仅靠 Flyway 创建五张业务表，重复启动不重复迁移。
- [ ] 三个公开 GET 已实际请求通过；列表和筛选不泄露草稿。
- [ ] 日期未知最后；同日期 ID 决定顺序；图片 work 在 scene 前。
- [ ] count 和列表共用筛选，数据库分页，空分页仍含四个字段。
- [ ] 五表的 FK/唯一键/CHECK 已用真实 MySQL 8.4 验证。
- [ ] 正常启动没有任何开发种子执行器；SQL 样例未放进生产 classpath。
- [ ] 本人能解释一条请求的五层流转，并独立完成至少两道练习。
- [ ] 证据文件写的是实际结果；没跑过的测试明确标“未执行”。

交给 D3 的边界固定如下，避免重新造一套类：

| 已有类/接口 | D2 已有内容 | D3/D4 追加方向 |
|---|---|---|
| `PortfolioRow` | 五表建模中的作品字段、封面 Key 投影；可变 Bean | 保留 String 枚举字段；管理查询补齐 SELECT 列 |
| `PortfolioMapper` | countPublished、findPublishedPage、findPublishedById、findImages、findTags | 独立管理查询、草稿 INSERT、更新、FOR UPDATE 锁行；不能删除公开 published 条件 |
| `PortfolioService` | publicPage、publicDetail，公开 DTO 组装 | 同类新增管理方法、事务与发布规则，不让管理 Controller 调公开详情获取草稿 |
| `PageResponse<T>` | items/page/pageSize/totalCount | 管理分页复用相同结构 |
| `PublicPortfolioDtos` | 公开契约没有原始 Key/status | 另建管理 DTO；不要在公开 DTO 上加管理字段 |
| `TagService.tree()` | 无写入的标签树 | 管理 GET 复用方法，但 HTTP 路由仍受鉴权 |
| `ImageUrlService.url()` | 固定 HTTPS 域名 + 原始 Key | D4 增加独立 OSS 核验服务，不把 URL 工具变上传接口 |

今天新增的产品接口只有 `GET /api/portfolios`、`GET /api/portfolios/{id}`、`GET /api/tag-groups`，仍属于全周 12 个 method+path 约定。明天先加鉴权，再开放管理写入；不因为已连数据库而提前放开匿名 POST、在线上传或 DELETE。

结束前填写：实际用时、最难解释的 Java 概念、一个已定位错误、未通过的验收、明天第一步。保留这份记录，D7 用它验证你是否真的能独立维护。


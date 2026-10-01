# Day 1：从一段 Java 代码到第一个 QuePhoto 接口

目标日期：2026-10-01，可整体顺延。今天按 4 小时安排；只有 3 小时时，服务器准备移到次日明确补齐，最晚 D3 完成。不要省掉本地验证。

今天交付：一个能在终端构建、启动和调用的 Java 服务；一个你能自己修改的 DTO；可连接的 MySQL；服务器准备记录。尚不实现登录、作品数据库或上传。

Spring Boot 基础工程已导入本项目根目录，原来的 Java 练习代码保存在 practice。下面的接口、配置和验收任务仍需按步骤完成；导入工程不代表 Day 1 已全部完成。

导航：[教程目录](D:/Project/QuePhoto_java/Plan/7天实战教程/README.md) · [下一天：数据库与公开查询](D:/Project/QuePhoto_java/Plan/7天实战教程/Day02_数据库建模与公开查询.md)

## 1. 开始前：今天到底要理解什么

你已经用过其他语言，不必重新花一天学变量和循环。今天重点理解 Java 项目的运行方式，以及“普通 Java 对象怎样成为 HTTP 返回值”。

```text
普通程序：.java 源码 → javac 编译 → .class 字节码 → JVM 运行
Web 项目：源码 + 依赖 → Maven 构建 → 可运行 JAR → 内嵌 HTTP 服务器
请求流程：HTTP GET → Controller 方法 → DTO 对象 → JSON 响应
```

| 时间 | 动作 | 必须留下的成果 |
|---|---|---|
| 0:00–0:20 | 确认工具和目录 | Java/Maven 使用的 JDK 明确 |
| 0:20–1:00 | 写普通 Java 小程序 | 能独立修改类、List 和筛选条件 |
| 1:00–2:10 | 确认工程、写接口、启动 | GET 返回自己的 JSON |
| 2:10–2:40 | 连接 MySQL、建空库 | MySQL 8.4 可用，记录连接方式 |
| 2:40–3:20 | 服务器准备、构建 JAR、Git | 基础设施待办与可运行产物 |
| 3:20–4:00 | 验证、排错、闭卷练习 | 今日验收与学习记录 |

## 2. 验证现有工具，不重复安装

### 要做什么、为什么

先保证终端、IDE、Maven 都使用同一个 JDK。否则 IDE 能运行、终端却编译失败，会掩盖真正的业务问题。

本次只读检查已经发现：`D:\Java\bin\java.exe` 和 `javac.exe` 为 Temurin 21.0.10；Git 已安装；Docker CLI 存在，但未确认 Docker 引擎可用。你不必先重装 Java。执行当天仍应复查版本。

### 怎么做

在 **Windows PowerShell** 运行：

```powershell
Set-Location 'D:\Project\QuePhoto_java'
Get-Command java,javac,git
java -version
javac -version
git --version
```

预期：`java` 和 `javac` 都是 21。若 Java 不存在或版本不符，使用 [Temurin 官方安装说明](https://adoptium.net/installation/) 安装/选择 JDK 21；关闭并重新打开终端再检查。不要在不同位置反复安装多个 JDK。

IDE 选择你熟悉的 Java IDE，例如 IntelliJ IDEA。打开设置，把 Project SDK、语言级别和 Maven Runner JRE 都设为 JDK 21；不用依赖付费 Spring 功能，命令行仍可启动项目。

## 3. 用 40 分钟迁移已有语言知识

### 继续普通 Java 练习

原来的练习代码已保存在 `D:\Project\QuePhoto_java\practice\PortfolioPractice.java`。对照下面的**完整单文件程序**继续练习，保留你已有的修改。它不连接数据库，也不属于生产代码。

```java
import java.util.ArrayList;
import java.util.List;

public class PortfolioPractice {
    public static void main(String[] args) {
        List<Work> works = new ArrayList<>();
        works.add(new Work("城市雨夜", "上海"));
        works.add(new Work("山间清晨", "杭州"));

        for (Work work : works) {
            if ("上海".equals(work.getLocation())) {
                System.out.println(work.getTitle());
            }
        }
    }
}

class Work {
    private final String title;
    private final String location;

    Work(String title, String location) {
        this.title = title;
        this.location = location;
    }

    String getTitle() {
        return title;
    }

    String getLocation() {
        return location;
    }
}
```

在 PowerShell 中编译运行：

```powershell
Set-Location 'D:\Project\QuePhoto_java\practice'
New-Item -ItemType Directory -Path out -Force
javac -encoding UTF-8 -d out PortfolioPractice.java
java -cp out PortfolioPractice
```

预期只打印 `城市雨夜`。`-d out` 指定字节码输出位置，`-cp out` 告诉 Java 去哪里找类。启动时写类名，不写 `.class` 后缀。

### 为什么这样写

| 代码 | 现在需要掌握的含义 |
|---|---|
| `public class PortfolioPractice` | 公开类名与文件名一致；`main` 是普通程序入口 |
| `List<Work>` | 泛型限定元素类型，拿到的元素无需手工强制转换 |
| `new ArrayList<>()` | List 是接口，ArrayList 是一种实现；可以先只会用 |
| `private final String` | 字段只允许类内访问，引用在构造后不重新赋值；不等于整个对象图都不可变 |
| `this.title = title` | 区分对象字段与构造器参数 |
| `"上海".equals(...)` | 比较字符串内容；`==` 通常比较对象引用，不能替代内容判断 |
| `for (Work work : works)` | 遍历集合中的元素，相当于常见语言的 foreach |

亲手改三件事：增加第三个作品、把筛选条件改成杭州、增加一个 `int imageCount` 字段并打印。先自己写，报错后再查对应语法。

今天暂不学继承体系、并发和 Stream。普通循环已经能把当前任务写清楚。

## 4. 确认已有后端工程

### 确认生成配置

现有基础工程已通过 [Spring Initializr](https://start.spring.io) 生成并导入，**无需再次生成项目**。下面保留生成配置供核对：

| 配置 | 值 |
|---|---|
| Project / Language | Maven / Java |
| Spring Boot | 4.0.x 的稳定补丁；本教程核验的候选为 4.0.8，不选 SNAPSHOT/RC |
| Group | `com.quephoto` |
| Artifact / Name | `quephoto` / `QuePhoto` |
| Package name | `com.quephoto` |
| Packaging / Java | Jar / 21 |
| Dependencies | Spring Web（MVC）、Validation |

生成的项目现已并入 **`D:\Project\QuePhoto_java` 根目录**。该目录直接包含 `pom.xml`、`mvnw.cmd`、`mvnw`、隐藏目录 `.mvn` 和 `src`；`Docs` 保留教程，`practice` 保存原来的 Java 练习代码。后续始终使用这个 Maven 工程，在 IDEA 中通过根目录的 `pom.xml` 导入。

如果页面已不提供教程候选版本，先查官方受支持稳定版本和 MyBatis 兼容矩阵，统一修改版本记录后再继续；不要一部分依赖用 3.x、一部分用 4.x。版本选择是一次性准备，不是每天更换框架。

**今天只引入 Web 与 Validation。** Day 2 配好数据库再加 MyBatis/Flyway；Day 3 再加 Security。否则“启动时找不到数据库”“默认登录拦截”会在你尚未学习相关概念时一起出现。

### 看懂项目文件

| 路径（均在上述工程目录下） | 作用 |
|---|---|
| `pom.xml` | 项目信息、Java 版本、依赖、构建插件 |
| `mvnw.cmd` / `mvnw` | Windows / Linux 的 Maven Wrapper 入口，固定构建工具 |
| `src/main/java` | 生产 Java 源码 |
| `src/main/resources` | 配置文件，以后放 SQL 迁移、Mapper XML |
| `src/test/java` | 自动化测试，不进入普通业务入口 |
| `target` | 构建输出，可重建，不提交 Git |

在 `D:\Project\QuePhoto_java\pom.xml` 查看，而不是盲目覆盖生成内容：

```xml
<!-- 局部示例：放在已有 dependencies 内，不再包一层 dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Boot 4 使用 `spring-boot-starter-webmvc`；若生成器使用兼容的旧 starter 名称，按选定版本官方说明统一。测试依赖保留生成器提供的配置。官方依据：[Boot 构建依赖说明](https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html)。

在已有 `<build>` 内添加 `<finalName>quephoto</finalName>`，保留 Spring Boot Maven 插件。这样后续每天统一使用 `target/quephoto.jar`，不用反复猜版本文件名。

## 5. 写第一个有用但很小的接口

### 5.1 确认启动类

文件：`D:\Project\QuePhoto_java\src\main\java\com\quephoto\QuePhotoApplication.java`。

以下是**完整类示例**。如果生成器已经创建同职责的启动类，使用 IDE 重命名成这个名字并保留一个启动类，不新增第二个。

```java
package com.quephoto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuePhotoApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuePhotoApplication.class, args);
    }
}
```

`@SpringBootApplication` 让 Boot 建立应用上下文并扫描本包和子包。把 Controller 放在 `com.quephoto` 的子包中，避免因为扫描范围不对而 404。

### 5.2 定义 DTO

文件：`D:\Project\QuePhoto_java\src\main\java\com\quephoto\common\dto\HelloResponse.java`。

```java
package com.quephoto.common.dto;

public record HelloResponse(String application, String message) {
}
```

这是完整类。record 是适合简单数据载体的 Java 语法：编译器提供构造器、访问方法、equals 等。`response.message()` 是访问方法，不是 `getMessage()`；Day 2 的数据库对象仍会用普通可变类，不把 record 到处套用。

### 5.3 定义 Controller

文件：`D:\Project\QuePhoto_java\src\main\java\com\quephoto\common\HelloController.java`。

```java
package com.quephoto.common;

import com.quephoto.common.dto.HelloResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/api/hello")
    public HelloResponse hello(
            @RequestParam(defaultValue = "QuePhoto") String name) {
        return new HelloResponse("QuePhoto", "Hello, " + name);
    }
}
```

这是完整类。注解声明路由；Spring 创建并调用它，你不需要在 `main` 中 `new HelloController()`。返回的是 Java 对象，Web 层负责把它序列化为 JSON；不要手写字符串拼接 JSON。

此路由仅用于今天的学习，D3 从公网白名单移除，D5 删除或明确留作本地诊断，不计入 P0 的 12 个业务接口。

### 5.4 配置本机监听

文件：`D:\Project\QuePhoto_java\src\main\resources\application.yml`。

```yaml
spring:
  application:
    name: quephoto
server:
  address: 127.0.0.1
  port: 8080
```

如果已有 `application.yml`，把有用配置迁入这个文件并避免在两边重复定义相同键。今天的服务尚无鉴权，只允许本机访问；D6 容器环境会通过 `application-prod.yml` 覆盖监听地址，宿主入口仍受控。

## 6. 从终端运行，看到真正的 HTTP 请求

打开 PowerShell 窗口 A：

```powershell
Set-Location 'D:\Project\QuePhoto_java'
.\mvnw.cmd -v
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

第一次会下载依赖。等待日志中出现应用启动成功和 8080 端口，不要把下载进度当报错。`-v` 显示的 Java 必须也是 21。

另开 PowerShell 窗口 B：

```powershell
$response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/hello?name=photographer'
$response.StatusCode
$response.Content
```

预期状态码 `200`，JSON 内容等价于：

```json
{"application":"QuePhoto","message":"Hello, photographer"}
```

JSON 属性顺序不作为验收条件。分别省略 name、改为另一个 name、访问不存在的路径，观察默认值和 404。暂不要求框架默认错误体与最终业务契约一致，Day 2–3 会统一。

在窗口 A 按 Ctrl+C 停服务，然后运行：

```powershell
.\mvnw.cmd clean package
java -jar .\target\quephoto.jar
```

再次调用同一 URL。为什么再启动一次 JAR？因为服务器最终运行构建产物，不运行 IDE；这一步提前验证构建和运行是两件事。

## 7. 让 MySQL 明天能用

今天只连接数据库和创建空库，表结构全部留给 Day 2 的 Flyway。选择以下一条你能最快跑通的路线，不同时搭两套。

**已有 MySQL 8.4：直接使用。没有则安装 MySQL 官方社区版，或者复用已能正常运行的 Docker。** Windows 安装时通过安装向导设置本地密码；若 Docker/WSL 排障超过 30 分钟，改用本机 MySQL，别让环境问题吞掉整个假期。

使用 MySQL 客户端交互登录（密码在提示中输入，不附在命令参数）：

```powershell
mysql -u root -p
```

在 MySQL 提示符中运行：

```sql
SELECT VERSION();
CREATE DATABASE IF NOT EXISTS quephoto_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SHOW DATABASES LIKE 'quephoto_dev';
```

预期版本属于 8.4，能查到 `quephoto_dev`。这里尚未创建表。Day 2 再创建受限应用账号、映射连接配置、增加 MyBatis/Flyway 依赖。不要直接用 root 账号作为长期业务连接。

Docker 路线必须同样满足：MySQL 8.4、持久卷、本地回环绑定、能交互登录；其容器名和端口记在本地环境笔记中。执行前先 `docker version` 确认 Client 和 Server 都有输出；只显示 Client 不代表数据库能启动。镜像与命令以 [MySQL 官方安装说明](https://dev.mysql.com/doc/refman/8.4/en/installing.html)和 [Docker 官方 MySQL 镜像说明](https://hub.docker.com/_/mysql)为准。

## 8. 项目保存和基础设施准备

### Git 保存

在项目或外层工作目录只建立一个你计划长期使用的 Git 仓库。先用 `git rev-parse --show-toplevel` 判断是否已有父级仓库，再决定是否 `git init`；不要在仓库里无意创建嵌套仓库。

`.gitignore` 至少覆盖：`target/`、`practice/out/`、`.idea/` 中的个人配置、`.env`、真实凭据文件、HTTP 客户端私有环境。保留 Maven Wrapper 的 `.mvn/` 文件，不要把它误当缓存删掉。

先 `git status`，只暂存今天检查过的源文件和配置模板，然后记录提交，例如 `feat: 创建Java服务与首个接口`。真实 token、数据库密码和 OSS 凭据不提交。

### 服务器与域名准备

今天要做的是确定和购买必要资源，不在本地先模拟一套复杂云架构。低访问量起点可按 Linux 单机 2 vCPU/4GB 估算，先看你购买时的真实预算和套餐；不要同时购买 Redis、负载均衡或多台机器。

记录以下非敏感信息：地域/系统、登录用户名、SSH 方式、IP、域名控制台、DNS/所需手续状态、现有 OSS 的 region 和图片域名。密钥文件另行受保护保存。

域名尚未完成不阻止本周私人管理使用；Day 6 主路线是 SSH 隧道。今日服务器若没有准备好，明确留一个 D2/D3 待办，别把这件事默认当成完成。

## 9. 遇到错误按这个顺序查

| 现象 | 先检查 | 为什么 |
|---|---|---|
| `java` 找不到 | Get-Command、终端是否重开、JDK 路径 | PATH 尚未指向 JDK |
| `invalid target release: 21` | `mvnw.cmd -v` 的 Java 版本 | Maven 可能使用另一个 JDK |
| 找不到 `pom.xml` | 当前是否在 QuePhoto_java 根目录 | Wrapper 不会替你找正确项目 |
| 找不到 Wrapper 配置 | 解压时是否漏了 `.mvn` | 隐藏目录也是项目文件 |
| 8080 被占用 | 是否同时开了 IDE、spring-boot:run 和 JAR | 同一端口不能被多个服务监听 |
| 接口 404 | URL、注解、Controller 是否在 com.quephoto 子包 | 路由或扫描范围不匹配 |
| 启动要求数据库或登录 | 是否提前加入 DB/Security starter | 今天只需要 MVC/Validation |
| MySQL 连接失败 | 服务是否运行、端口、账户、密码 | 先解决数据库连通再接 Java |

可以用 `Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue` 查看端口占用。先识别自己启动的进程，优先在对应终端 Ctrl+C；不要随意结束不认识的进程。

## 10. 今日验收与学习考核

- [ ] java/javac/Maven/IDE 都使用 Java 21。
- [ ] 普通 Java 练习运行成功，并亲手增加过字段和筛选。
- [ ] `mvnw.cmd test` 与 `clean package` 成功；记录实际执行结果。
- [ ] JAR 能独立启动，GET `/api/hello` 返回自己的 JSON。
- [ ] 能连接 MySQL 8.4，空库 `quephoto_dev` 已准备好。
- [ ] 服务器准备情况明确，没有把未完成项写成已完成。
- [ ] 能说明代码从 HTTP 进入哪个方法，以及 JSON 从何而来。

**闭卷任务**：给 HelloResponse 加 `int apiVersion`，Controller 返回 1；停止旧进程、重新构建、重新运行，验证响应多出该字段。不要让 AI 直接完成修改。

提示：record 的参数列表变了，创建它的构造器调用也必须跟着变。只改文件但不重新构建，旧 JAR 不会自动更新。

**自问答案**：Maven 管依赖与构建，JVM 运行字节码；Controller 处理请求，DTO 描述对外数据；record 不替代所有 Entity；字符串内容比较用 equals；今天没有操作作品数据库。

明天从这个工程继续：增加数据库依赖、创建 5 表、实现真实公开查询。不要另建第二个 Spring Boot 项目。

官方参考仅在用到时阅读：[Java 学习入口](https://dev.java/learn/)、[Boot 4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[Spring REST 入门](https://spring.io/guides/gs/rest-service/)。本教程示例是为 QuePhoto 编写的练习，不代表已在你的新后端工程完成运行验证。

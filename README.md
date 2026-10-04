# QuePhoto

摄影作品网站的 Spring Boot 后端。项目根目录为 `D:\Project\QuePhoto_Java`，在此目录运行 Maven 命令，无需创建 `quephoto-backend` 子工程。

## 当前结构

- `pom.xml`：Java 21、Spring Boot 4.0.8、Spring Web MVC、Validation。
- `src/main/java/com/quephoto/QuePhotoApplication.java`：服务启动入口。
- `src/main/resources/application.yml`：服务配置。
- `src/test/java/com/quephoto/QuePhotoApplicationTests.java`：应用上下文启动测试。
- `practice/`：原来的普通 Java 练习，不参与 Maven 构建。
- `Docs/Plan/`：学习计划与七天实战教程。

项目正在按七天教程开发，当前包含公开查询和管理员认证等模块；各项功能以实际实现与验收记录为准。

## 在 IDEA 中打开

打开根目录的 `pom.xml`，选择作为项目打开并等待 Maven 同步。若已经打开本目录，可右键 `pom.xml` 选择 **Add as Maven Project（添加为 Maven 项目）**；已经识别为 Maven 时，点击 Maven 工具窗口的重新加载按钮。Project SDK 和 Maven Runner JRE 使用 JDK 21。

服务入口是 `com.quephoto.QuePhotoApplication`。本地开发推荐使用下面的启动脚本准备配置；直接在 IDEA 中运行入口类需要另外设置 `local` profile 和所需环境变量。现有 `Main` 和 `PortfolioPractice` 运行配置属于之前的练习。

## 构建和运行

在项目根目录的 PowerShell 中执行：

```powershell
.\mvnw.cmd clean verify
java -jar .\target\quephoto.jar
```

本地开发启动（首次交互配置，之后自动加载加密凭据）：

```powershell
.\start-local.cmd
```

默认地址为 `http://127.0.0.1:8080`。终端按 `Ctrl+C` 停止服务。修改保存的配置可运行 `.\start-local.cmd -Configure`；变量清单、存储方式和更多命令见 [Windows 本地启动](Docs/local-startup.md)。直接运行 JAR 时同样需要自行提供配置和 profile。

普通 Java 练习可以单独运行：

```powershell
java .\practice\PortfolioPractice.java
```

## 迁移备份

2026-10-01 从 `D:\Project\QuePhoto1\QuePhoto` 导入工程骨架，源目录保留。原练习源码、IDEA 配置、`.gitignore` 及本次修改的教程原文保存在 `.migration-backup/2026-10-01/`，该备份目录不会提交到 Git。

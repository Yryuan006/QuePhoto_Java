# Day 06：服务器部署与备份恢复

今天把前五天的 Java 后端搬到服务器，并证明数据能够恢复。教程中的代码和命令是实施材料，**尚未执行，也不表示服务器已购买或部署成功**。

目标时间为 4 小时：准备与学习 30 分钟、打包和部署 100 分钟、真实访问和导入 30 分钟、备份恢复 50 分钟、缓冲 30 分钟。首次配置服务器很容易超时，4 小时是挑战目标；只有 3 小时时，保留恢复验收，将剩余内容顺延。不要省掉恢复来制造“上线完成”。

## 1. 开始前：确认今天不是重新开发业务

先检查 Day 05 的验收记录。以下条件缺一项，就先修复该项，再继续部署：

- [ ] Java 21、Spring Boot 4.0.x、MyBatis、MySQL 8.4 的版本已在项目中固定。
- [ ] `D:\Project\QuePhoto_java` 能运行测试、打包为 `target\quephoto.jar`。
- [ ] JWT 管理接口、公开 API、`local,import` 导入已经在本地走通。
- [ ] 导入器只在 `import` profile 中运行；正常 Web 启动不写演示数据。
- [ ] 自定义配置从 Spring 配置对象读取，不能直接使用 `System.getenv("OSS_ACCESS_KEY_SECRET")`。
- [ ] 有一台本人可通过 SSH 密钥登录的 Linux 服务器；本教程主线按 Ubuntu 24.04 LTS、x86_64 编写。
- [ ] 已确认真实 OSS 的 region、endpoint、bucket、图片访问域名、允许目录 `photos/`、只读核验凭据。

为什么最后两项要提前完成：服务器账号、网络和 OSS 权限属于外部条件，改 Java 代码无法解决。服务器尚未准备好时，可以完成本地打包和配置文件，但不能勾选服务器验收。

今天的访问路径是：

```text
Windows HTTP 客户端 → 本地 127.0.0.1:18080
 → SSH 加密隧道 → 服务器 127.0.0.1:18080
 → Nginx:80 → Java:8080 → MySQL:3306
```

Nginx、Java、MySQL 在同一 Docker bridge 网络中；只有 Nginx 映射到宿主回环地址。Java 和 MySQL 没有宿主端口。网络保留出站能力，否则离线导入不能请求 OSS HEAD；不要随手改成 `internal: true`。

## 2. 先理解四件事，再执行命令（10 分钟）

| 概念 | 在本项目中的作用 | 需要亲口解释的区别 |
|---|---|---|
| JAR | 编译后的 Java 程序和依赖 | JAR 不是运行中的进程 |
| 镜像 | JRE、JAR、启动方式的发布包 | 镜像不可变；容器是它的运行实例 |
| Compose | 描述多个容器如何连接和启动 | 不会自动证明业务正确 |
| named volume | 保存 MySQL 数据目录 | 容器换版本时，数据卷仍要保留 |

Java 编译产物通常可跨操作系统运行；容器中的 JRE 和系统库仍区分 `linux/amd64`、`linux/arm64`。本机能编译 JAR，不代表本机生成的镜像一定适合服务器。今天不使用本地 native-image 或含 Windows 原生库的依赖。

## 3. 明确命令在哪执行（20 分钟）

以后看到 **Windows PowerShell** 就在开发电脑执行；看到 **服务器 Linux Bash** 就先 SSH 登录。不要把 PowerShell 的反引号续行复制进 Bash，也不要把 Bash 的 `\` 续行复制进 PowerShell。

Windows PowerShell，任意目录：

```powershell
$ServerAddress = Read-Host '输入服务器 IP 或 SSH 主机名'
$SshLogin = Read-Host '输入已有 SSH 用户名，例如 deploy'
ssh "$SshLogin@$ServerAddress"
```

`Read-Host` 输入的地址不是密码。私钥口令交给 SSH 的交互提示，不写进脚本。首次连接前，在云控制台核对主机指纹。

服务器 Linux Bash：

```bash
uname -m
cat /etc/os-release
sudo docker version
sudo docker compose version
```

预期：服务器为 `x86_64`，Docker Engine 可用，Compose 为 V2 插件。若是 `aarch64`，后文平台改为 `linux/arm64`，并确认三个基础镜像支持它。Docker 未安装时，按 [Docker 官方 Ubuntu 安装步骤](https://docs.docker.com/engine/install/ubuntu/) 安装 Engine 和 Compose plugin；不要运行不明来源的一键脚本。安装超过 20 分钟时，将错误输出单独记录，本日其余文档和本地打包仍可继续。

生产目录约定如下，均为**将要创建的位置**：

| 位置 | 放什么 |
|---|---|
| `/opt/quephoto/compose.yaml` | 三个常驻服务和一个显式导入服务 |
| `/opt/quephoto/.env` | 镜像引用，不含密码 |
| `/opt/quephoto/app.env` | 普通生产配置，不含密码、令牌、AccessKey |
| `/opt/quephoto/secrets/` | 私有配置文件 |
| `/opt/quephoto/deploy/nginx.conf` | Nginx 配置 |
| `/opt/quephoto/imports/` | 本人审核的导入清单 |
| `/opt/quephoto/releases/` | 镜像、摘要、发布记录 |
| `/opt/quephoto/backups/` | 数据库备份及校验文件 |
| `/opt/quephoto/bin/` | 备份脚本 |

服务器 Linux Bash：

```bash
sudo install -d -m 755 /opt/quephoto
sudo install -d -m 755 /opt/quephoto/deploy /opt/quephoto/bin
sudo install -d -m 700 /opt/quephoto/secrets /opt/quephoto/imports
sudo install -d -m 700 /opt/quephoto/releases /opt/quephoto/backups
```

Docker 管理权限约等于宿主 root 权限。这里使用 `sudo docker`，无需为了方便把不可信用户加入 docker 组。云安全组暂时只放行 SSH，能限制为自己的出口 IP 时就限制；先用第二个 SSH 窗口确认密钥登录有效，再考虑关闭密码登录，避免把自己锁在门外。

## 4. 准备生产配置与发布包（35 分钟）

### 4.1 生产 profile

编辑计划目标 `D:\Project\QuePhoto_java\src\main\resources\application-prod.yml`。把下面结构合并到现有配置，保留前几天的 MyBatis、JSON、错误响应设置，不重复创建同名 YAML 顶层键：

```yaml
server:
  address: 0.0.0.0
  port: 8080
spring:
  config:
    import: "configtree:/run/secrets/"
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
  flyway:
    enabled: true
  sql:
    init:
      mode: never
admin:
  username: ${ADMIN_USERNAME}
jwt:
  issuer: ${JWT_ISSUER}
  audience: ${JWT_AUDIENCE}
oss:
  region: ${OSS_REGION}
  endpoint: ${OSS_ENDPOINT}
  bucket: ${OSS_BUCKET}
  base-url: ${OSS_BASE_URL}
  allowed-prefix: ${OSS_ALLOWED_PREFIX}
logging:
  level:
    root: INFO
```

`server.address=0.0.0.0` 仅让 Java 在容器内能被 Nginx 访问；公网隔离由后面的端口映射控制。沿用本地的 `127.0.0.1` 会导致容器之间连接失败。

秘密值由 `/run/secrets/` 的文件名映射为 Spring property。缺少秘密目录时生产启动应失败，因此不加 `optional:`。例如文件 `admin.password-hash` 的内容成为 `admin.password-hash`；`spring.datasource.password` 成为数据库密码。普通配置类只绑定 property，不自行打开文件。[Spring Boot 4.0 外部配置文档](https://docs.spring.io/spring-boot/4.0/reference/features/external-config.html)

本地 `application-local.yml` 可以继续使用 `${DB_PASSWORD}` 等占位符。生产不要再要求缺失的 `ADMIN_PASSWORD_HASH`、`JWT_SECRET`、`OSS_ACCESS_KEY_SECRET` 环境变量；确保秘密 property 最终由导入的配置树覆盖。不要给生产秘密提供默认值。

### 4.2 Dockerfile 和构建上下文

计划目标 `D:\Project\QuePhoto_java\Dockerfile`：

```dockerfile
ARG JAVA_BASE
FROM ${JAVA_BASE}
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app
COPY --chown=10001:10001 target/quephoto.jar /app/quephoto.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quephoto.jar"]
```

计划目标 `D:\Project\QuePhoto_java\.dockerignore`：

```text
**
!target/
!target/quephoto.jar
!Dockerfile
```

exec 形式的 `ENTRYPOINT` 可正确接收停止信号，也允许导入模式追加 Spring 参数。不能写成忽略参数的 shell 命令。`USER` 降低应用进程权限，MySQL、Nginx 使用各自官方镜像管理进程。

Windows PowerShell，项目根。新开终端后，先按 Day 05 第 6 节重新交互设置 `TEST_DB_USERNAME/TEST_DB_PASSWORD`，并确认本机 MySQL 在运行；这些环境变量不会从昨天的窗口自动继承。构建期间测试只能连接 `quephoto_test`，不能改成服务器数据库来绕过本地失败。

```powershell
Set-Location 'D:\Project\QuePhoto_java'
.\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { throw '构建或测试失败，停止发布' }
Get-Item '.\target\quephoto.jar'
Get-FileHash '.\target\quephoto.jar' -Algorithm SHA256
docker pull --platform linux/amd64 eclipse-temurin:21-jre-jammy
$JavaBase = docker image inspect eclipse-temurin:21-jre-jammy --format '{{index .RepoDigests 0}}'
if (-not $JavaBase.Contains('@sha256:')) { throw '未取得基础镜像摘要' }
$ReleaseTag = 'quephoto:day06-baseline'
docker build --platform linux/amd64 --build-arg "JAVA_BASE=$JavaBase" -t $ReleaseTag .
if ($LASTEXITCODE -ne 0) { throw '镜像构建失败' }
docker image inspect $ReleaseTag --format '{{.Id}} {{.Architecture}}'
New-Item -ItemType Directory -Force '.\target\release' | Out-Null
docker save --output '.\target\release\quephoto-day06.tar' $ReleaseTag
Get-FileHash '.\target\release\quephoto-day06.tar' -Algorithm SHA256
```

`21-jre-jammy` 是选择入口；实际 `FROM` 使用本次解析出的 digest，并把它、应用镜像 ID、JAR SHA256 写进发布记录。后续发布不复用 baseline 标签。若本机 Docker 不可用，可以将 **JAR、Dockerfile、.dockerignore** 传到服务器独立构建目录构建，按服务器架构拉取并记录 digest；不上传源码、`.git`、本地配置和真实凭据。这条备用路径仍须保留前面本地测试结果。

上传时只传镜像包和部署模板。Windows PowerShell：

```powershell
scp '.\target\release\quephoto-day06.tar' "${SshLogin}@${ServerAddress}:quephoto-day06.tar"
```

服务器 Linux Bash，在 SSH 用户家目录中：

```bash
sha256sum quephoto-day06.tar
sudo install -m 600 quephoto-day06.tar /opt/quephoto/releases/quephoto-day06.tar
sudo docker load --input /opt/quephoto/releases/quephoto-day06.tar
```

对比两端 SHA256 相同。不要用 PowerShell 的 `docker save ... > image.tar`：不同 PowerShell 版本对原生程序重定向处理不同，二进制文件可能受损；用 `--output` 和 `scp`。

## 5. 在服务器存好配置（20 分钟）

下面的编辑和脚本在**服务器 Linux Bash**中进行。使用 `sudoedit /opt/quephoto/app.env` 创建普通配置，替换所有示例值：

```dotenv
DB_URL=jdbc:mysql://db:3306/quephoto?connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true&preserveInstants=true&characterEncoding=UTF-8
DB_USERNAME=quephoto
ADMIN_USERNAME=替换为你的管理员名
JWT_ISSUER=quephoto
JWT_AUDIENCE=quephoto-admin
OSS_REGION=替换为实际region
OSS_ENDPOINT=https://替换为实际OSS-endpoint
OSS_BUCKET=替换为实际bucket
OSS_BASE_URL=https://替换为现有图片访问域名
OSS_ALLOWED_PREFIX=photos/
```

`db` 是 Compose 服务名；这里不能填 `localhost`。endpoint、region 和 bucket 必须来自同一个 OSS 实例，图片访问域名与 API 域名是两件事。沿用现有 OSS 访问方式，不修改已上线小程序使用的 Bucket ACL。

接着首次生成数据库密码和 JWT secret。**仅在秘密文件不存在时运行**；不能把“重跑初始化”当密码轮换。数据库已经有数据时，更换文件不会自动修改 MySQL 账号密码。

```bash
sudo bash <<'BASH'
set -euo pipefail
umask 077
cd /opt/quephoto/secrets
for f in db-root-password spring.datasource.password jwt.secret mysql-root-client.cnf; do
  if test -e "$f"; then echo "已有配置，停止初始化：$f" >&2; exit 1; fi
done
openssl rand -hex 32 | tr -d '\n' > db-root-password
openssl rand -hex 32 | tr -d '\n' > spring.datasource.password
openssl rand -base64 32 | tr -d '\n' > jwt.secret
printf '[client]\nuser=root\npassword=%s\nprotocol=socket\n' "$(cat db-root-password)" > mysql-root-client.cnf
BASH
```

JWT 签名端和验证端均按 Day 03 的约定 Base64 解码，获得至少 32 字节的 HS256 密钥。不要一端 Base64 解码、另一端直接拿字符串字节。

将 Day 03 生成的 BCrypt 哈希、OSS 核验凭据通过无回显输入写入私有文件，避免命令历史中出现明文：

```bash
sudo bash <<'BASH'
set -euo pipefail
umask 077
cd /opt/quephoto/secrets
for f in admin.password-hash oss.access-key-id oss.access-key-secret; do
  if test -e "$f"; then echo "已有配置，停止写入：$f" >&2; exit 1; fi
  read -r -s -p "输入 $f：" secret_value </dev/tty
  printf '\n' >/dev/tty
  test -n "$secret_value"
  printf '%s' "$secret_value" > "$f"
  unset secret_value
done
chown root:10001 spring.datasource.password jwt.secret admin.password-hash oss.access-key-id oss.access-key-secret
chmod 440 spring.datasource.password jwt.secret admin.password-hash oss.access-key-id oss.access-key-secret
chmod 600 db-root-password mysql-root-client.cnf
BASH
```

宿主秘密目录保持 `root:root 700`；应用需要读取的文件组为容器 GID `10001`，权限 `440`。Compose 的本地 file secrets 使用挂载，不能假定 YAML 中写 `uid/gid/mode` 就替你修改宿主文件权限。[Compose 服务与 secrets 说明](https://docs.docker.com/reference/compose-file/services/)

BCrypt 哈希包含 `$`。这里把哈希作为文件内容，不经过 Compose 插值，**不要把 `$` 替换成 `$$` 写进哈希文件**。`$$` 是 Compose YAML 字符串中转义美元符号的规则，不是哈希自身的格式。不要把秘密写入普通 `env_file`，不要运行或分享会展开配置的 `docker compose config`、`docker inspect` 完整输出；下面只用 `config --quiet` 验证语法。

## 6. 三个常驻服务与显式导入服务（25 分钟）

先为 MySQL、Nginx 解析镜像摘要。服务器 Linux Bash：

```bash
sudo docker pull mysql:8.4
sudo docker pull nginx:1.28-alpine
sudo bash <<'BASH'
set -euo pipefail
umask 077
db_image=$(docker image inspect mysql:8.4 --format '{{index .RepoDigests 0}}')
nginx_image=$(docker image inspect nginx:1.28-alpine --format '{{index .RepoDigests 0}}')
case "$db_image" in *@sha256:*) ;; *) exit 1;; esac
case "$nginx_image" in *@sha256:*) ;; *) exit 1;; esac
printf 'APP_IMAGE=quephoto:day06-baseline\nMYSQL_IMAGE=%s\nNGINX_IMAGE=%s\n' "$db_image" "$nginx_image" > /opt/quephoto/.env
BASH
```

这一步只在首次部署写 `.env`；后续发版只修改 `APP_IMAGE`，不要覆盖已经记录的数据库/Nginx摘要。版本标签只是解析入口，Compose 使用固定 digest。若指定标签不存在，去官方镜像页选择该系列已发布版本，再记录 digest；不能静默换到 `latest`。

通过 `sudoedit /opt/quephoto/compose.yaml` 保存以下完整模板：

```yaml
name: quephoto
x-logging: &logs
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
x-app: &app
  image: ${APP_IMAGE:?必须设置 APP_IMAGE}
  env_file: ./app.env
  environment:
    SPRING_PROFILES_ACTIVE: prod
    JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=60 -Duser.timezone=UTC"
  secrets:
    - source: db_password
      target: spring.datasource.password
    - source: admin_hash
      target: admin.password-hash
    - source: jwt_secret
      target: jwt.secret
    - source: oss_id
      target: oss.access-key-id
    - source: oss_key
      target: oss.access-key-secret
  networks: [backend]
  read_only: true
  tmpfs: [/tmp]
  security_opt: ["no-new-privileges:true"]
  cap_drop: [ALL]
  mem_limit: 768m
  logging: *logs
services:
  db:
    image: ${MYSQL_IMAGE:?必须设置 MYSQL_IMAGE}
    environment:
      MYSQL_DATABASE: quephoto
      MYSQL_USER: quephoto
      MYSQL_PASSWORD_FILE: /run/secrets/db_password
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/db_root_password
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci", "--default-time-zone=+00:00"]
    secrets:
      - db_password
      - db_root_password
      - mysql_root_client
    volumes: ["mysql_data:/var/lib/mysql"]
    networks: [backend]
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mysql", "--defaults-extra-file=/run/secrets/mysql_root_client", "--execute=SELECT 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s
    logging: *logs
  app:
    <<: *app
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'"]
      interval: 15s
      timeout: 5s
      retries: 8
      start_period: 60s
  nginx:
    image: ${NGINX_IMAGE:?必须设置 NGINX_IMAGE}
    ports: ["127.0.0.1:18080:80"]
    volumes: ["./deploy/nginx.conf:/etc/nginx/nginx.conf:ro"]
    networks: [backend]
    depends_on:
      app:
        condition: service_healthy
    restart: unless-stopped
    logging: *logs
  importer:
    <<: *app
    profiles: [tools]
    restart: "no"
    volumes: ["./imports:/imports:ro"]
    command: ["--spring.profiles.active=prod,import", "--spring.main.web-application-type=none", "--import.file=/imports/import-images.json"]
networks:
  backend:
    name: quephoto_backend
    driver: bridge
volumes:
  mysql_data:
    name: quephoto_mysql_data
secrets:
  db_password:
    file: ./secrets/spring.datasource.password
  db_root_password:
    file: ./secrets/db-root-password
  mysql_root_client:
    file: ./secrets/mysql-root-client.cnf
  admin_hash:
    file: ./secrets/admin.password-hash
  jwt_secret:
    file: ./secrets/jwt.secret
  oss_id:
    file: ./secrets/oss.access-key-id
  oss_key:
    file: ./secrets/oss.access-key-secret
```

Java healthcheck 只证明 HTTP 端口已监听，不证明数据库查询和登录正常；实际业务验证在下一节。`depends_on` 的 healthy 条件能避免数据库尚未就绪就启动应用，但容器运行后业务故障仍需检查日志和 API。[Compose 启动顺序文档](https://docs.docker.com/compose/how-tos/startup-order/)

应用连接账号仅拥有 `quephoto` 库权限；首周让它执行 Flyway DDL，避免额外迁移账号的操作复杂度，后续再拆分迁移账号与运行账号。不要用 root 作为 `DB_USERNAME`。

使用 `sudoedit /opt/quephoto/deploy/nginx.conf` 保存：

```nginx
events {}
http {
    access_log /dev/stdout;
    error_log /dev/stderr warn;
    limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
    limit_req_status 429;
    server {
        listen 80;
        server_name _;
        client_max_body_size 1m;
        location = /api/auth/login {
            limit_req zone=login burst=5 nodelay;
            proxy_pass http://app:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
        location /api/ {
            proxy_pass http://app:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 30s;
        }
        location / { return 404; }
    }
}
```

Nginx 不记录 Authorization 或请求体；客户端也不要将令牌放查询字符串。SSH 隧道下 Nginx 看到的来源常相同，登录限流按单人管理理解。不要连续快速执行错误密码测试后，误判正确密码也失效。

## 7. 启动、迁移、建立安全访问（20 分钟）

服务器 Linux Bash：

```bash
cd /opt/quephoto
sudo docker compose config --quiet
sudo docker compose up -d db
sudo docker compose ps
sudo docker compose logs --tail=80 db
```

等 `db` 变为 healthy。第一次创建库可能需要一两分钟；超过 3 分钟仍不健康就检查磁盘、文件权限、日志，不反复执行初始化。MySQL 的密码文件只在空数据目录初始化账号时生效，保留卷重启不会重建账号。[MySQL 官方镜像说明](https://hub.docker.com/_/mysql/)

继续：

```bash
sudo docker compose up -d app nginx
sudo docker compose ps
sudo docker compose logs --tail=100 app
sudo docker compose exec -T nginx nginx -t
curl --fail --silent --show-error 'http://127.0.0.1:18080/api/portfolios?page=1&pageSize=20'
sudo docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client quephoto --execute='SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
```

预期：Flyway 成功；无演示种子；空库返回合法分页对象，而不是 500。失败迁移不要通过删除库或 `repair` 硬跳过，先比较数据库状态和迁移脚本。不能修改已经应用的迁移文件。

Windows PowerShell **新开窗口**建立隧道，并让这个窗口保持运行：

```powershell
$ServerAddress = Read-Host '输入服务器 IP 或 SSH 主机名'
$SshLogin = Read-Host '输入 SSH 用户名'
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 18080:127.0.0.1:18080 "$SshLogin@$ServerAddress"
```

另外一个 PowerShell 窗口运行 Day 05 的请求集合，base URL 改为 `http://127.0.0.1:18080`。先测试公开 GET，再测试登录和创建草稿。真实密码通过 Day 03/05 的交互输入或私有请求环境读取，不粘贴到版本化 `.http` 文件。

确认隔离：服务器 `sudo ss -lntp` 应看到 SSH 和 `127.0.0.1:18080`，不应看到公网 `3306` 或 `8080`。Windows 用 `Test-NetConnection $ServerAddress -Port 18080`、`-Port 3306`、`-Port 8080` 验证直连失败；必要时从另一网络复查。HTTPS 尚未就绪时，这叫“服务器上可安全运营的后端”，不叫“公网 API 已开放”。

## 8. 在生产使用同一个导入器（10 分钟）

通过管理 API 创建真实草稿，使用**服务器返回的新 ID**准备清单，不能把本地作品 ID 直接搬过来。计划清单格式沿用 Day 04，上传到 SSH 用户目录，再由本人检查目标作品、真实 Key、封面、类型。

Windows PowerShell，在已设置 `$SshLogin/$ServerAddress` 的操作窗口中上传审核后的清单：

```powershell
scp 'D:\Project\QuePhoto_java\.local\import-images.json' "${SshLogin}@${ServerAddress}:import-images.json"
```

服务器 Linux Bash：

```bash
cd ~
sudo install -m 440 -o root -g 10001 import-images.json /opt/quephoto/imports/import-images.json
sudo chown root:10001 /opt/quephoto/imports
sudo chmod 750 /opt/quephoto/imports
cd /opt/quephoto
sudo docker compose run --rm --no-deps importer
```

importer 自动获得与 app 相同的网络、普通配置和受保护 secrets；`ENTRYPOINT` 会接收 `prod,import` 与非 Web 参数。预期命令执行完退出 0，输出作品 ID、图片 ID、实际新增数量；不能留下另一个 Web 端口或后台常驻进程。

清单文件的 `440` 允许容器组 `10001` 读取；目录的 `750` 还给这个组路径穿越权限。两者都需要，不能只改文件权限、保留挂载目录为 `root:root 700`，否则非 root 导入进程仍会报权限不足。宿主 `secrets` 目录继续保持 `700`，它通过逐个文件挂载提供给容器，与整目录挂载的 imports 不同。

重复同清单应新增 0 行；异常时非 0 退出，数据库不留下部分导入。之后通过 API 设置标签、确认封面、发布、公开查询。正常 `docker compose up -d` 不会启用 `tools` profile，也不会自动运行导入器。

## 9. 做一份能恢复的备份（15 分钟）

数据库备份只包含数据和表结构，不包含 OSS 图片。真实原图仍由本人另行保留，本周不删除、覆盖旧 OSS 对象。备份也不等于复制数据卷目录；运行中的 MySQL 不应直接打包 `/var/lib/mysql` 来冒充一致备份。

使用 `sudoedit /opt/quephoto/bin/backup.sh` 保存：

```bash
#!/usr/bin/env bash
set -euo pipefail
umask 077
cd /opt/quephoto
stamp=$(date -u +%Y%m%dT%H%M%SZ)
dest="/opt/quephoto/backups/quephoto-${stamp}.sql.gz"
test ! -e "$dest"
tmp=$(mktemp /opt/quephoto/backups/dump.XXXXXX.sql)
trap 'rm -f -- "$tmp"' EXIT
/usr/bin/docker compose exec -T db mysqldump \
  --defaults-extra-file=/run/secrets/mysql_root_client \
  --single-transaction --no-tablespaces --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 quephoto > "$tmp"
test -s "$tmp"
gzip -c "$tmp" > "${dest}.partial"
gzip -t "${dest}.partial"
mv -- "${dest}.partial" "$dest"
sha256sum "$dest" > "${dest}.sha256"
printf '%s backup_ok file=%s bytes=%s\n' "$stamp" "$dest" "$(stat -c %s "$dest")"
```

没有 `--databases`，导出的 SQL 不会指示恢复到生产库；后面可明确选择独立库。`--single-transaction` 适用于本项目 InnoDB，在备份期间不要执行 DDL；文件非空只是最低检查，仍必须恢复验证。[MySQL 8.4 mysqldump 文档](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)

服务器 Linux Bash：

```bash
sudo chmod 750 /opt/quephoto/bin/backup.sh
sudo /opt/quephoto/bin/backup.sh
```

记录输出的精确备份文件名。整个导出、压缩在 Linux 完成，不经过 Windows PowerShell 的文本/二进制管道。root 客户端选项文件提供密码，命令参数、日志和历史不出现密码。

安排每日备份：`sudoedit /etc/cron.d/quephoto-backup` 写入以下两行，末尾保留换行。时间以服务器时区为准，先用 `date` 核对；建议维持 UTC 并在运维记录中说明。

```cron
SHELL=/bin/bash
15 19 * * * root /opt/quephoto/bin/backup.sh >> /opt/quephoto/backups/backup.log 2>&1
```

确认 cron 服务存在且运行；下一次调度后核对日志和新文件。每天至少留最近 7 份成功备份，本周先不自动删除，Day 07 记录磁盘巡检和保留策略。失败时必须处理日志，不能把“配置了 cron”当“备份成功”。

## 10. 恢复到独立库，验证真实读取（35 分钟）

本节不会覆盖生产库。使用固定演练库名 `quephoto_restore`，**如果已经存在，就停止并选择新的演练库名，所有后续命令保持一致**；不覆盖已有演练记录。

服务器 Linux Bash，以具备 sudo 的本人账号运行：

```bash
cd /opt/quephoto
sudo docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client --execute="CREATE DATABASE quephoto_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON quephoto_restore.* TO 'quephoto'@'%';"
```

用刚才记录的路径替换输入；备份路径不是秘密，可以在终端输入。为了能读取 root 私有备份，这一小段在 `sudo bash` 内完成：

```bash
sudo bash <<'BASH'
set -euo pipefail
cd /opt/quephoto
read -r -p '输入刚才完整的 .sql.gz 备份绝对路径：' backup_file </dev/tty
case "$backup_file" in /opt/quephoto/backups/quephoto-*.sql.gz) ;; *) exit 1;; esac
test -f "$backup_file"
gzip -t "$backup_file"
gzip -dc "$backup_file" | docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client quephoto_restore
BASH
```

不要添加 `--force` 忽略 SQL 错误。恢复出错时保存第一条错误；生产库仍继续运行，不拿生产库重试。

核对恢复前记录的生产数量与恢复库：

```bash
sudo docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client --execute="SELECT 'prod' source,(SELECT COUNT(*) FROM quephoto.portfolio) portfolios,(SELECT COUNT(*) FROM quephoto.portfolio_image) images,(SELECT COUNT(*) FROM quephoto.portfolio_tag) links UNION ALL SELECT 'restore',(SELECT COUNT(*) FROM quephoto_restore.portfolio),(SELECT COUNT(*) FROM quephoto_restore.portfolio_image),(SELECT COUNT(*) FROM quephoto_restore.portfolio_tag);"
sudo docker compose exec -T db mysql --defaults-extra-file=/run/secrets/mysql_root_client quephoto_restore --execute="SELECT COUNT(*) invalid_cover FROM portfolio p JOIN portfolio_image i ON p.cover_image_id=i.id WHERE i.portfolio_id<>p.id OR i.image_type<>'work'; SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;"
```

若备份之后继续录入了作品，生产当前数量可能更多，要与备份时快照比较。`invalid_cover` 应为 0；进一步核对标签组、标签数量与真实作品标题。

只在演练库启动一个临时应用，禁用迁移避免演练改变 Schema，不发布宿主端口：

```bash
sudo docker compose run -d --no-deps --name quephoto-restore-check \
  -e 'DB_URL=jdbc:mysql://db:3306/quephoto_restore?connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true&preserveInstants=true' \
  app --spring.profiles.active=prod --spring.flyway.enabled=false
sudo docker logs --tail=60 quephoto-restore-check
sudo docker compose exec -T nginx wget -qO- 'http://quephoto-restore-check:8080/api/portfolios?page=1&pageSize=20'
```

等待日志中启动完成后，再请求其中一个已发布作品详情，并在本机打开返回的真实图片 URL。只测试公开读取，不在演练库导入、发布或修改内容。记录恢复开始/结束时间、库名、数据数量、API 状态和图片结果，然后停止临时应用：

```bash
sudo docker stop quephoto-restore-check
```

临时容器和演练库先保留给 Day 07 检查，后续清理要明确对象；本教程没有任何删除生产卷的命令。恢复容器仍占用名称，若需重做，应先审阅其状态并使用新的明确名称。

将备份另存到服务器外：先在服务器把选中的备份复制到本人 SSH 用户目录并设为 `600`，再通过 `scp` 下载到开发机的受控目录，例如 `D:\QuePhotoPrivateBackups`。这不是 Git 项目目录，不要把数据库备份纳入仓库。下载后用 `Get-FileHash -Algorithm SHA256` 对比服务器校验值，再清理传输用副本。异地副本应存于本人有权限且磁盘受保护的位置。

## 11. 重启证明持久化（5 分钟）

记录公开作品数量和一个真实作品 ID，然后在低影响时间执行：

```bash
cd /opt/quephoto
sudo docker compose restart db app
sudo docker compose ps
sudo docker compose logs --tail=50 app
```

等待数据库和应用健康，再经隧道请求相同作品，数量、标签、图片不应变化。数据库刚重启时短暂连接失败可以出现；若恢复后仍失败，就记录连接池/启动行为并修复，不宣称“自动恢复”。容器 `restart` 不是删除 named volume。

## 12. 排障顺序与超时处理

| 症状 | 先查什么 | 为什么 |
|---|---|---|
| Nginx 502 | app 日志、8080 监听、prod address | Java 在容器内只监听回环时，Nginx 连不到 |
| app 重建后 502 | `docker compose restart nginx` 后复测 | 此最小配置启动时解析上游；app 换 IP 后需要重新解析 |
| Access denied | 密码文件权限、首次建库时的账号、是否误改秘密 | 重启不会根据新文件自动轮换数据库密码 |
| 秘密 property 缺失 | configtree 路径、文件名、文件读取权限 | 配置名与环境变量名不是同一套写法 |
| Permission denied | `root:10001` 与 `440`、导入目录 `750` | 应用是非 root，挂载存在不等于有权限 |
| 容器退出 137 | 宿主内存、`docker stats --no-stream` | 可能被 OOM 杀死；不能靠一直重启掩盖 |
| 导入卡住或失败 | OSS region/endpoint/HEAD 权限、出站网络 | 此时无需给 MySQL 开公网端口 |
| 隧道失败 | SSH 会话、端口占用、ExitOnForwardFailure 输出 | 本地 18080 已占用时可以临时改本地为 18081 |
| Flyway checksum 错误 | 迁移是否被编辑、当前版本记录 | 不盲目 repair，不删卷重来 |

单个问题超过 15 分钟：写下“执行位置、命令、预期、实际、第一条根因”，只贴去密钥后的必要日志。超过 30 分钟：停止该步骤，保留已验证状态，并把下一项验收标为未通过。不要临时加入 Kubernetes、Redis、CI 平台扩大范围。

## 13. 域名和 HTTPS 是就绪后的分支

今天主线已经能让本人安全维护服务器数据。只有域名所需手续、DNS 指向、有效证书和续期方案全部就绪后，才增加公网 HTTPS Nginx 配置和安全组 443，按 HTTPS 访问重跑登录、发布、图片 URL 与错误码验收。

准备工作是：确认所需手续 → DNS 验证 → 按所选证书工具官方说明申请证书 → 证书只读挂载给 Nginx → `nginx -t` → 启动 HTTPS → 外网验证证书链/域名/到期时间 → 验证续期与重载。公网 80 是否临时需要取决于证书验证方式，不默认长期开放。HTTP 登录请求不得经过公网明文链路。没完成就继续 SSH 隧道，不影响本周后端运营目标；现有小程序本周仍不切换。

## 14. 学习练习、提示与交付记录

先遮住提示，每题用自己的话答两三句：

1. 为什么把生产 `DB_URL` 写成 `localhost:3306` 通常会失败？提示：每个容器有自己的回环地址。答案要点：app 的 localhost 是 app 容器，MySQL 在 db 服务上，应使用内部 DNS 名 `db`。
2. 为什么 `$2a$...` 哈希放入秘密文件后不能改成 `$$2a$$...`？提示：哪一层负责插值？答案要点：文件原样读取，双美元会改变哈希；Compose YAML 中的转义不适用于秘密文件内容。
3. `ENTRYPOINT` 的 exec 数组为什么适合导入器？提示：追加参数和信号。答案要点：Java 直接接到 Spring 参数和停止信号，非 Web 模式结束后应退出。
4. 为什么能下载 dump 还不能宣布恢复成功？提示：数据结构、外键、编码与实际查询。答案要点：要导入独立库，验证数量、关联、API 和图片引用。
5. 说清源码、JAR、JRE、镜像、容器、数据卷六者的关系。答不完整就画图，不背术语。

今日完成时，在计划目标 `D:\Project\QuePhoto_java\docs\operations.md` 记录：版本与 digest、服务器系统/架构、访问方式、五表及 Flyway 版本、镜像 ID、备份路径与 SHA256、恢复耗时和验证结果。只记录秘密的位置与管理方式，不记录内容。

- [ ] 隧道可访问，管理接口仍要求 JWT，数据库/应用端口不公开。
- [ ] 正常启动没有导入，显式 importer 在生产成功并正确退出。
- [ ] 至少一个真实作品完整闭环，Day 07 补齐至少三个作品。
- [ ] 实际备份、独立库恢复、恢复应用读取及异地副本均有证据。
- [ ] 重启后真实数据保持一致，日志具备轮转设置。
- [ ] 能独立解释上述练习；未通过项带着明确错误进入 Day 07。

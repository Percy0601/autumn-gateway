# autumn-gateway-deploy 部署说明

最终网关发行包。按 Maven profile 决定「装哪些组件 / 出哪个平台的二进制」，按 **Spring profile** 决定「运行时连哪个库」——两者互不相干，一个二进制可以跑多套环境。

---

## 1. 环境变量（必填，无默认值）

仓库里**不再包含任何可用的密钥、口令或内网地址**。缺变量时 Spring 启动即失败（快速失败，避免回落到公开开发密钥）。

| 变量 | 用途 | 哪些形态需要 |
|---|---|---|
| `AUTUMN_SHARED_JWT_SECRET` | HS256 共享密钥，≥32 字节 | **所有形态**，网关签发与 dashboard 验签必须是同一把 |
| `DB_URL` | 完整 JDBC URL | `db-mysql` / `db-postgresql` |
| `DB_USER` | 数据库账号 | 同上 |
| `DB_PASSWORD` | 数据库口令 | 同上 |
| `AUTHN_CLIENT_SECRET` | OAuth2 客户端密钥 | 仅 `feature/oauth2` 配方 |
| `SPRING_PROFILES_ACTIVE` | 运行时库形态，默认 `db-mysql` | 可选 |

参考仓库根目录 `.env.example`（只提交占位值）。本地加载方式：

```bash
# Linux / macOS / Git Bash
set -a; source .env; set +a
```
```powershell
# PowerShell
Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') } }
```

> **升级提示**：改造前 `application.properties` 里内置了可用的开发 JWT 密钥和数据库口令，直接 `java -jar` 就能连预设的 MySQL。现在必须显式注入上述变量——这正是「轮换泄露凭据」的代价。所有已写进仓库/git 历史的旧口令应视为已泄露，请在线上/102 环境一并轮换。

---

## 2. 运行时数据库 profile

同一个二进制，启动参数切换即可，无需重新构建：

| Spring profile | 说明 | 需要 DB_* 变量 |
|---|---|---|
| `db-mysql`（默认） | 等价于改造前的 MySQL 形态 | 是 |
| `db-postgresql` | PostgreSQL | 是 |
| `db-h2` | 本地内存库，**只用于启动/路由/限流冒烟**（库是空的，登录会失败） | 否 |

```bash
# 生产（MySQL）
java -jar target/autumn-gateway-deploy.jar

# 切 PostgreSQL
java -jar target/autumn-gateway-deploy.jar --spring.profiles.active=db-postgresql

# 本地冒烟（无需数据库，但仍需 AUTUMN_SHARED_JWT_SECRET）
java -jar target/autumn-gateway-deploy.jar --spring.profiles.active=db-h2
```

对应配置文件：`application-db-mysql.properties` / `application-db-postgresql.properties` / `application-db-h2.properties`。三个驱动都随包提供；OAuth2 配方（`feature/oauth2/application.properties`）是**替换**主配置使用的，不含数据库 profile，网关侧不需要数据源。

---

## 3. 构建

```bash
# JVM 包
./mvnw -pl autumn-gateway-deploy -am package -DskipTests

# GraalVM native（JAVA_HOME 指向 GraalVM）
./mvnw -Pnative,native-image -pl autumn-gateway-deploy package -DskipTests
```

- `native` profile 由 starter-parent 提供，`native-image` 由本仓库根 pom 提供，两个都要。
- **不要使用 `mvn native:compile`**：它会跳过 `process-aot`，产物一启动就 `ClassNotFoundException`。旧版 README 里那条命令已废弃。
- 平台后缀由 `-Pos-linux` / `-Pos-linux-arm` / `-Pos-windows` 决定；GraalVM 不交叉编译，在目标平台上构建。
- 组件编排：默认轻量 authz；`-Pcomponent-authn -Dno.authz` 为 OAuth2 SSO；`-Pcomponent-discovery` 加服务发现。

---

## 4. 限流（可选，默认关闭）

完整说明与可直接复制的配置见 `feature/limit/`。

**先澄清一个常见误解**：官方 `RequestRateLimiter` 的默认实现是 `RedisRateLimiter`（需要 Redis），**不是内存**。单机内存版是 Gateway 5.x 的 `Bucket4jRateLimiter`（Caffeine 后端），需要：

1. `bucket4j_jdk17-caffeine`（已随本模块引入，锁 `8.15.0`）；
2. 一个 `AsyncProxyManager<String>` bean；
3. 一个 `KeyResolver` bean。

本模块用 `Bucket4jRateLimitConfig` 提供 2、3，但加了一层 `@ConditionalOnProperty(prefix="autumn.limit", name="enabled", havingValue="true")` —— **默认不注册任何 bean，行为与改造前完全一致**。

启用：

```bash
# 方式 A：把 feature/limit/application.properties 的内容复制进 application.properties
# 方式 B：运行时导入
java -jar target/autumn-gateway-deploy.jar \
  --spring.config.import=classpath:/feature/limit/application.properties
```

核心配置（给 `routes[1]` 即 `/api/**` 追加第 2 个 filter）：

```properties
autumn.limit.enabled=true
spring.cloud.gateway.server.webflux.routes[1].filters[1].name=RequestRateLimiter
spring.cloud.gateway.server.webflux.routes[1].filters[1].args.key-resolver=#{@ipKeyResolver}
spring.cloud.gateway.server.webflux.routes[1].filters[1].args.bucket4j-rate-limiter.capacity=4
spring.cloud.gateway.server.webflux.routes[1].filters[1].args.bucket4j-rate-limiter.refill-tokens=2
spring.cloud.gateway.server.webflux.routes[1].filters[1].args.bucket4j-rate-limiter.refill-period=10s
spring.cloud.gateway.server.webflux.routes[1].filters[1].args.bucket4j-rate-limiter.requested-tokens=1
```

效果：同一 IP 前 4 次放行，之后 429，10 秒回灌 2 个 token。响应头带 `X-RateLimit-Remaining` 等。

**边界**：
- 内存桶是单机的，多实例各算各的；集群需换 Redis / 集中配额。
- IP 默认只取 TCP 远端地址。**不要**随便填 `autumn.limit.trusted-header=X-Forwarded-For`，除非边缘代理会剥离客户端自带的同名头，否则换个头就能绕过限流。
- `/gateway/endpoint/login`、`/endpoint/captcha` 是网关本地 Controller，不走路由过滤器链，本示例限不到它们；登录防暴破需要额外的 `WebFilter`（尚未实现）。
- GraalVM native 下 Caffeine 反射加载生成缓存类，已随模块提供 `META-INF/native-image/.../reflect-config.json`。

---

## 5. 发行配方目录

| 文件 | 用途 |
|---|---|
| `application.properties` | 默认主配置（轻量 authz + 路由），不含任何凭据 |
| `feature/authz/application.properties` | 轻量 authz 完整配方，替换主配置使用 |
| `feature/oauth2/application.properties` | OAuth2 SSO 配方，替换主配置使用 |
| `feature/limit/application.properties` | 限流片段，默认关闭 |
| `application-db-*.properties` | 三个运行时数据库 profile |

# OAuth2 统一认证登录使用手册

> 面向：**怎么用这套东西登录**、**每个 URL 是干嘛的**、**出问题先看哪**。
> 设计层面的论证在 `docs/网关认证授权演进规划.md`，本文只讲「跑起来 + 用起来」。
>
> 最后验证时间：2026-10-21 22:35（macOS 本地三服务全链路跑通；授权服务器 native 镜像在 102 虚拟机跑通）

---

## 一、先回答那个问题：谁是 OAuth2 客户端？

**`autumn-gateway`（网关）是 OAuth2 客户端。`autumn-gateway-dashboard` 不是。**

这句话的意思是：

| 角色 | 组件 | 端口 | 它负责什么 | 它不负责什么 |
|---|---|---|---|---|
| **OAuth2 客户端（Client）** | `autumn-gateway` | 8080 | 发起登录跳转、拿授权码、换令牌、**保管令牌**（放在自己的服务端会话里）、把令牌中继给下游 | 不签发令牌、不知道密码 |
| **授权服务器（Authorization Server / IdP）** | `autumn-gateway-authorization` | 9000 | 显示登录页、校验账号密码/验证码、**签发 JWT**（RS256 + JWKS） | 不认识任何业务接口 |
| **资源服务器（Resource Server）** | `autumn-gateway-dashboard` | 8081 | 只**验签**网关转发来的 `Authorization: Bearer`，然后干活 | 不签发令牌、不跳转登录 |

### 为什么不是 dashboard 当客户端

如果 dashboard 自己当 OAuth2 客户端，那么每接一个业务系统都要重复一遍「跳授权服务器 → 换令牌 → 存会话 → 刷新令牌」，
而且每个系统各自持有令牌，SSO 就散了。网关当客户端之后：

- **接入成本**：新业务系统只要会「验签」就行，不用懂 OAuth2；
- **令牌不出网关**：浏览器只拿到一枚 HttpOnly 的 `SESSION` Cookie，**access_token 从头到尾没落过浏览器**；
- **签发点唯一**：只有 9000 那台能签发，SSO 才成立。

代价只有一个：令牌存在网关的**服务端会话**里，所以网关多实例部署时要用 Spring Session 做会话共享（或粘性会话）。

### 一次登录的完整时序

```
浏览器                     网关 :8080                 授权服务器 :9000              dashboard :8081
  │                           │                            │                            │
  │─ GET / ─────────────────►│                            │                            │
  │                           │ 未认证                     │                            │
  │◄── 302 /oauth2/authorization/web-client ───────────────│                            │
  │─ GET /oauth2/authorization/web-client ─►│              │                            │
  │                           │─ 302 /oauth2/authorize?code_challenge=… ─►│             │
  │─ GET /oauth2/authorize ─────────────────────────────►│                │             │
  │◄────────────── 登录页（含验证码）─────────────────────│                │             │
  │─ POST /login（用户名/密码）──────────────────────────►│                │             │
  │◄────────────── 302 回调 + code ──────────────────────│                │             │
  │─ GET /login/oauth2/code/web-client?code=… ─►│         │                │             │
  │                           │─ POST /oauth2/token（code + code_verifier）►│            │
  │                           │◄───────── access_token（RS256 JWT）────────│            │
  │                           │ 令牌存入服务端会话                          │             │
  │◄── 302 / + Set-Cookie: SESSION=… ─────────│                            │             │
  │─ GET /api/currentUser ───►│                            │                │            │
  │                           │─ TokenRelay 注入 Bearer ──────────────────────────────►│
  │                           │                            │                │  验签+返回 │
  │◄── 200 {"userid":"750147","authorityList":[…]} ──────────────────────────────────│
```

关键点：**`TokenRelay=` 这一行路由过滤器**是整个方案的枢纽，它把会话里的 access_token 塞进转发请求的
`Authorization` 头。dashboard 看到的永远是一个已经验过签的 Bearer 令牌，它不需要知道用户是怎么登录的。

---

## 二、关键 URL 一览

### 前台（你日常要用的）

| URL | 说明 |
|---|---|
| **http://localhost:8080/** | **业务入口**。浏览器只认这个。未登录会自动跳授权服务器 |
| http://localhost:8080/logout | 退出登录，跳回授权服务器登录页 |

### 后台（授权服务器 :9000，OAuth2 / OIDC 端点）

| URL | 说明 |
|---|---|
| http://localhost:9000/login | 统一登录页（含验证码） |
| http://localhost:9000/oauth2/authorize | 授权端点（OAuth2 标准） |
| http://localhost:9000/oauth2/token | 令牌端点（授权码 / 刷新令牌） |
| http://localhost:9000/oauth2/jwks | **JWKS 公钥集**，资源服务器验签用 |
| http://localhost:9000/.well-known/oauth-authorization-server | 授权服务器元数据（自动发现） |
| http://localhost:9000/userinfo | OIDC 用户信息 |
| http://localhost:9000/api/csrf | 登录页取 CSRF 令牌 |
| http://localhost:9000/api/captcha | 验证码 |
| http://localhost:9000/login/wechat · /login/wechat/callback | 微信扫码登录 |

### 后台（网关 :8080，OAuth2 客户端端点）

| URL | 说明 |
|---|---|
| http://localhost:8080/oauth2/authorization/web-client | **登录入口**（`registration-id=web-client`） |
| http://localhost:8080/login/oauth2/code/web-client | 授权码回调（**必须在授权服务器登记过**） |

### 后台（dashboard :8081，内部服务，浏览器不该直接访问）

| URL | 说明 |
|---|---|
| http://localhost:8081/api/currentUser | 当前登录用户（含权限清单），未登录回 401 + `loginUrl` |
| http://localhost:8081/api/login/outLogin | 返回登出地址（前端据此跳转） |
| http://localhost:8081/api/system/basic/** | 各业务接口 |

> dashboard 也在 8081 上托管前端产物（`/`、`/assets/**`），但**请一律从 8080 访问**，
> 直连 8081 会绕过认证。

### 数据库（102 虚拟机上的 MySQL）

- 地址 `192.168.0.102:3306/autumn-security`，账号 `admin/123456`（root 密码同为 `123456`，跑在 Docker 容器 `mysql` 里）
- OAuth2 相关表：`oauth2_registered_client`（客户端注册）、`oauth2_authorization`（授权/令牌）、`oauth2_authorization_consent`
- 已登记的客户端：`web-client` / `secret`，回调地址
  `http://localhost:8080/login/oauth2/code/web-client`、`http://127.0.0.1:8080/callback`

---

## 三、怎么用（三分钟上手）

### 1. 起服务

```bash
cd /Users/mac/work/project/autumn-gateway

# ① 授权服务器 :9000（注意：必须去掉沙箱的 SERVER__PORT，否则端口会被抢成 64884）
env -u SERVER__PORT ./mvnw -pl autumn-gateway-authorization spring-boot:run

# ② dashboard :8081（连 102 的真实 MySQL）
env -u SERVER__PORT ./mvnw -pl autumn-gateway-dashboard spring-boot:run

# ③ 网关 :8080
env -u SERVER__PORT ./mvnw -pl autumn-gateway-deploy spring-boot:run
# 或跑打包产物：
# env -u SERVER__PORT java -jar autumn-gateway-deploy/target/autumn-gateway-deploy-0.0.1-SNAPSHOT.jar
```

MySQL 没起时先到 102 上 `docker start mysql`。

### 2. 浏览器登录

1. 打开 **http://localhost:8080/**
2. 自动跳到 http://localhost:9000/login（统一认证中心）
3. 输入 **`admin` / `Autumn@123456`** + 验证码
4. 自动回跳 http://localhost:8080/ ，进入 dashboard

> 前端登录页（`autumn-gateway-dashboard-ui/src/views/login/index.vue`）已经**没有账号密码表单**了，
> 只有一个「使用统一认证登录」按钮 —— 点了就走上面的流程。
> 已经在登录态时访问 `/login` 会被直接放行进首页。

### 3. 退出

右上角「退出登录」→ 跳转到 `http://localhost:8080/logout` → 清掉网关会话 → 跳回 9000 的登录页。

---

## 四、改了哪些东西（改动清单）

| 模块 | 文件 | 改动 |
|---|---|---|
| **新建 starter** | `autumn-gateway-starters/authn-autumn-gateway-starter` | 网关统一认证自动配置：OAuth2 客户端注册（授权码 + **PKCE**）、会话存令牌、XHR 未认证回 401、登出入口 |
| 网关 | `autumn-gateway-deploy/pom.xml` | 引入 authn starter；`component-authn` / `os-linux` 等 profile |
| 网关 | `autumn-gateway-deploy/application.properties` | 通配路由 + `TokenRelay=`；`autumn.authn.*` 配置 |
| dashboard | `security/JwtTokenService.java`（**新建**） | 用 **Nimbus** 验签，取代原 JJWT 的 `JwtUtil` |
| dashboard | 删除 `JwtUtil.java`、`AuthController.java` | **dashboard 不再自签令牌** |
| dashboard | `security/JwtAuthenticationFilter.java` | 改为按 uuid 反查 `basic_user` 组装身份 |
| dashboard | `controller/LoginController.java` | 只保留 `/api/currentUser` 与 `/api/login/outLogin` |
| dashboard | `application.properties` | 删 `jwt.secret`，改 `autumn.security.jwt.jwk-set-uri=http://localhost:9000/oauth2/jwks` |
| 前端 | `utils/request.ts` | 加 `withCredentials: true`；**删除**「从 localStorage 取 token」的拦截器 |
| 前端 | `store/modules/user.ts` | 删除 token 状态，改为会话模式（`ensureAuthenticated()` / `redirectToLogin()`） |
| 前端 | `router/index.ts` | 守卫简化：非 `/login` 一律 `ensureAuthenticated()`，失败就跳登录 |
| 前端 | `views/login/index.vue` | 重写为「使用统一认证登录」 |
| 前端 | `vite.config.ts` | `base` 从 `./` 改回 `/`（相对 base 在深链 F5 时资源 404） |

---

## 五、排障清单（按症状查）

| 症状 | 原因 / 处理 |
|---|---|
| 访问 8080 一直 302 到 9000，登录后又回到登录页 | 账号密码错，或验证码没过。看 9000 的日志 |
| 网关报 `invalid_request: OAuth 2.0 Parameter: code_challenge` | PKCE 没开。检查 starter 里 `requireProofKey(true)`；授权服务器是 OAuth 2.1，强制要求 PKCE |
| 回调报 `invalid_redirect_uri` | 回调地址没登记。改 `oauth2_registered_client.redirect_uris` 或用 `demo-client.redirect-uri` 重启初始化 |
| `/api/**` 返回登录页 HTML（200 而不是 401） | 请求没带 `Accept: application/json`，或没带 `withCredentials`。SPA 的 axios 两个都要有 |
| 前端一直提示「登录已过期」 | 网关会话过期/丢了吗？看浏览器是否带着 `SESSION` Cookie；网关多实例时要做会话共享 |
| dashboard 报 `401 invalid_token` | 令牌过期或 JWKS 拉不到。确认 9000 起来了、`jwk-set-uri` 指向正确 |
| 端口不对（连 8080 被拒但进程在） | 沙箱里 `SERVER__PORT=64884` 会覆盖 `server.port`，启动必须 `env -u SERVER__PORT` |
| 后端起不来报 `JdbcAggregateOperations` 连环错 | 只是连不上 MySQL。先 `nc -z 192.168.0.102 3306`，到 102 上 `docker start mysql`，**不要改代码** |
| SPA 深链（`/system/user`）F5 后白屏 | `vite.config.ts` 的 `base` 必须是 `/`，不能是 `./` |

---

## 六、已知限制（还没做的）

1. **单点登出（SLO）未实现**：`GET /logout` 只清了网关侧会话，授权服务器的会话还在，
   所以再访问网关会「静默自动重新登录」（因为 IdP 侧还认）。要真正登出得授权服务器提供
   `end_session_endpoint` —— 属于 P3。
2. **网关多实例需要会话共享**：令牌在 WebSession 里，默认内存态。上多实例要接 Spring Session（JDBC/Redis）。
3. **验证码**：默认开启（`autumn.security.captcha.enabled=true`）。跑自动化脚本时用
   `--autumn.security.captcha.enabled=false` 关掉。
4. **网关细粒度授权还没做**：现在网关只做「认证」，鉴权还是 dashboard 自己的 `@PreAuthorize`。
   网关侧的权限码判定属 P2。

---

## 七、附录 A：GraalVM native 构建配方

```bash
# 102 虚拟机（ssh percy@192.168.0.102，密码 1）
export JAVA_HOME=/home/percy/opt/graalvm-jdk-25.0.3+9.1
export PATH=$JAVA_HOME/bin:/home/percy/opt/apache-maven-3.9.16/bin:$PATH
cd ~/work/project/autumn-gateway-native

# 授权服务器
mvn -B -Pnative,native-image -pl autumn-gateway-authorization package -DskipTests

# 网关（deploy）—— 组件与平台可组合
mvn -B -Pnative,native-image -pl autumn-gateway-deploy package -DskipTests              # 默认 authn + linux-amd64
mvn -B -Pnative,native-image -pl autumn-gateway-deploy -Pos-windows package -DskipTests # 出 .exe
mvn -B -Pnative,native-image -pl autumn-gateway-deploy -Pos-linux-arm package -DskipTests
mvn -B -Pnative,native-image -pl autumn-gateway-deploy -Dno.authn package -DskipTests   # 纯路由网关
```

- ⚠️ **不要用 `mvn native:compile`**：buildtools 1.1.x 的 CLI 目标会跳过 `process-aot`，
  产物启动即 `ClassNotFoundException: WebMvcWebApplicationTypeDeducer`（症状：64MB 小二进制一启动就挂）。
- ⚠️ **native 构建不要加 `-am`**：根 pom 的 `native-image` profile 会被 pom 打包的父模块继承，
  父模块会报 `Image classpath is empty`。依赖先 `install`，再单模块 `package`：
  ```bash
  mvn -B -pl autumn-gateway-deploy -am install -DskipTests          # 先装依赖（不带 native）
  mvn -B -Pnative,native-image -pl autumn-gateway-deploy package -DskipTests
  ```
- 产物：
  - 授权服务器 `autumn-gateway-authorization/target/autumn-gateway-authorization`，约 114MB，构建约 3m45s，启动 **0.12s**；
  - 网关 `autumn-gateway-deploy/target/autumn-gateway-deploy-linux-amd64`（名字带 `image.suffix`）。

### native 下最容易踩的坑（已修，别再踩）

授权服务器启动 **0.12 秒**、端口也起来了，但第一个请求就 500：

```
InvalidTypeIdException: Could not resolve type id 'java.util.Collections$UnmodifiableMap'
  ... PolymorphicTypeValidator (BasicPolymorphicTypeValidator) denied resolution
```

表面看是「JDK 集合内部类没注册」，**真因是 Spring Security 的 Jackson 模块在 native 下加载失败**
（`SecurityJacksonModules.loadAndGetInstance` 里 catch 掉异常只打 DEBUG 日志，静默失效），
于是默认多态类型的白名单根本没建起来。

验证命令（能看到满屏 `Cannot load module …` 就是它）：

```bash
./autumn-gateway-authorization --logging.level.org.springframework.security.jackson=DEBUG
```

修法有两种，我们**两个都做了**：
1. `AuthorizationRuntimeHints` 手写注册模块类 + JDK 集合类型（保留作根因说明）；
2. **用 tracing agent 生成完整元数据**（真正兜底）：
   `src/main/resources/META-INF/native-image/xyz.wewin.autumn/autumn-gateway-authorization/reachability-metadata.json`

### 附录 B：native 元数据怎么重新生成

升级 Spring Security 后若再报 `InvalidTypeIdException` / `Cannot construct instance of …`，按这个来：

```bash
# 1) 用 agent 起 JVM 版（关验证码，方便脚本跑）
rm -rf /tmp/nia-authz && mkdir -p /tmp/nia-authz
java -agentlib:native-image-agent=config-output-dir=/tmp/nia-authz \
     -jar autumn-gateway-authorization-0.0.1-SNAPSHOT.jar \
     --autumn.security.captcha.enabled=false

# 2) 跑一遍真实流程：登录 → /oauth2/authorize(PKCE) → /oauth2/token → /userinfo → refresh_token
bash /tmp/oauth_flow.sh

# 3) 必须是 TERM 结束（agent 在 JVM 退出时落盘）
kill -TERM <pid>

# 4) 覆盖回去
cp /tmp/nia-authz/reachability-metadata.json \
   autumn-gateway-authorization/src/main/resources/META-INF/native-image/\
xyz.wewin.autumn/autumn-gateway-authorization/reachability-metadata.json
```

---

## 八、附录 C：本次验证的原始记录

macOS 本地三服务（9000 / 8081 / 8080）端到端脚本 `/tmp/e2e_full.py` 输出：

```
1. 未认证访问 8080/            -> 302 → http://localhost:9000/login
2. IdP 登录                    -> 200
3. 授权码流程完成回到网关        -> 200 http://localhost:8080/
4. GET /api/currentUser        -> 200 {"userid":"750147","authorityList":[...41 项权限...]}
5. GET /logout                 -> 302 → http://localhost:9000/login
6. 登出后 GET /api/currentUser -> 401
浏览器侧 Cookie：只有 SESSION（HttpOnly），没有 token
```

102 虚拟机 **native 授权服务器**（`:9000`，启动 0.138s）完整 OAuth2 流程，零错误：

```
登录 200 → /oauth2/authorize(PKCE) 302 带 code → /oauth2/token 200
→ /userinfo 200 {"sub":"u-admin-0001","name":"管理员","authorities":["admin"]}
→ refresh_token 200（走 oauth2_authorization 的 JDBC 读写）→ /oauth2/jwks 200
```

102 虚拟机 **native 网关**（`:8080`，启动 **0.112s**）：

```
网关统一认证已启用：登录入口 /oauth2/authorization/web-client，授权服务器 http://localhost:9000，PKCE 已开启
Netty started on port 8080
GET /             (Accept: text/html)      -> 302 → /oauth2/authorization/web-client
GET /api/currentUser (Accept: application/json) -> 401
无 ERROR
```

> 网关 native 侧只验证到「正确把未认证请求交给 IdP」这一步 —— 102 上没有跑 dashboard 实例，
> 所以「TokenRelay → dashboard 验签 → 返回用户信息」这一段是在 macOS 用 JVM 版网关验的（见上）。

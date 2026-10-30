# 项目长期约定（autumn-gateway → autumn-cloud）

## 终极目标：autumn-cloud 全链路 GraalVM native 化
- 本地仓库 `autumn-gateway` 是 autumn-cloud 的前身；**autumn-cloud-dashboard 就是界面配置模块**（对应本地 `autumn-gateway-dashboard`）。
- 推进路径：① dashboard-ui `vite build` → dist 放进 `autumn-gateway-dashboard/src/main/resources/static`（已完成）② dashboard native 化（已完成）③ authorization / discovery 等逐步 native 化 ④ **统一在 `autumn-gateway-deploy` 构建发布**，可编排 starter 组合、按 OS（win/linux/linux-arm）出不同发行版。
- 讨论技术选型一律对照：启动速度、内存占用、单二进制分发。

## 环境与运维（102 Ubuntu 虚拟机）
- `ssh percy@192.168.0.102`，密码 `1`。macOS 无 sshpass/setsid → 用 `/usr/bin/expect` 脚本免交互。
- MySQL 跑在 102 的 docker 容器 `mysql`，`0.0.0.0:3306->3306`，库 `autumn-security`。
  - 应用账号 `admin/123456`（**无 ALTER/DROP**）；root 也是 `123456`，DDL 用 `docker exec -i mysql mysql -uroot -p123456 autumn-security < x.sql`。
  - Spring Boot 报 `JdbcAggregateOperations` 连环错 = 连不上库，先 `nc -z 192.168.0.102 3306`，别改代码。
- GraalVM `/home/percy/opt/graalvm-jdk-25.0.3+9.1`、Maven `/home/percy/opt/apache-maven-3.9.16`；VM 构建目录 `~/work/project/autumn-gateway-native/`。SSH 非登录 shell 要手工 export PATH。
- 本机无 mysql 客户端，用 `/Users/mac/.workbuddy/binaries/python/envs/default/bin/python`（pymysql）。
- 沙箱：`ps` 被禁（用 `lsof -i :端口`）；有 `HTTP_PROXY`（访问 localhost 需 `curl --noproxy '*'`）；**`SERVER__PORT=64884` 会覆盖 server.port**，起后端要 `env -u SERVER__PORT`。

## 构建约定
- **native 配方**：`mvn -Pnative,native-image -pl <模块> package -DskipTests`（JAVA_HOME 指 GraalVM）。
  `native` profile 由 starter-parent 提供，`native-image` 由本仓库根 pom 提供。**切勿用 `mvn native:compile`**（跳过 process-aot，产物一启动就 ClassNotFoundException）。
  dashboard 产物 139MB、构建 ~4.5min、启动 ~0.3s、RSS ~155MB；authorization 产物 ~114MB、构建 ~3m45s、启动 0.12s。
- **native 反射元数据：优先用 tracing agent 生成，别手写枚举**。
  `java -agentlib:native-image-agent=config-output-dir=... -jar 应用.jar` 跑一遍真实流程 → `kill -TERM`（必须 TERM 才落盘）→ 得到 `reachability-metadata.json`。
  **放进 `src/main/resources/META-INF/native-image/<group>/<artifact>/` 时必须改名成 `reflect-config.json`**
  （Spring AOT 会写同名的 `reachability-metadata.json` 把我们的覆盖掉；旧格式名不会被覆盖且同样生效）。
  转换要点：`type`→`name`、跳过无点的原始类型、并补 `allDeclaredConstructors/Methods/Fields`（agent 对只 forName 的类不记成员，
  而 Jackson mixin 必须读到成员上的 `@JsonCreator` 才生效）。`resources` 段不要转（glob≠pattern），交给 Spring AOT。
- **Caffeine 在 native 下必须额外注册反射**：Caffeine 运行时用 `Class.forName` 加载生成的缓存实现类（如 `com.github.benmanes.caffeine.cache.SSMSA`）并反射读写其静态 `FACTORY` 字段；GraalVM reachability metadata 仓库只覆盖 `caffeine:3.1.8`，而本仓库锁定 `3.2.4`（生成类命名不同），否则 native 启动报 `ClassNotFoundException`/`MissingReflectionRegistrationError`。已在 `autumn-gateway-common` 与 example 的 `META-INF/native-image/.../reflect-config.json` 注册全部 238 个生成类（constructors+methods+fields）。**本仓库任何 native 构建只要用到 Caffeine 都需要这份配置**（dashboard/authorization 经 common 间接依赖 Caffeine）。
- **Windows 本机 native 构建**：`vcvars64.bat` 依赖 `reg.exe` 定位 Windows SDK 的 include 路径，但沙箱把 `reg.exe` 列入黑名单 → `INCLUDE` 未设置，`cl.exe` 编不出 `stdio.h`。本地构建改用 `D:/tmp/build-native2.bat`（手动 `set INCLUDE/LIB/PATH` 指向 VS2022 + Windows SDK `10.0.26100.0`），跳过 `vcvars64.bat`。102 Ubuntu VM 不受影响。


- **Spring Security 的 Jackson 模块在 native 下会静默加载失败**（`SecurityJacksonModules.loadAndGetInstance` catch 掉只打 DEBUG），
  症状是 `InvalidTypeIdException: Could not resolve type id 'java.util.Collections$UnmodifiableMap' ... denied resolution`
  —— 看着像 JDK 类没注册，真因是默认多态类型的白名单没建起来。排查：
  `--logging.level.org.springframework.security.jackson=DEBUG` → 满屏 `Cannot load module ...`。
- OAuth2 授权服务器 starter：`spring-boot-starter-security-oauth2-authorization-server`（Boot 4 新名），版本 4.1.1，底层 `spring-security-oauth2-authorization-server:7.1.1`。当前 Boot 4.1.1 / Java 25。
- 验证「依赖改名是否生效」必须 `clean compile` 或 `dependency:tree`。
- **Maven profile 坑**：命令行只要显式写了任何一个 `-P`，所有 `activeByDefault` 的 profile 都会被关闭。
  deploy 默认组件因此用「属性不存在则激活」：现在是 `component-authz` 的 `!no.authz`；其余组件显式启用。
  用法：默认=轻量 authz，`-Pcomponent-authn -Dno.authz`=OAuth2 SSO。**限流不再走 starter**：直接用官方 `RequestRateLimiter`+`Bucket4jRateLimiter`（Caffeine 后端，见 `autumn-gateway-examples-limit`），在路由 filter 里配即可，已移除 `limit-autumn-gateway*` 三个模块与 `component-limit` profile。
- 后台起服务要用 Bash 工具的后台任务；普通 `nohup ... &` 在命令返回后会被回收。

## 认证 / 授权边界（双发行形态，2026-10-22）
- **默认轻量版**：`authz-autumn-gateway-starter`。网关本地 `GET /gateway/endpoint/captcha`、`POST /gateway/endpoint/login`，
  Nimbus HS256 签 JWT；共享密钥必须由 `AUTUMN_SHARED_JWT_SECRET` 同时下发给 gateway 与 dashboard。
  dashboard-ui 持 token，所有请求使用标准 `Authorization: Bearer <jwt>`，禁止 query token。
- **路由级安全三档**：只注册 `AuthzGatewayFilterFactory`，**绝不注册 GlobalFilter/SecurityWebFilterChain**。
  route 不挂 Authz=匿名；`Authz=authenticate,<appCode>`=只验 JWT；`Authz=authorize,<appCode>`=验 JWT 后用 PreparedStatement 查角色/资源，HTTP Method + AntPathMatcher 判定。
  JDBC 阻塞查询必须在 `Schedulers.boundedElastic()`；SQL 通过 `autumn.authz.sql.*` 可配置。
- **OAuth2 SSO 版保留**：`-Pcomponent-authn -Dno.authz`，网关作为 OAuth2 客户端（`authn-autumn-gateway-starter`），
  authorization 9000 签发 RS256，WebSession + TokenRelay；完整说明见 `docs/OAuth2 统一认证登录使用手册.md`。
- dashboard `JwtTokenService` 是 Nimbus 双轨验签：先共享密钥 HS256，再可选 JWKS RS256；dashboard 不签发 token。
- 轻量版端口：网关 8080、dashboard 8081；登录/验证码均在网关 `/gateway/endpoint/*`。账号 `admin / Autumn@123456`。
- 待办：轻量版真实 DB 登录闭环（2026-10-22 本机到 192.168.0.102:3306 超时）、native 构建、权限缓存/主动失效、租户隔离；SSO 版另有单点登出待办。

## dashboard（后端）约定
- 本地起：`env -u SERVER__PORT SPRING_PROFILES_ACTIVE=h2database ./mvnw spring-boot:run`；默认 profile 已改为 MySQL（复用 102 库）。
- 登录由网关完成（轻量版 `/gateway/endpoint/login`；SSO 版见上节），admin 初始密码 `Autumn@123456`；`GET /api/system/rbac/status` 看 RBAC 就绪。
- Spring Security 7 包迁移：`oauth2.jwt`（Jwt/JwtDecoder/NimbusJwtDecoder）在 **`spring-security-oauth2-jose`**，
  `spring-security-oauth2-resource-server` 不再传递它；`oauth2.client.registration.*`（不是 `.client.*`）；
  `ClientSettings` 是 `ClientRegistration.ClientSettings` 内部类。
- RBAC 三档：匿名放行 → 认证即可（GET + 新增/编辑）→ 权限管控（assign-*、删除、启停、重置密码，`@PreAuthorize`）。
  `basic.rbac.enabled`(默认 true) / `basic.rbac.url-check`(默认 **false**，刻意：URL 层默认拒绝与"新增认证即可"冲突)。
- **超管 authorities 必须包含库里全部权限码**（`UserGrant.fullAccess`）；只给 `"*"` 会让 `hasAuthority` 拒掉管理员。权限码三段式 `system:user:create`，与 `PermCodes.java` 和 `basic_menu.menu_identification` 严格一致。
- `basic_resource.app_code` 必须是 `dashboard`。
- 不要对已有库再跑 `db/rbac/V2__rbac_init_data_mysql.sql`（固定 ID 段会造出两棵菜单树），就地升级用 `V4__rbac_fix_and_grant_mysql.sql`。
- 迁移脚本在 `src/main/resources/db/migration-vX-to-vY.sql`（v1→v6 已在 102 执行完）。种子数据由 `NamespaceDataInitializer` 幂等播种。
- `BasicResourceRepository.findWithPage` 的 `LIMIT :offset OFFSET :limit` 是既有约定，**不要"修复"**。
- Boot 4 迁移坑：`ContentCachingRequestWrapper` 包路径 `org.springframework.web.filter` → `org.springframework.web.util`，构造器必须传 `maxContentLength`。

## dashboard-ui（前端）约定
- maku-admin 风格，主色 `#409eff`。列表页：查询栏 → `.pro-toolbar` → 表格 → 分页。全局样式只在 `src/styles/pro.scss`。
- **只有 user、group 有删除接口**，别给 app/role/resource/menu/permission 加删除。
- **默认轻量 JWT 模式**：登录页是用户名/密码/验证码；调用 `/gateway/endpoint/login`，token 存 localStorage；axios 统一注入 `Authorization: Bearer`，不使用 Cookie 会话。
  `vite.config.ts` 的 `base` 必须是 `/`，开发代理同时覆盖 `/api` 与 `/gateway`。
- 当前 `src` 已清理同名 `.js` 产物，只保留 `.ts`；`tsconfig.json` 强制 `noEmit`。若以后又出现双份文件，Vite 的 `.js` 优先于 `.ts`，必须先清理旧 `.js`。
- 菜单图标必须存在于 `src/icons/iconfont/iconfont.js`（599 个 symbol），不存在会静默不显示。

## 工作记录
- **每次做完事都要写 `docs/工作记录.md`**（按日期倒序：做了什么 / 产物在哪 / 验证程度 / 下一步 / 环境备忘）。规划类文档放 `docs/网关认证授权演进规划.md`。

# autumn-gateway

Spring Cloud Gateway（WebFlux）之上的**可视化配置网关**：路由/断言/过滤器在 dashboard 里配、落库、运行时下发；认证鉴权、服务发现、配置中心、泳道路由按需装配。

| 项 | 值 |
|---|---|
| Maven 坐标 | `xyz.we-win.autumn:autumn-gateway:0.0.1-SNAPSHOT`（根 POM 为 parent） |
| Java 包根 | `xyz.wewin.autumn.gateway` |
| 技术基线 | Spring Boot 4.1.1 / Spring Cloud 2025.1.3 / Spring Cloud Gateway 5.0.3 / Java 25 |
| 扩展依赖 | `xyz.we-win.autumn:autumn-cloud-extension-*:1.0.0-RELEASE` |

> 坐标里的 `xyz.we-win` 是 Maven Central 校验过的域名反写（域名 `we-win.xyz`，带连字符）；
> Java 包名不允许连字符，所以包根统一写成 `xyz.wewin.autumn`。这与
> `autumn-cloud-extension`（包根 `xyz.wewin.autumn.cloud.extension`）同根，两边保持一致。

## 1. 模块结构

| 模块 | 作用 |
|---|---|
| `autumn-gateway-deploy` | 最终发行包。按 Maven profile 选组件、统一做 GraalVM native 构建 |
| `autumn-gateway-dashboard` | 可视化配置后台（WebMVC + JDBC）：路由/应用/角色/资源的增删改查 |
| `autumn-gateway-authorization` | 演示/配套的 OAuth2 授权服务器（RS256 + JWKS） |
| `autumn-gateway-common` | 公共工具（缓存等），零业务依赖 |
| `autumn-gateway-starters/authn-autumn-gateway-starter` | OAuth2 客户端登录（`TokenRelay`） |
| `autumn-gateway-starters/authz-autumn-gateway-starter` | 轻量 JWT 认证 + 路由级鉴权（`Authz=` FilterFactory） |
| `autumn-gateway-examples/*` | 可运行样例（basic、consul、discovery、limit、security、httpexchange…） |
| `autumn-gateway-dashboard-ui` | dashboard 前端（Vue 构建产物进 dashboard 的 `static/`） |

## 2. 构建

```bash
# 全量编译
mvn -B -DskipTests compile

# 默认发行形态：轻量 JWT 认证 + 路由级鉴权
mvn -B -pl autumn-gateway-deploy -am package

# OAuth2 单点认证（不用轻量 authz）
mvn -B -pl autumn-gateway-deploy -am package -Pcomponent-authn -Dno.authz

# 纯路由网关：组件全关
mvn -B -pl autumn-gateway-deploy -am package -Dno.authz
```

### 组件编排（`-Pcomponent-*`）

| profile | 装进来的能力 | 依赖坐标（groupId 均为 `xyz.we-win.autumn`） |
|---|---|---|
| `component-authn` | OAuth2 客户端登录（可选，与 authz 二选一优先） | `authn-autumn-gateway-starter` |
| `component-authz` | 轻量 JWT + 路由级鉴权（**默认开启**，`-Dno.authz` 关闭） | `authz-autumn-gateway-starter` |
| `component-discovery` | 服务发现：Consul HTTP API | `autumn-cloud-extension-discovery-consul-starter` |
| `component-discovery-nacos` | 服务发现：Nacos OpenAPI v1 | `autumn-cloud-extension-discovery-nacos-starter` |
| `component-discovery-multicast` | 服务发现：UDP 组播（零外部依赖，本地开发） | `autumn-cloud-extension-discovery-multicast-starter` |
| `component-config` | 配置中心：Consul KV | `autumn-cloud-extension-config-consul-starter` |
| `component-config-nacos` | 配置中心：Nacos 长轮询 | `autumn-cloud-extension-config-nacos-starter` |
| `component-traffic` | 泳道路由（`X-Traffic` 标签选实例） | `autumn-cloud-extension-traffic-starter` |
| `component-thrift` | Thrift 泛化路由（HTTP → Thrift，默认关） | `thrift-autumn-gateway-starter` |

> ⚠️ Maven 的坑：命令行只要显式写了任意一个 `-P`，所有 `activeByDefault` 的 profile 都会失效。
> 所以默认组件用「属性不存在则激活」（`!no.authz`）而不是 `activeByDefault`。

### 运行时形态（Spring profile，与 Maven profile 不是一回事）

| profile | 说明 |
|---|---|
| `db-mysql`（默认） | MySQL，需要注入 `DB_*` / `AUTUMN_SHARED_JWT_SECRET` |
| `db-postgresql` | PostgreSQL |
| `db-h2` | 内存库，零环境变量，用于启动/路由/限流冒烟 |

### GraalVM native

```bash
# 必须只针对单个模块，不能加 -am（parent 的 native profile 会报 Image classpath is empty）
mvn -B -pl autumn-gateway-deploy -am install -DskipTests
mvn -B -Pnative,native-image -pl autumn-gateway-deploy package -DskipTests
```

native 元数据放在 `src/main/resources/META-INF/native-image/xyz.we-win.autumn/<artifactId>/`，
目录名与 groupId/artifactId 一致。

## 3. 配置项

分四块：**网关自身**（`autumn.authn` / `autumn.authz` / `autumn.limit`）、
**注册发现**（`autumn.discovery`）、**配置中心**（`autumn.config`）、**泳道路由**（`autumn.traffic`）。
后三块由 [`autumn-cloud-extension`](../autumn-cloud-extension/README.md) 提供，本仓库的键名与默认值**逐项对齐**扩展库。

### 3.1 网关自身

#### `autumn.authn`（OAuth2 客户端登录，`-Pcomponent-authn`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false`（deploy 里显式关闭，默认走轻量 authz） | 总开关，`false` 时不注册任何认证 Bean |
| `registration-id` | `web-client` | 登录入口 `/oauth2/authorization/{registration-id}` |
| `client-id` / `client-secret` | `web-client` / `secret` | OAuth2 客户端凭据 |
| `issuer-uri` | `http://localhost:9000` | 授权服务器地址，OIDC 端点按约定拼接 |
| `scope` | `openid,profile` | 含 `openid` 时走 OIDC（会拿到 id_token） |
| `redirect-uri-template` | `{baseUrl}/login/oauth2/code/{registrationId}` | 回调地址，`{baseUrl}` 运行时展开 |
| `logout-url` | `/logout` | 登出路径 |

#### `autumn.authz`（轻量 JWT + 路由级鉴权，默认开启）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关，`false` 时网关退化为纯路由 |
| `app-code` | `dashboard` | 路由未给 appCode 时的兜底应用编码 |
| `login-app-code` | `dashboard` | 登录装配 authorities 用的应用编码 |
| `max-login-attempts` | `5` | 密码错误递减，减到 0 置 `status=0` |
| `jwt.secret` | 空（**必填**，deploy 取 `${AUTUMN_SHARED_JWT_SECRET}`） | 与 dashboard 验签必须同值，漏配直接启动失败 |
| `jwt.issuer` | `autumn-gateway` | 签发者 |
| `jwt.ttl-seconds` | `86400` | 令牌有效期 |
| `headers.user-id` / `headers.username` / `headers.roles` | `X-Autumn-User-Id` / `X-Autumn-Username` / `X-Autumn-Roles` | 透传给下游的身份头 |
| `captcha.enabled` | `true` | 登录验证码 |
| `captcha.length` | `4` | 验证码位数 |
| `captcha.ttl-seconds` | `180` | 验证码有效期 |
| `captcha.cookie-name` | `captchaId` | 验证码 cookie |
| `sql.user-query` / `roles-query` / `resource-query` / `login-failure-sql` / `login-success-sql` | 见 `application.properties` | SQL 全量外置；**列别名即契约**，覆盖时必须保留 |

#### `autumn.limit`（官方 RequestRateLimiter + Bucket4j 内存后端，默认关闭）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 为 `true` 才注册限流 Bean |
| `cache-maximum-size` | `10000` | 桶缓存条目上限 |
| `cache-expiration` | `1m` | 桶缓存过期 |
| `trusted-header` | 空 | 仅此时才读该头取真实 IP，须由边缘代理保证不可伪造 |

> `capacity` / `refill-tokens` / `refill-period` 写在**路由的 filter 参数**里：
> `RequestRateLimiter=...,capacity=4,refill-tokens=2,refill-period=10s`。

#### 路由（Spring Cloud Gateway 原生键）

```properties
spring.cloud.gateway.server.webflux.routes[0].id=user-route
spring.cloud.gateway.server.webflux.routes[0].uri=lb://user-service
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/users/**
spring.cloud.gateway.server.webflux.routes[0].filters[0]=Authz=authorize,user-app
```

### 3.2 注册发现（前缀 `autumn.discovery`）

与 autumn-cloud-extension 的 `autumn.discovery` 一致：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `backend` | 可省略 | `consul` / `nacos` / `multicast`。**只引一个 discovery Starter 时可省略**；同时引多个必须显式指定，否则启动失败 |
| `register` | `true` | 是否注册自身；**网关侧应设为 `false`**（只做发现者） |
| `service` | `spring.application.name` | 注册的服务名 |
| `host` | 自动探测本机 IP | 注册地址 |
| `port` | `server.port` | 注册端口 |
| `traffic` | `default` | 本实例泳道标签，写入 metadata `traffic` |
| `metadata.xxx` | - | 附加元数据 |

后端专属前缀：

| 前缀 | 键与默认值 |
|---|---|
| `autumn.discovery.consul` | `host`=`127.0.0.1`、`port`=`8500`、`scheme`=`http`、`token`、`namespace`、`health-check-ttl-seconds`=`30` |
| `autumn.discovery.nacos` | `server-addr`=`127.0.0.1:8848`、`namespace`=`public`、`group`=`DEFAULT`、`access-token`、`beat-interval-ms`=`5000` |
| `autumn.discovery.multicast` | `group`=`239.192.71.88`、`port`=`47891`、`ttl`=`1`、`heartbeat-interval-ms`=`3000`、`evict-after-ms`=`15000`、`network-interface` |

### 3.3 配置中心（前缀 `autumn.config`，可选）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | `false` 时完全不装配（真实生效） |
| `backend` | 无 | `consul` / `nacos`。**必须显式设置才启用**；拼错或没引对应 Starter 会启动失败 |
| `data-id` | `${spring.application.name}.properties` | 远程配置 dataId，内容按 `.properties` 解析 |
| `group` | 空 | 本次拉取分组，优先于 `default-group` |
| `default-group` | `DEFAULT_GROUP` | 通用默认分组 |
| `default-namespace` | 后端默认值 | Nacos 命名空间 / Consul datacenter |
| `refresh-enabled` | `true` | 是否注册变更监听；`false` 时只加载启动期配置 |

后端专属前缀：

| 前缀 | 键与默认值 |
|---|---|
| `autumn.config.consul` | `host`=`127.0.0.1`、`port`=`8500`、`scheme`=`http`、`token`、`datacenter`=`dc1`、`default-group`=`DEFAULT_GROUP` |
| `autumn.config.nacos` | `server-addr`=`127.0.0.1:8848`、`namespace`=`public`、`default-group`=`DEFAULT_GROUP`、`access-token`、`polling-timeout-ms`=`30000` |

### 3.4 泳道路由（前缀 `autumn.traffic`，可选）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `header` | `X-Traffic` | 携带泳道标签的请求头 |
| `fallback-to-main` | `true` | 目标标签无实例时回落 `default` 主干；`false` 则快速失败（503） |

语义：实例未打标、请求未带头，都归入 `default` 主干；只有 `lb://` 路由会走这段选择逻辑，
`uri=http://…` 的直连路由不受影响。

```bash
# 主干
curl --noproxy '*' http://127.0.0.1:8080/api/hello
# 灰度
curl --noproxy '*' -H "X-Traffic: gray" http://127.0.0.1:8080/api/hello
# 不存在的标签 → 回落主干
curl --noproxy '*' -H "X-Traffic: beta" http://127.0.0.1:8080/api/hello
```

### 3.5 与 autumn-cloud-extension 的对齐关系

| 能力 | 本仓库开启方式 | 依赖（`xyz.we-win.autumn:*:1.0.0-RELEASE`） | 配置前缀 |
|---|---|---|---|
| 服务发现 | `-Pcomponent-discovery` / `-discovery-nacos` / `-discovery-multicast` | `autumn-cloud-extension-discovery-{consul,nacos,multicast}-starter` | `autumn.discovery.*` |
| 配置中心 | `-Pcomponent-config` / `-config-nacos` | `autumn-cloud-extension-config-{consul,nacos}-starter` | `autumn.config.*` |
| 泳道路由 | `-Pcomponent-traffic` | `autumn-cloud-extension-traffic-starter` | `autumn.traffic.*` |

三块配置的前缀、键名、默认值全部取自扩展库的 `@ConfigurationProperties`，本仓库不做二次改名；
扩展库侧的完整说明见其 `README.md` 的「配置项」章节。
历史上网关自研的服务发现实现已删除，统一改用扩展库的 Consul/Nacos/组播实现。

### 3.6 Thrift 泛化路由（前缀 `autumn.thrift`，可选，默认关闭）

方向是单向的：**HTTP 请求进来（JSON 参数）→ 按 IDL 泛化转换成 Thrift 调用 → JSON 响应出去**。
网关在运行期解析 IDL、按 field id 编解码，不生成也不依赖任何 Thrift stub，后端仍是普通 Thrift 服务。

开启方式：

```bash
mvn -B -Pcomponent-authz,component-thrift -pl autumn-gateway-deploy -am package -DskipTests
# 还要在配置里显式打开（引入依赖 ≠ 开启能力）
# autumn.thrift.enabled=true
```

路由写法（`uri` 是占位，过滤器会短路，不会真的发 HTTP）：

```yaml
# 动态分发：一条路由覆盖整个服务，服务名/方法名从路径变量取（也可用请求头）
- id: thrift-dynamic
  uri: thrift://demo-service
  predicates:
    - Path=/thrift/{service}/{method}
  filters:
    - Authz=authenticate
    - ThriftGeneric

# 固定映射：一条路由一个方法
- id: thrift-demo
  uri: thrift://demo-service
  predicates:
    - Path=/thrift/demo
  filters:
    - ThriftGeneric=demoService,sayHello

# 固定映射 + 参数规则：第三参引用 autumn.thrift.mappings[].name
- id: thrift-demo-mapped
  uri: thrift://demo-service
  predicates:
    - Path=/thrift/greeting/{id}
  filters:
    - ThriftGeneric=demoService,sayHello,demoGreeting
```

**入参/出参可配置**（HTTP 的 header / queryString / requestBody / path 变量 / 常量 → thrift 参数）：

```properties
autumn.thrift.mappings[0].name=demoGreeting
autumn.thrift.mappings[0].include-body=true          # 请求体先作为参数基座
autumn.thrift.mappings[0].arguments[0].arg=name      # thrift 参数名
autumn.thrift.mappings[0].arguments[0].source=QUERY  # BODY/QUERY/HEADER/PATH/FIXED
autumn.thrift.mappings[0].arguments[0].key=userName  # query 参数名
autumn.thrift.mappings[0].arguments[0].required=true
autumn.thrift.mappings[0].arguments[1].arg=id
autumn.thrift.mappings[0].arguments[1].source=PATH
autumn.thrift.mappings[0].arguments[1].key=id
autumn.thrift.mappings[0].arguments[2].arg=tenant
autumn.thrift.mappings[0].arguments[2].source=HEADER
autumn.thrift.mappings[0].arguments[2].key=X-Tenant
autumn.thrift.mappings[0].arguments[3].arg=source
autumn.thrift.mappings[0].arguments[3].source=FIXED
autumn.thrift.mappings[0].arguments[3].value=gateway
autumn.thrift.mappings[0].response.mode=ENVELOPE     # ENVELOPE（默认）| DATA（只返回 data）
# autumn.thrift.mappings[0].response.success-status=200
```

不配 `mapping` 时保持最朴素的行为：请求体 JSON 整体作为参数（对象按参数名、数组按声明顺序）。

dashboard 侧对应「网关配置 → 协议转换」页（表 `gw_thrift_service` / `gw_thrift_conversion`），
配好之后由「配置同步」导出 `autumn.thrift.*` 到文件 / Consul / Nacos；上线初始化语句见
`docs/网关协议转换-上线初始化清单.md`。

动态分发解析优先级：路由写死 → 路径变量 `{service}` / `{method}` → 请求头 `X-Thrift-Service` /
`X-Thrift-Method`；都拿不到返回 400，不猜。

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `false` | 总开关；关着时 `ThriftGeneric` 路由直接 503，不碰网络 |
| `connect-timeout-millis` | `2000` | TCP 连接超时 |
| `socket-timeout-millis` | `5000` | 读超时 |
| `max-concurrent-calls` | `256` | 在飞调用上限，超出快速失败 |
| `framed` | `true` | 与后端 `transportFactory` 保持一致 |
| `multiplexed` | `false` | 后端用 `TMultiplexedProcessor` 时打开 |
| `expose-error-detail` | `false` | 是否把后端异常原文透给调用方 |
| `attachment-header-mapping` | `{X-Traffic: traffic}` | HTTP 头 → thrift 隐式参数（field-0 attachment） |
| `services[n].name` | — | 服务名，需与 IDL 中 `service` 名一致（IDL 只有一个 service 时可省） |
| `services[n].host` / `.port` | `127.0.0.1` / — | 后端地址（P1 改为注册中心解析） |
| `services[n].idl` | — | 内联 IDL（换行写 `\n`） |
| `services[n].idl-location` | — | `classpath:idl/x.thrift` 或 `file:/path/x.thrift` |
| `mappings[n].name` | — | 入参/出参规则名，路由过滤器第三参引用 |
| `mappings[n].include-body` | `true` | 请求体是否作为参数基座 |
| `mappings[n].arguments[k].arg/source/key/value/required/default-value` | `BODY`… | 参数规则：thrift 参数名 + 取值来源 + key + 常量 + 必填 + 兜底 |
| `mappings[n].response.mode` | `ENVELOPE` | `ENVELOPE` / `DATA` |
| `mappings[n].response.success-status` | `200` | 成功时覆盖状态码 |

响应语义与错误码：成功 200；`BAD_REQUEST` 400；后端声明异常 `SERVICE` 500；
`TApplicationException` 502；网络/超时 `TRANSPORT` 503。响应头带 `X-Thrift-Cost-Millis`。

```bash
curl --noproxy '*' -XPOST 'http://127.0.0.1:8080/thrift/demoService/sayHello' \
     -H 'Content-Type: application/json' -H 'X-Traffic: gray' -d '{"name":"gateway"}'
# {"success":true,"costMillis":12,"data":"hello gateway"}
```

`X-Traffic` 会被翻成 thrift 的隐式参数 `traffic` 随调用透传，与 HTTP 侧泳道语义一致。
分期规划见 `docs/网关Thrift泛化路由规划.md`（P0 已落地，P1 连接池 + 注册中心实例解析待做）。

## 4. 安全基线

- `AUTUMN_SHARED_JWT_SECRET` 没有默认值：漏配直接启动失败，不回落到公开开发密钥；
- 数据库口令、内网地址不进仓库，由 `application-db-*.properties` + 环境变量提供（见 `.env.example`）；
- `autumn.authz` 不注册 GlobalFilter，也不接管 Spring Security 过滤器链，只对显式写了 `Authz=` 的路由生效。

## 5. 相关文档

- `docs/本地启动与测试顺序.md` — 起 dashboard / authorization / gateway 的顺序与命令
- `docs/权限与授权设计.md` — 认证授权模型
- `docs/OAuth2 统一认证登录使用手册.md` — OAuth2 单点配方与 native 元数据再生成
- `docs/网关可视化配置-表结构设计.md`、`docs/网关可视化配置-常用配置参数清单.md` — dashboard 侧
- `docs/网关Thrift泛化路由规划.md` — Thrift 泛化的能力边界与 P0–P3 分期
- `docs/工作记录.md` — 变更流水

## 6. 能力规划（原 README 保留）

### 限流
- 限制上游流量：URL / IP / Other
- 限制下游：服务限流 / 服务的节点限流

### 负载均衡
- 轮询
- 自定义
- 按流量标签负载均衡（已由 `autumn-cloud-extension-traffic-starter` 提供）

### 服务发现
- 解决 Consul 无法 Native 问题（已由扩展库 HTTP API 实现解决）
- 解决 Nacos 无法 Native 问题（同上）

### 配置中心
- 解决 Consul 无法 Native 问题
- 解决 Nacos 无法 Native 问题

### 度量观察
- OpenTelemetry

### 安全
- 认证
- 授权

## License

Apache License 2.0

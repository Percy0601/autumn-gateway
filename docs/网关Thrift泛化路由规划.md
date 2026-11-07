# 网关 Thrift 泛化路由演进规划（P0–P3）

> 状态：P0 已落地（`thrift-autumn-gateway-starter`），P1–P3 待排期
> 关联代码：`autumn-service-framework/autumn-protocol-extension`（`xyz.we-win.autumn:1.0.0-RELEASE`）
> 关联文档：`docs/工作记录.md`、`README.md`

## 一、结论先行

网关不生成任何 Thrift stub，直接按 IDL 在运行时编解码，把 HTTP 请求转成对后端 Thrift 服务的调用——**这件事能办，而且编解码这一层在 `autumn-protocol-extension` 里已经做完了**，网关侧只是"接线"。

已验证的底层能力（该模块 20 个测试全绿，含真实 TCP 往返）：

| 能力 | 类 | 对网关的意义 |
|---|---|---|
| IDL 运行时解析 | `IdlParser` | 新增服务只要给一份 IDL，不用编译、不用发版 |
| 服务描述表 | `ServiceRegistry` / `ServiceSpec` / `MethodSpec` | 支持热注册，控制面下发即可生效 |
| 按 field id 编解码 | `ThriftCodec` | 无反射（GraalVM 友好），未知字段自动跳过 |
| 调用门面 | `GenericThriftInvoker` / `GenericThriftClient` | framed / multiplexed / 超时 / 同步 + 异步 |
| 隐式参数透传 | `AttachableBinaryProtocol`（args 的 field-0 MAP） | 泳道、链路 ID 可跨服务透传，且**不走 ThreadLocal**，适配 WebFlux |

依赖只有 `autumn-api` + `libthrift 0.25.0` + `slf4j`，不带 Spring，体积与耦合都可控。

## 二、总体数据流

```
HTTP 请求（POST /thrift/demo，body = 参数 JSON）
        │
        ▼
RoutePredicateHandlerMapping ──命中带 ThriftGeneric 过滤器的路由──▶ ThriftGenericGatewayFilterFactory
        │                                                              │ 1. 读 body → 参数 Map/List
        │                                                              │ 2. 查 ServiceRegistry 拿 MethodSpec
        │                                                              │ 3. 拼 attachment（X-Traffic → traffic）
        │                                                              ▼
        │                                                    GenericThriftInvoker（boundedElastic 线程池）
        │                                                              │  TBinaryProtocol(+TFramed) 直连
        │                                                              ▼
        │                                                      后端 Thrift 服务
        │◀─────────────── 短路返回 JSON：GenericResponse.asMap() ────────┘
```

要点：过滤器**不调用 `chain.filter`**，自己在 `ServerWebExchange` 上写响应；SCG 的 `NettyRoutingFilter` 不会被执行，所以路由的 `uri` 只是占位（写 `thrift://<service>` 表示语义）。

## 三、分期

| 期 | 目标 | 交付物 | 验收标准 | 估时 |
|---|---|---|---|---|
| **P0（已完成）** | 打通"能调通一次"，纯增量、默认关 | `thrift-autumn-gateway-starter`；`ThriftGeneric=<service>,<method>,<mapping>` 路由过滤器；HTTP 入参/出参可配置；dashboard「协议转换」页 + `gw_thrift_service`/`gw_thrift_conversion` 两表首版 | 单测 30 个全绿 + 真实 TCP 往返；`autumn.thrift.enabled=false` 时既有路由零变化；dashboard 配置能导出 `autumn.thrift.*` | 1–2 天 |
| **P1** | 能扛并发，能按注册中心选实例 | 连接池（按 host:port 复用 + 失效剔除）；实例解析从静态配置切到 discovery；泳道基线统一 | 压测下不出现端口耗尽；`X-Traffic: gray` 的请求只落在 gray 实例 | 2–3 天 |
| **P2** | "动态"补齐到控制面 | 网关侧热注册（`ServiceRegistry.registerIdl`）+ 发布期 IDL 校验 + 与 dashboard 配置变更联动 | 新增一个 IDL 不重启网关即可调用；坏 IDL 在发布阶段就被拒 | 3–5 天 |
| **P3** | 运维可用 | metrics / 慢调用日志 / 熔断降级 / compact 协议 / native-image 冒烟 | 指标可观测；`-Pnative` 构建出可跑的泛化调用 | 视需求 |

### P0 边界（已落地）

- 新增模块 `autumn-gateway-starters/thrift-autumn-gateway-starter`，坐标 `xyz.we-win.autumn:thrift-autumn-gateway-starter`。
- 路由过滤器 `ThriftGeneric`，三种用法：
  - 固定映射：`ThriftGeneric=<service>,<method>`（一条路由一个方法）；
  - 固定映射 + 参数规则：`ThriftGeneric=<service>,<method>,<mapping>`（第三参引用 `autumn.thrift.mappings[].name`）；
  - 动态分发：裸写 `ThriftGeneric`，服务名与方法名从 `Path=/thrift/{service}/{method}` 的路径变量解析，路径里没有时退到请求头 `X-Thrift-Service` / `X-Thrift-Method`——**一条路由覆盖整个服务，这才是"泛化"的常态用法**。
- **HTTP endpoint 入参/出参可配置**（对应 ShenYu 的 `RpcParamTransformPlugin` + `param-mapping`）：
  - 每个参数一条规则：`arg`（thrift 参数名）+ `source`（`BODY`/`QUERY`/`HEADER`/`PATH`/`FIXED`）+ `key`（字段名/参数名/头名/路径变量）+ `value`（FIXED 常量）+ `required` + `default-value`；
  - `include-body=true`（默认）时请求体 JSON 先作为参数基座，规则逐条覆盖/补充；`false` 时只看规则取值；
  - 出参 `response.mode`：`ENVELOPE`（默认，`{success,costMillis,data|errorType,message}`）或 `DATA`（只返回 data 本身）；`response.success-status` 可覆盖成功状态码；
  - 必填项缺失 → 400，且不会发起 thrift 调用；映射规则名不存在 → 500 `unknown_mapping`（装配期就拦住，不等到请求）。
- 方向固定为 HTTP → Thrift：过滤器自己写响应并短路 `chain.filter`，`NettyRoutingFilter` 不会执行，`uri` 只是占位。
- 服务端点来自 `application.properties`（`autumn.thrift.services[]`），IDL 支持内联与 `classpath:` / `file:` 两种来源。
- 默认关闭：deploy 侧只有选中 `component-thrift` profile 才引入依赖，且 `autumn.thrift.enabled` 默认 `false`（引入但不开，过滤器返回 503 而不是去连后端）。
- 鉴权沿用既有 `Authz=` 过滤器，排在 `ThriftGeneric` 之前即可覆盖；不给 snapshot 另立鉴权逻辑。
- 响应体固定为 `GenericResponse.asMap()`：`{success, costMillis, data}` 或 `{success, costMillis, errorType, message}`。

### P1 待办明细

1. **连接池**：当前 `GenericThriftInvoker` 每次调用都 `new TSocket` + `open/close`，高并发下会打满本地端口并放大握手开销。做法：`ThriftConnectionPool`（按 `host:port` 分桶，`ArrayBlockingQueue` 借还，`socketTimeout` 到期归还时校验存活），或在 P1 末期评估换 Netty transport 走真异步。
2. **泳道基线统一**：`autumn-cloud-extension` 用 `Traffic.DEFAULT = "default"`，`autumn-protocol-extension` 的 `TrafficRouter.DEFAULT_LANE = "main"`。网关要"HTTP 与 thrift 同一条泳道"，必须先统一成 `default`（或让 `TrafficRouter` 可配置），否则灰度和主干会错位。
3. **实例解析**：`autumn.thrift.services[].host/port` 换成"服务名 → 注册中心实例列表"，复用 `autumn-cloud-extension` 的 traffic 选择器，保证与 HTTP 侧同一套泳道语义。

### P2 待办明细

1. dashboard 建表首版已落地（P0 顺手做了）：`gw_thrift_service`（服务 + IDL）+ `gw_thrift_conversion`（入参/出参规则），页面「网关配置 → 协议转换」，导出并入 `GwConfigSyncService`；P2 要补的是"改了就能生效"：
2. 网关侧监听配置变更 → `ServiceRegistry.registerIdl()` 热注册；IDL 解析失败时保留旧版本并告警，不整体回滚。
3. 发布前校验：IDL 语法、方法是否存在、与已有同名服务的兼容性（字段 id 不复用）。

### P3 待办明细

- 指标：`gateway_thrift_calls_total{service,method,errorType}`、`gateway_thrift_cost_seconds`。
- 熔断：按 `service` 维度统计失败率，超阈值直接 503 并进入半开探测。
- 协议补全：`compact`；`IDL` 支持 `include` / `typedef` / `const` / `service extends`（当前这些会被 `IdlParser` 拒绝）。
- native：把 `IdlParser` / `ThriftCodec` 的反射点（如果有）补进 `reflect-config.json`，`-Pnative` 出镜像后跑一次真实调用。

## 四、已知硬点（谁来解决）

| 硬点 | 影响 | 承担阶段 |
|---|---|---|
| 每次调用新建 TSocket，无连接池 | 并发一高就打满端口、延迟抖动 | P1 |
| 泳道基线 `main` vs `default` 不一致 | HTTP 与 thrift 的灰度/主干错位 | P1（统一前 thrift 侧先不传泳道） |
| 控制面没有 IDL 存储 | "动态"只剩一半，改服务要改配置重启 | P2 |
| 仅支持 binary(+framed) / multiplexed | 用 compact 的后端接不了 | P3 |
| IDL 不支持 include/typedef/const/service extends | 跨文件 IDL 需要先拍平 | P3（现阶段靠发布期校验提示） |
| attachment 用 args 的 field-0 承载 | 标准生成的服务端会把未知字段跳过，但自定义 processor 未必 | P0 已用真实 TCP 服务验证过一次；P1 联调真实业务服务再确认 |

## 五、配置项

P0 已实现（`autumn.thrift.*`）：

| 键 | 默认 | 说明 |
|---|---|---|
| `autumn.thrift.enabled` | `false` | 总开关。false 时过滤器直接返回 503，不连后端 |
| `autumn.thrift.connect-timeout-millis` | `2000` | TCP 连接超时 |
| `autumn.thrift.socket-timeout-millis` | `5000` | 读超时 |
| `autumn.thrift.max-concurrent-calls` | `256` | 并发闸门，超出直接 503 |
| `autumn.thrift.framed` | `true` | 全局默认 framed |
| `autumn.thrift.multiplexed` | `false` | 全局默认 multiplexed |
| `autumn.thrift.expose-error-detail` | `false` | 是否把后端错误信息透给调用方 |
| `autumn.thrift.attachment-header-mapping` | `{X-Traffic: traffic}` | HTTP 头 → attachment key |
| `autumn.thrift.services[n].name` | — | 服务名，需与 IDL 中 `service` 名一致（IDL 只有一个 service 时可省略） |
| `autumn.thrift.services[n].host` / `.port` | `127.0.0.1` / — | 后端地址（P1 改为注册中心解析） |
| `autumn.thrift.services[n].idl` | — | 内联 IDL 文本 |
| `autumn.thrift.services[n].idl-location` | — | `classpath:idl/xxx.thrift` 或 `file:/path/xxx.thrift` |
| `autumn.thrift.services[n].multiplexed` / `.framed` | 继承全局 | 单服务覆盖 |
| `autumn.thrift.mappings[n].name` | — | 规则名，路由过滤器第三参引用 |
| `autumn.thrift.mappings[n].include-body` | `true` | 请求体是否作为参数基座 |
| `autumn.thrift.mappings[n].arguments[k].arg` | — | thrift 参数名 |
| `autumn.thrift.mappings[n].arguments[k].source` | `BODY` | `BODY`/`QUERY`/`HEADER`/`PATH`/`FIXED` |
| `autumn.thrift.mappings[n].arguments[k].key` | 同 `arg` | 字段名 / query 名 / 头名 / 路径变量名 |
| `autumn.thrift.mappings[n].arguments[k].value` | — | `FIXED` 时的常量值 |
| `autumn.thrift.mappings[n].arguments[k].required` | `false` | 缺值是否 400 |
| `autumn.thrift.mappings[n].arguments[k].default-value` | — | 取不到值时的兜底 |
| `autumn.thrift.mappings[n].response.mode` | `ENVELOPE` | `ENVELOPE` / `DATA` |
| `autumn.thrift.mappings[n].response.success-status` | `200` | 成功时覆盖 HTTP 状态码 |

路由写法：

```yaml
# 用法一：动态分发，一条路由覆盖整个服务的所有方法（推荐）
- id: thrift-dynamic
  uri: thrift://demo-service      # 占位，ThriftGeneric 短路，不会真的发 HTTP
  predicates:
    - Path=/thrift/{service}/{method}
  filters:
    - Authz=authenticate           # 可选，放在 ThriftGeneric 之前
    - ThriftGeneric

# 用法二：固定映射，一个路由一个方法
- id: thrift-demo
  uri: thrift://demo-service
  predicates:
    - Path=/thrift/demo
  filters:
    - ThriftGeneric=demoService,sayHello

# 用法三：固定映射 + 参数规则（第三参引用 mappings 里的 name）
- id: thrift-demo-mapped
  uri: thrift://demo-service
  predicates:
    - Path=/thrift/greeting/{id}
  filters:
    - ThriftGeneric=demoService,sayHello,demoGreeting
```

参数规则的对应配置（把 query/header/path 拼进同一组 thrift 参数）：

```properties
autumn.thrift.mappings[0].name=demoGreeting
autumn.thrift.mappings[0].arguments[0].arg=name
autumn.thrift.mappings[0].arguments[0].source=QUERY
autumn.thrift.mappings[0].arguments[0].key=userName
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
autumn.thrift.mappings[0].response.mode=DATA
```

调用示例（参数按 IDL 的参数名给，JSON 数组则按声明顺序）：

```bash
# 动态：路径里直接写服务名和方法名
curl -XPOST 'http://localhost:8080/thrift/demoService/sayHello' \
     -H 'Content-Type: application/json' -H 'X-Traffic: gray' \
     -d '{"name":"gateway"}'
# → {"success":true,"costMillis":12,"data":"hello gateway"}

# 固定映射的路由，路径由 route 决定
curl -XPOST 'http://localhost:8080/thrift/demo' \
     -H 'Content-Type: application/json' -d '{"name":"gateway"}'

# 动态：不想把服务名放路径里，就用请求头
curl -XPOST 'http://localhost:8080/thrift/any' \
     -H 'X-Thrift-Service: demoService' -H 'X-Thrift-Method: sayHello' \
     -H 'Content-Type: application/json' -d '{"name":"gateway"}'
```

响应语义：

| 结果 | HTTP 状态 |
|---|---|
| 成功 | 200 |
| 参数/IDL 校验失败（`BAD_REQUEST`） | 400 |
| 后端声明异常（`SERVICE`） | 500 |
| 后端 `TApplicationException`（`APPLICATION`） | 502 |
| 网络/超时（`TRANSPORT`） | 503 |
| 总开关关闭 / 并发超限 | 503 |

## 六、参照 Apache ShenYu（HTTP → Dubbo）

ShenYu 的"协议转换"不是另写一套网关，而是：plugin（dubbo）→ selector/rule（按 URI/Header/Query/Cookie 匹配流量）→ metadata（serviceName/methodName/parameterTypes）→ `RpcParamTransformPlugin` 把 HTTP 参数放进 exchange → 泛化调用 → `ResponsePlugin` 统一回写。我们的三段式与它一一对应：

| ShenYu | 我们 | 落点 |
|---|---|---|
| selector / rule | SCG 路由 Predicate（`Path` / `Header` / `Query`） | `gw_route` 既有能力，不新增 |
| metadata（接口 + 方法 + 参数类型） | `gw_thrift_service`：端点 + IDL（免接口 jar，参数类型从 IDL 推） | 新表 + `autumn.thrift.services[]` |
| RpcParamTransformPlugin / param-mapping | `gw_thrift_conversion.arguments_json`（BODY/QUERY/HEADER/PATH/FIXED） | 新表 + `autumn.thrift.mappings[]` |
| GenericService | `GenericThriftInvoker`（IDL 驱动） | P0 已验通 |
| admin 落库 → 数据同步 → 网关缓存 | dashboard 落库 → `GwConfigSyncService` 导出 → Consul/Nacos | P0 首版已接上 |

差异（刻意的）：ShenYu 靠客户端 SDK 注解自动注册 metadata；我们 P0 先"页面手填服务 + IDL"，P2 再做注册中心 / 热注册。ShenYu 用 Dubbo 的 GenericService（需要知道参数类型列表），我们用 IDL 自己做编解码，参数类型由 IDL 决定，调用方不用报类型。

## 七、不做的事（刻意的克制）

- **不做 SCA（双向协议网关）**：P0 只做 HTTP → Thrift 的单向；Thrift 服务端反向调 HTTP 不在范围内。
- **不在 P0 做 IDL 编辑器的语法高亮/校验器**：页面先给 textarea，校验在发布期（P2）做。
- **不动既有 HTTP 路由**：新增能力全部在独立 starter 与独立 profile 里，默认关。

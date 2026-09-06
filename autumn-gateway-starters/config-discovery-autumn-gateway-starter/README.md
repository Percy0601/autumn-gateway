# config-discovery-autumn-gateway / config-discovery-autumn-gateway-starter

轻量级 Consul 配置中心 + 自动刷新（参考 spring-cloud-consul-config，仅保留核心能力）。

- `config-discovery-autumn-gateway`：核心实现（Consul HTTP 客户端 / ConfigData 加载 / 自动刷新），无容器依赖，可独立测试。
- `config-discovery-autumn-gateway-starter`：Spring Boot 自动装配启动器。
- `config-discovery-autumn-gateway-starter-using`：使用示例（含网关路由热更新演示）。

## 设计目标与取舍

| 需求 | 实现方式 |
|---|---|
| HTTP API 拉配置 | Consul KV REST 接口 `/v1/kv/{prefix}?recurse=true` |
| 最少依赖 / 轻量 | 仅 `spring-boot` + `spring-cloud-context` + `jackson-databind` + `slf4j-api`；HTTP 用 JDK 自带 `java.net.http.HttpClient`，无 WebClient/RestTemplate/consul-client |
| 自动刷新 | Consul **Blocking Query** 长轮询（`index` + `wait`），变化即时返回，非固定间隔轮询 |
| 兼容 @Value / @ConfigurationProperties | 配置以标准 `PropertySource` 注入 Environment，天然兼容两种绑定 |
| 兼容 GraalVM Native Image | 仅 `JsonNode` 字段遍历解码（无反射）；类型绑定走 Boot 编译期绑定；HTTP 为 JDK 内置模块 |

## 工作原理

```
Environment 准备阶段（Boot ConfigData）
  application.properties 里 spring.config.import=consul:
        │  (resolver/loader 通过 META-INF/spring.factories 注册，与
        │   spring-cloud-config-client 5.x 适配 Boot 4 的方式一致，无需容器)
        ▼
ConsulConfigDataLocationResolver ── 用 Binder 绑定 consul.config.*（此时读本地 application.*）
        ▼
ConsulConfigDataLoader ── HTTP GET /v1/kv/config/{application}?recurse
        ▼
PropertiesPropertySource("consul-config:config/application") 注入 Environment
                        └── key 相对路径即 property key（config/order/x.y=1 → x.y=1）
        ▼
@Value / @ConfigurationProperties 正常绑定

运行期（starter 自动装配 ConsulConfigWatchRefresher，可配开关）
  GET /v1/kv/{root}?recurse&index={lastIndex}&wait=..s   （长轮询）
        ▼ X-Consul-Index 变化
  用新值替换同名 PropertySource + 发布 EnvironmentChangeEvent
        ▼
  @RefreshScope Bean（@Value / @ConfigurationProperties）自动重建
  网关路由侧可监听 EnvironmentChangeEvent → RefreshRoutesEvent（见 using 模块）
```

## KV 布局（与 spring-cloud-consul-config 语义一致）

```
config/application/{propertyKey}      ← 全局默认（所有应用共享）
config/{spring.application.name}/...  ← 当前应用（优先级更高，可覆盖）
```

## 使用

1. 引入 starter（using 模块示例）：
```xml
<dependency>
    <groupId>xyz.wewin.autumn</groupId>
    <artifactId>config-discovery-autumn-gateway-starter</artifactId>
</dependency>
```

2. `application.properties`：
```properties
spring.application.name=order-service
spring.config.import=consul:

consul.config.host=localhost
consul.config.port=8500
consul.config.prefix=config
consul.config.fail-fast=true        # false：Consul 拉取失败也继续启动
consul.config.watch-enabled=true    # 动态刷新开关
consul.config.watch-delay=15s
```

3. 准备 KV：
```bash
consul kv put config/application/demo.message hello-from-application
consul kv put config/order-service/demo.message hello-from-order
consul kv put config/order-service/demo.feature-enabled true
```

4. 代码侧（刷新能力由 @RefreshScope 提供）：
```java
@Component
@RefreshScope
@ConfigurationProperties(prefix = "demo")
public class DemoProperties { /* ... */ }
```
```java
@RefreshScope
@RestController
class DemoController {
    @Value("${demo.message}")
    private String message; // 配置变更后随 bean 重建刷新
}
```

## 需要动态刷新的 Bean 请加 @RefreshScope

环境变化后 `EnvironmentChangeEvent` 只会触发两类刷新：
- 标了 `@RefreshScope` 的 Bean（重建时重新绑定 @Value / @ConfigurationProperties）；
- 业务自己监听 `EnvironmentChangeEvent` 的逻辑（如路由热更新）。

普通（非 RefreshScope）Bean 不会自动更新，属 Spring 语义预期。

## GraalVM Native 提示

- 全部解码路径为 `JsonNode` 字段遍历，无反射；
- HTTP 为 JDK 模块，无需额外配置；
- 若使用 `@ConfigurationProperties`，建议配合 `spring-boot-configuration-processor` 生成元数据（starter 已 optional 引入）；
- 编译 native：参考 Spring Boot 官方 Native 构建方式把使用示例打成可执行镜像（本模块代码路径无反射，不需要额外 hints）。

## 与官方 spring-cloud-consul-config 的差异（有意为之）

- 不支持：watch 变更 key 级 diff 精确定位（整 context 替换）、多 profile context（`config/{app}-{profile}`）、properties/yaml 大文本格式解析、故障转移缓存、服务发现（discovery 另做）。
- KV 约定：每个 KV 即一个 property（key=property key），不做大文本格式解析，天然无反射。

## 已知边界

- 刷新单线程串行长轮询多个 context，context 较多时可调小 `watch-delay`；
- Consul ACL 仅支持 token（X-Consul-Token），不支持 mTLS。

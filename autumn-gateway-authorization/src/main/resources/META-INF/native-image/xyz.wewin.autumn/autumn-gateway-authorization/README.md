# 本目录说明

`reflect-config.json` 是 **GraalVM tracing agent 自动生成**的，不要手改。

> ⚠️ **为什么是 `reflect-config.json` 而不是 `reachability-metadata.json`**：
> Spring Boot 的 `process-aot` 会把自己的产物写到
> `META-INF/native-image/<group>/<artifact>/reachability-metadata.json`，
> **正好覆盖我们放同名的文件**（编译后 `target/classes` 里的会被换成 AOT 那份）。
> 改用旧格式的 `reflect-config.json` 就不会被覆盖，native-image 同样认，且与 AOT 那份合并生效。

## 它是怎么来的

授权服务器把 `oauth2_registered_client` / `oauth2_authorization` 里的
`client_settings`、`token_settings`、`attributes` 等列，用**开启了默认多态类型（default typing）的 Jackson**
写成带 `@class` 的 JSON。native image 下读回来要 `Class.forName(...)` + 一堆 mixin 反射，
手工枚举根本列不全（模块类、mixin 类、`UsernamePasswordAuthenticationToken`、
`SimpleGrantedAuthority`、`OAuth2TokenFormat`……）。

正确做法是用 tracing agent 在 JVM 上跑一遍真实流程，让它把用到的反射/资源全部记下来：

```bash
# 102 虚拟机
export JAVA_HOME=/home/percy/opt/graalvm-jdk-25.0.3+9.1
rm -rf /tmp/nia-authz && mkdir -p /tmp/nia-authz
java -agentlib:native-image-agent=config-output-dir=/tmp/nia-authz \
     -jar autumn-gateway-authorization-0.0.1-SNAPSHOT.jar \
     --autumn.security.captcha.enabled=false
# 跑一遍：登录 → /oauth2/authorize（PKCE）→ /oauth2/token → /userinfo → refresh_token
# 然后 kill -TERM <pid>（必须是 TERM，agent 在 JVM 退出时落盘）
# 产物：/tmp/nia-authz/reachability-metadata.json

# 转成 reflect-config.json（type→name，丢掉原始类型），放进本目录再重新 native 构建
```

转换脚本（一次性，Python）：

```python
import json
d = json.load(open('/tmp/nia-authz/reachability-metadata.json'))
out = []
for e in d['reflection']:
    t = e.get('type') if isinstance(e, dict) else None
    if not isinstance(t, str) or '.' not in t:   # 跳过 boolean / boolean[]
        continue
    entry = {'name': t}
    if 'methods' in e: entry['methods'] = e['methods']
    if 'fields' in e:  entry['fields']  = e['fields']
    out.append(entry)
json.dump(out, open('reflect-config.json', 'w'), ensure_ascii=False, indent=2)
```

> `resources` 段**不要转**：agent 用的是新格式的 `glob`，旧 `resource-config.json` 用 `pattern` 正则，
> 语义不同容易转错；而且 Spring AOT 已经把该注册的资源都注册了，缺的那部分由 Spring 自己管。

流程脚本见 `docs/` 下的使用手册（「附录：native 元数据怎么重新生成」）。

## 什么时候要重新生成

- 升级 Spring Security / Spring Boot 版本；
- 新增了会进 `oauth2_authorization.attributes` 的认证类型（如短信登录、微信登录的 Authentication 实现）；
- native 启动报 `InvalidTypeIdException` / `Cannot construct instance of ...`。

## 与 AuthorizationRuntimeHints 的关系

`AuthorizationRuntimeHints` 是手写版本，只覆盖当时排查出来的类型，作为可读的「根因说明」保留；
真正兜底的是这个自动生成的文件。两者并存，重复注册无害。

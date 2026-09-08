### 1、验证 OIDC 元数据（OIDC 职责）

```
http://localhost:9000/.well-known/openid-configuration

```
会返回完整的 OIDC 协议端点、支持的 scope、签名算法等信息，这是 OIDC 合规的标志。

### 2、验证 JWKS 公钥端点（JWKS 职责）

```
http://localhost:9000/oauth2/jwks

```
返回 RSA 公钥信息，网关和业务服务就是用这个公钥校验 JWT 签名。

### 3、授权码模式登录拿令牌（账号登录 + 令牌颁发职责）

> 注意：OAuth 2.1（Spring Authorization Server 7.x）下 **PKCE 是强制的**，
> 授权请求必须带 `code_challenge` / `code_challenge_method=S256`，否则会返回
> `invalid_request: OAuth 2.0 Parameter: code_challenge`。

第一步：生成 PKCE 参数并访问授权端点
```bash
CODE_VERIFIER=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')
CODE_CHALLENGE=$(printf "%s" "$CODE_VERIFIER" | openssl dgst -binary -sha256 | openssl base64 | tr '+/' '-_' | tr -d '=\n')
echo "$CODE_VERIFIER"   # 换令牌时要用，先保存
echo "$CODE_CHALLENGE"  # 授权请求时要用
```
浏览器访问（CODE_CHALLENGE 替换为上一步的值）：
```
http://localhost:9000/oauth2/authorize?response_type=code&client_id=web-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid profile business:read&code_challenge=CODE_CHALLENGE&code_challenge_method=S256
```
会跳转到登录页，输入 `user / password` 登录，登录成功后会重定向到回调地址，地址栏 `code=` 后面的值就是授权码。

第二步：用授权码换取令牌（必须带上 code_verifier）
```
curl -X POST \
  http://localhost:9000/oauth2/token \
  -u web-client:secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=你的授权码&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=CODE_VERIFIER"
```
成功会返回 `access_token`、`refresh_token`、`id_token`（OIDC）。

附：客户端凭证模式（无需登录，适合服务间调用）
```
curl -X POST http://localhost:9000/oauth2/token \
  -u web-client:secret \
  -d "grant_type=client_credentials&scope=business:read"
```

### 4、验证 userinfo 端点（OIDC 职责）
```
curl -H "Authorization: Bearer 你的access_token" http://localhost:9000/userinfo
```
返回当前登录用户的基本信息（默认只返回 `sub`，需要更多字段要在 `OidcConfigurer` 上配置 `userInfoMapper`）。

> 说明：`/userinfo` 要求请求先通过 bearer token 认证，因此授权服务器过滤器链里开启了
> `oauth2ResourceServer().jwt()`，并注册了 `JwtDecoder`（用 JWKS 公钥校验 access_token）。
> 缺少任意一个都会导致该端点 401。

## JWT 签名密钥（JWKS）持久化

密钥必须持久化，否则：

- 每次重启都换密钥 → 已签发的令牌全部失效
- 多实例各用各的密钥 → A 实例签的令牌 B 实例验不过

三种存储方式通过 `autumn.authorization-server.jwk.store-type` 切换，原实现完整保留：

| store-type | 说明 | 类 |
|---|---|---|
| `database`（默认） | 落 `oauth2_jwk` 表，重启/多实例共用同一把密钥，首次启动自动生成 | `JdbcJwkConfig` |
| `file` | 落 JSON 文件，适合单实例或密钥由外部下发 | `FileJwkConfig` |
| `memory` | 原始实现：每次启动随机生成，**仅演示用** | `JwkConfig` |

```properties
autumn.authorization-server.jwk.store-type=database
autumn.authorization-server.jwk.key-id=autumn-gateway-authorization-key
#autumn.authorization-server.jwk.file-path=./config/authorization-jwk.json
```

- 密钥生成/序列化公共逻辑在 `JwkSupport`（序列化显式传 `false`，否则只输出公钥，读回来签不了名）
- 多实例并发启动时只有一个实例能写入，其余复用已有记录（按 `keyId` 主键冲突处理）
- **安全提醒**：`oauth2_jwk.jwk_set_json` 含私钥，生产建议加密存储并收紧库账号权限

## 登录页、验证码与账号锁定

### 登录页

已弃用 Spring Security 默认登录页，改为自定义 SPA 登录页（Vue3 + Vite + Pinia + UnoCSS）：

- 工程目录：`autumn-gateway-authorization-ui`
- 构建产物：`autumn-gateway-authorization/src/main/resources/static`（后端启动即可访问）
- 前端**不挂在 Maven 生命周期**里，属于纯浏览器端资产，不影响 GraalVM native image 构建

```bash
cd autumn-gateway-authorization-ui
npm install          # 若本机 npm 设置了 omit=dev，请用：npm install --include=dev
npm run dev          # 本地调试（已配置代理到 9000）
npm run build        # 产出到后端 static 目录
```

后端相关接口：

| 接口 | 说明 |
|---|---|
| `GET /login`、`GET /` | 转发到 `index.html` |
| `GET /api/captcha` | SVG 验证码（点击图片即刷新） |
| `GET /api/csrf` | 获取 CSRF 参数（登录表单需带 `_csrf`） |
| `POST /login` | 表单登录，返回 JSON（`success` / `code` / `remainingAttempts`） |

登录成功会返回 `redirect`：若来自 `/oauth2/authorize`，会跳回原授权请求，授权码流程不会断。

### 验证码（零依赖，GraalVM 友好）

自研 SVG 验证码，只用 `SecureRandom` 拼字符串，**不依赖 AWT / ImageIO / 任何第三方库**。

> 为什么不用 easy-captcha / kaptcha：它们基于 `java.awt`，
> 在 GraalVM native image 下需要额外的 headless 与字体配置，容易踩坑。

配置项：

```properties
autumn.security.captcha.enabled=true      # 压测/联调可临时关闭
autumn.security.captcha.length=4
autumn.security.captcha.ttl-seconds=180
```

### 账号锁定

连续密码错误达到阈值即锁定，锁定期间**即便密码正确也拒绝登录**，成功登录后清零：

```properties
autumn.security.login.max-attempts=10
autumn.security.login.lock-minutes=30
```

- 数据落在 `user_login_security` 表（建表脚本：`schema-login-security.sql`），支持多实例部署
- 锁定判断统一使用数据库时间 `NOW()`，避免应用与数据库时区不一致导致误判
- 用户名不存在同样计数（缓解账号枚举）

### 错误码

| code | 场景 |
|---|---|
| `CAPTCHA_INVALID` | 验证码错误或已失效（前端应自动刷新验证码） |
| `BAD_CREDENTIALS` | 用户名或密码错误，附带 `remainingAttempts` |
| `ACCOUNT_LOCKED` | 账号已锁定，附带剩余时间提示 |
| `ACCOUNT_DISABLED` | 账号被禁用 |

---

这个示例是最小可运行版本，生产环境需要做以下升级，完全符合业界规范：





```azure
http://localhost:9000/oauth2/authorize?response_type=code&client_id=web-client&redirect_uri=http%3A%2F%2F127.0.0.1%3A8080%2Fcallback&scope=openid%20profile&code_challenge=$CODE_CHALLENGE&code_challenge_method=S256
```







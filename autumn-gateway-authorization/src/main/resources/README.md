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

这个示例是最小可运行版本，生产环境需要做以下升级，完全符合业界规范：





```azure
http://localhost:9000/oauth2/authorize?response_type=code&client_id=web-client&redirect_uri=http%3A%2F%2F127.0.0.1%3A8080%2Fcallback&scope=openid%20profile&code_challenge=$CODE_CHALLENGE&code_challenge_method=S256
```







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
第一步：浏览器访问授权端点
```
http://localhost:9000/oauth2/authorize?response_type=code&client_id=web-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid profile business:read

```
会跳转到登录页，输入 `user / password` 登录，同意授权后会重定向到回调地址，地址栏 `code=` 后面的值就是授权码。

第二步：用授权码换取令牌
```
curl -X POST \
  http://localhost:9000/oauth2/token \
  -u web-client:secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=你的授权码&redirect_uri=http://127.0.0.1:8080/callback"
```
成功会返回 `access_token`、`refresh_token`、`id_token`（OIDC）。

### 4、验证 userinfo 端点（OIDC 职责）
```
curl -H "Authorization: Bearer 你的access_token" http://localhost:9000/userinfo
```
返回当前登录用户的基本信息。

这个示例是最小可运行版本，生产环境需要做以下升级，完全符合业界规范：










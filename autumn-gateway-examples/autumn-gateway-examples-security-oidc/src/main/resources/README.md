## 加载密钥
```
keytool -genkeypair -alias jwt -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 365

```

输入的指令如下：
```
输入密钥库口令: 123456
再次输入新口令: 123456
输入唯一判别名。提供单个点 (.) 以将子组件留空，或按 ENTER 以使用大括号中的默认值。
您的名字与姓氏是什么?
  [Unknown]:  Percy Zhao
您的组织单位名称是什么?
  [Unknown]:  Null
您的组织名称是什么?
  [Unknown]:  JD
您所在的城市或区域名称是什么?
  [Unknown]:  SJZ
您所在的省/市/自治区名称是什么?
  [Unknown]:  HB
该单位的双字母国家/地区代码是什么?
  [Unknown]:  CN
CN=Percy Zhao, OU=Null, O=JD, L=SJZ, ST=HB, C=CN是否正确?
  [否]:  Y

正在为以下对象生成 2048 位 RSA 密钥对和自签名证书 (SHA384withRSA)（有效期为 365 天）：
	CN=Percy Zhao, OU=Null, O=JD, L=SJZ, ST=HB, C=CN
```


## 测试
### 登录获取Token
```
curl -X POST http://localhost:8080/auth/login \
-H "Content-Type: application/json" \
-d '{"username":"admin","password":"123456"}'
```
响应返回:

```
{"token":"eyJhbGciOiJSUzI1NiJ9...","type":"Bearer"}
```


### 访问业务接口:

```
curl http://localhost:8080/orders/1 \
-H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9..."

```

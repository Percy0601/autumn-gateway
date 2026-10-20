# autumn-gateway-dashboard

网关控制台后端（Spring Boot 4.1 / Java 25 / Spring Security 7）。

本文件聚焦 **RBAC 权限体系** 的接入方式、设计决策与踩坑复盘。
构建与 native 相关问题见 `src/main/resources/README.md`。

---

## 目录

- [1. 快速开始](#1-快速开始)
- [2. 权限模型](#2-权限模型)
- [3. 七条授权链路](#3-七条授权链路)
- [4. ID 段规划](#4-id-段规划)
- [5. 三层鉴权的工作方式](#5-三层鉴权的工作方式)
- [6. 灰度开关](#6-灰度开关)
- [7. 前端契约](#7-前端契约)
- [8. 踩坑复盘（重要）](#8-踩坑复盘重要)
- [9. 新增一个功能页的完整步骤](#9-新增一个功能页的完整步骤)
- [10. 安全加固清单](#10-安全加固清单)
- [11. 自检与排查](#11-自检与排查)

---

## 1. 快速开始

### 1.1 依赖顺序（不可颠倒）

SQL 脚本在 `src/main/resources/db/rbac/`：

| 顺序 | 文件 | 作用 | 幂等 |
|---|---|---|---|
| 1 | `schema-basic-mysql.sql` / `schema-basic-postgresql.sql` | 建 `basic_*` 基础表（项目既有） | ✅ `CREATE TABLE IF NOT EXISTS` |
| 2 | `V1__rbac_schema_mysql.sql` / `V1__rbac_schema_postgresql.sql` | 补唯一键与索引（**不建表、不改业务字段**） | ✅ 存储过程探测 `information_schema` |
| 3 | `V2__rbac_init_data_mysql.sql` / `V2__rbac_init_data_postgresql.sql` | 初始化菜单 / 资源 / 角色 / 账号 | ✅ `ON DUPLICATE KEY UPDATE` / `ON CONFLICT DO UPDATE` |

> **为什么 V1 不建表**：`basic_*` 表由既有 `schema-basic-*.sql` 负责。V1 只做「收紧约束 + 补索引」这一件事，因此可以安全地叠加在**存量库**上——这是能直接分发给已有环境的团队复用的前提。

MySQL 8 不支持 `CREATE INDEX IF NOT EXISTS`，重复执行会抛 1061/1060。V1 用临时存储过程 `sp_rbac_add_index` 包装，靠 `information_schema.STATISTICS` 探测后再决定是否 ALTER，执行完自动 `DROP PROCEDURE`，不留痕迹。

PostgreSQL 原生支持 `CREATE INDEX IF NOT EXISTS`，直接用即可。唯一约束统一用**唯一索引**实现——`ADD CONSTRAINT` 不支持 `IF NOT EXISTS`，而唯一索引效果等价（同样阻止重复值，且 `ON CONFLICT` 能识别它）。两库的约束名刻意保持一致（`uk_role_permission` 等），便于跨库对比。

### 1.2 执行顺序

```bash
# MySQL
mysql -h <host> -u <user> -p <db> < src/main/resources/schema-basic-mysql.sql
mysql -h <host> -u <user> -p <db> < src/main/resources/db/rbac/V1__rbac_schema_mysql.sql
mysql -h <host> -u <user> -p <db> < src/main/resources/db/rbac/V2__rbac_init_data_mysql.sql

# PostgreSQL
psql -h <host> -U <user> -d <db> -f src/main/resources/schema-basic-postgresql.sql
psql -h <host> -U <user> -d <db> -f src/main/resources/db/rbac/V1__rbac_schema_postgresql.sql
psql -h <host> -U <user> -d <db> -f src/main/resources/db/rbac/V2__rbac_init_data_postgresql.sql
```

`V2` 末尾自带 4 条只读自检 SQL（角色权限计数、孤儿菜单、无父按钮、用户有效角色清单），执行时会直接打印结果，肉眼核对即可。

### 1.3 初始账号

| 账号 | 密码 | 角色 | 用途 |
|---|---|---|---|
| `admin` | `Autumn#Admin@2026` | SUPER_ADMIN（`admin=1`） | 超管，全权限直通 |
| `auditor` | `Autumn#User@2026` | AUDITOR | **验证「非管理员视角」的专用账号** |

> ⚠️ 这两个密码只用于交付初始化。**首次登录后必须立即修改**，并把 V2 脚本里的 BCrypt 哈希替换为团队内部值。
>
> 注意 `auditor` 的密码是 `Autumn#User@2026`（`#` 不是 `@`），和 `admin` 的 `Autumn#Admin@2026` 保持同一分隔符。
>
> **改哈希后请务必自验一次**：BCrypt 的坑在于「哈希和明文对不上」不会报错，只会让人登录失败，而失败信息又和「密码输错」完全一样，很容易被误判成配置问题。生成后跑一遍：
>
> ```java
> new BCryptPasswordEncoder(10).matches("明文", "哈希");   // 必须为 true
> ```

V2 的 `basic_user` 段 `ON DUPLICATE KEY UPDATE` **故意不含 `password` 列**——否则每次重启都会把用户改过的密码重置回初始值。要重置密码请手工执行：

```sql
UPDATE `basic_user` SET `password` = '<新 BCrypt 哈希>' WHERE `username` = 'admin';
```

### 1.4 启动

```bash
# 默认灰度关闭，行为与改造前完全一致
./mvnw -pl autumn-gateway-dashboard -am spring-boot:run

# 启用鉴权
./mvnw -pl autumn-gateway-dashboard -am spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dbasic.rbac.enabled=true"
```

启动日志出现下面这行，说明数据已就位：

```
RBAC 就绪自检通过：启用菜单 52 条
```

如果打印的是告警 `basic_menu 中没有启用状态的菜单`，说明 V2 脚本没跑或跑到了别的库。

### 1.5 冒烟验证

```bash
BASE=http://127.0.0.1:8080

# 1) 匿名访问受保护接口 → 401 JSON（不是 HTML 登录页）
curl -i $BASE/api/system/basic/user

# 2) 登录拿 token
TOKEN=$(curl -s -X POST $BASE/api/login/account \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Autumn#Admin@2026"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 3) 带 token 访问 → 200
curl -i -H "Authorization: Bearer $TOKEN" $BASE/api/system/basic/user

# 4) 当前用户 → authorityList 应为 ["*"]（超管）
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/currentUser

# 5) 用 auditor 验证受限视角：列表接口 403、菜单只剩只读项
ATOKEN=$(curl -s -X POST $BASE/api/login/account \
  -H 'Content-Type: application/json' \
  -d '{"username":"auditor","password":"Autumn#User@2026"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -i -H "Authorization: Bearer $ATOKEN" $BASE/api/system/basic/user   # 期望 403
```

执行第 3 步若得到 **401**，直接跳到 [第 8 节](#8-踩坑复盘重要)——这是本项目踩得最深的一个坑。

---

## 2. 权限模型

### 2.1 三层结构

```
┌─────────────────────────────────────────────────────────┐
│  前端可见性            basic_menu                        │
│  （目录 / 菜单 / 按钮）  menu_type = 1 / 2 / 3            │
│                        menu_identification = 权限标识    │
└─────────────────────────────────────────────────────────┘
                            │ basic_menu_resource
                            ▼
┌─────────────────────────────────────────────────────────┐
│  后端可调用性          basic_resource                     │
│  （URL 原子权限）      path + http_method + match_type   │
└─────────────────────────────────────────────────────────┘
                            ▲
                            │ basic_role_permission (type=1/2)
┌─────────────────────────────────────────────────────────┐
│  授权关系              basic_role                        │
└─────────────────────────────────────────────────────────┘
```

- **`basic_menu`** 决定「前端看到什么」。`menu_type=3` 的按钮不渲染，只作为权限点供 `v-auth` 与 `@PreAuthorize` 使用。
- **`basic_resource`** 决定「后端接口能不能调」。`match_type` 支持 `exact` / `prefix` / `suffix`；`http_method` 支持 `ALL` 与逗号分隔多值。
- **`basic_menu_resource`** 把两者缝起来：给角色授了某个菜单，该菜单挂的资源在 URL 层同时放行。这就是需求里「菜单关联后台资源」的落地方式。

### 2.2 权限标识命名规范

```
<模块>:<子模块>:<动作>
动作 ∈ view / create / update / delete / enable / disable / assign / export
例：system:user:create、system:role:assign-menu、system:log:export
```

前后端**共用同一个字符串**：后端写 `@PreAuthorize("hasAuthority('system:user:create')")`，前端写 `v-auth="'system:user:create'"`。V2 脚本里的按钮权限点注释直接给了可复制的写法。

### 2.3 内置角色

| id | code | master | 授权策略 |
|---|---|---|---|
| 1 | `SUPER_ADMIN` | 1 | **不写 `basic_role_permission`**，代码层 `admin=1 or master=1` 短路放行 |
| 2 | `SYS_ADMIN` | 2 | 全部菜单与按钮，**不含**菜单/资源管理、不含用户删除与重置密码 |
| 3 | `SECURITY_ADMIN` | 2 | 菜单/资源/角色全功能 + 用户与用户组只读（与 SYS_ADMIN 职责分离） |
| 4 | `AUDITOR` | 2 | 只有 `*:view` 菜单 + 日志导出，**零写按钮** |
| 5 | `VIEWER` | 2 | 纯查看 |

> **超管为什么不写权限记录**：新加菜单或接口时，不会因为「忘了给 admin 授权」把自己锁在系统外面。`master=1` 是运维安全阀，不是后门——它对应的账号本身就受 `basic_user.status` 与密码策略保护。

### 2.4 用户组与继承

```
basic_user ─┬─ basic_user_role ────────────────┐
            │   （直接授角色）                  │
            └─ basic_user_group                ├─→ 角色集合
                   ├─ basic_role_group ────────┘
                   └─ basic_group_group
                         （上级组角色向下继承）
```

`WITH RECURSIVE` 一次算完组继承链，MySQL 8 与 PostgreSQL 语法一致，无需分库写两套。继承受 `basic.rbac.group-inherit-enabled` 控制（默认 `true`）。

V2 默认建了「运维组 ← 安全组 / 审计组」的层级关系用于演示。**如果不需要继承语义，把 `basic.rbac.group-inherit-enabled` 置为 `false`，或不要在 `basic_group_group` 建关系**——否则运维组的人会自动获得安全组和审计组的角色，这在严格隔离场景下是过度授权。

---

## 3. 七条授权链路

需求里点名的 7 条关系，全部在库里配置、无需改代码：

| # | 链路 | 表 | 接口 | 按钮权限点 |
|---|---|---|---|---|
| 1 | 用户管理 | `basic_user` | `/api/system/basic/user` | `system:user:*` |
| 2 | 用户授权角色 | `basic_user_role` | `PUT .../user/{id}/roles` | `system:user:assign-role` |
| 3 | 用户授权用户组 | `basic_user_group` | `PUT .../user/{id}/groups` | `system:user:assign-group` |
| 4 | 用户组和角色 | `basic_role_group` | `PUT .../group/{id}/roles` | `system:group:assign-role` |
| 5 | 角色管理用户 | `basic_user_role`（反向） | `PUT .../role/{id}/users` | `system:role:assign-user` |
| 6 | 角色管理菜单 | `basic_role_permission`（type=1） | `PUT .../role/{id}/menus` | `system:role:assign-menu` |
| 7 | 菜单关联后台资源 | `basic_menu_resource` | `PUT .../menu/{id}/resources` | `system:menu:assign-resource` |

每条链路的写方法都标了 `@GrantChanged`，方法正常返回后自动清空权限缓存（见 5.3）。

---

## 4. ID 段规划

硬编码主键，便于跨库对比与人工排查。每段留 100 步长，后续新增不用改动已有脚本。

| 表 | ID 段 | 细分 |
|---|---|---|
| `basic_namespace` | 1 | 固定 |
| `basic_app` | 1 | 固定 |
| `basic_menu` | 1000 ~ 1499 | 1100 目录 / 1200 菜单 / 1300 按钮（当前已用至 1397，新增页从 1400 起） |
| `basic_resource` | 2000 ~ 2499 | 2000 认证 / 2010 用户 / 2020 角色 / 2030 组 / 2040 菜单 / 2050 资源 / 2060 应用 / 2070 命名空间 / 2080 日志（当前已用至 2080） |
| `basic_role` | 1 ~ 9 | 1 超管 2 系统管理员 3 安全管理员 4 审计员 5 访客 |
| `basic_user` | 1 ~ 9 | 1 admin 2 auditor |
| `basic_group` | 1 ~ 9 | 1 运维 2 安全 3 审计 |
| `basic_role_permission` / `basic_user_role` / `basic_menu_resource` | 自增 | 靠唯一键幂等，不依赖 ID |

**当前规模**：52 菜单 / 36 资源 / 47 菜单-资源关联 / 5 角色 / 2 账号。

菜单树结构：

```
1101 首页（menu_type=2，直接是页面）
1102 系统管理（目录）
  ├ 1201 用户管理        → 1301~1308 按钮（新增/编辑/删除/启停/分配角色/分配用户组/重置密码）
  ├ 1202 角色管理        → 1321~1328 按钮（新增/编辑/删除/启停/分配用户/分配菜单/分配资源/分配用户组）
  ├ 1203 用户组管理      → 1341~1348 按钮（新增/编辑/删除/启停/分配用户/分配角色/维护层级）
  ├ 1204 菜单管理        → 1361~1365 按钮（新增/编辑/删除/启停/关联资源）
  ├ 1205 资源管理        → 1381~1385 按钮（新增/编辑/删除/启停/关联菜单）
  ├ 1206 应用管理        → 1391~1393 按钮
  ├ 1207 命名空间管理    → 1394~1396 按钮
  └ 1208 操作日志        → 1397 导出按钮
1103 网关运维（目录，预留，暂未挂页面）
```

菜单 ID 段与按钮 ID 段一一对应：`12NN` 页面对应 `13NN` 段按钮（`1201` → `1301`，`1202` → `1321`）。加页面时沿这个规律取号，比查表快。

> **分段已接近上半区上限**：`13xx` 段用到 `1397`（1208 日志页的导出按钮）。新增页面请从 **`1209` 起编页、`1400` 起编按钮**，即按钮段整体上移到 `14NN`，不再与 `13NN` 严格对齐。这是有意的破例——`13NN` 已被 `*:assign-*` 类按钮占满，硬塞会打乱可读性。

---

## 5. 三层鉴权的工作方式

### 5.1 认证：`JwtAuthenticationFilter`

```java
Claims claims = jwtUtil.parseToken(token);          // 验签 + 过期，失败抛 JwtException
Long userId = Long.parseLong(claims.getSubject());
RequestContext.set(RequestContext.of(userId, username, token));   // 审计字段用
UserGrant grant = permissionService.load(userId);   // 命中缓存则零 SQL
LoginUser principal = new LoginUser(userId, username, token, grant);
// ...写入 SecurityContext（见 8.1）
```

**token 非法按匿名处理、不抛异常**。是否拦截由 `authorizeHttpRequests` 决定，过滤器只负责「尽力识别身份」。这样 `/api/login/**` 这类匿名路径即使带了脏 token 也不会 500。

`LoginUser#toString()` 刻意不含 token，避免日志泄露凭据。

### 5.2 功能授权：`UrlAuthorizationManager`

五步判定，**第 5 步是拒绝**：

1. 未认证 → `DENY`（交给 `AuthenticationEntryPoint` 返回 401）
2. 超管 → `GRANT`
3. 路径命中 `ALWAYS_PERMIT` → `GRANT`（双保险）
4. `grant.urlResources()` 中有任意一条 `matches(path, method)` → `GRANT`
5. 否则 → `DENY`（403）

> **默认拒绝是刻意设计**。新加接口忘了配 `basic_resource`，结果是「403 报错」而不是「悄悄对外开放」——前者会被人发现并修好，后者可能几个月都没人注意到。

日志分流：匿名被拒是正常流程，静默处理；**非匿名却拿不到 `LoginUser`** 才 `log.warn`，因为这说明有别的 `AuthenticationProvider` 混进了过滤器链，属于真异常。

`ALWAYS_PERMIT` 与 `SecurityConfig` 的 `permitAll` 必须同步维护——一处在过滤器链层放行，一处在授权管理器层放行，漏改任何一处都会出现「看似放行实则 403」的诡异现象。

### 5.3 按钮授权：`@PreAuthorize` 与 `Permissions`

```java
// 声明式（推荐）
@PreAuthorize("hasAuthority('system:user:create')")
public Result<Long> create(@RequestBody BasicUser user) { ... }

// 编程式（条件依赖方法参数时用）
if (!Permissions.has("system:user:update") && !isSelf(id)) {
    throw new AccessDeniedException("无权修改他人信息");
}
```

`Permissions` 提供 `has` / `hasAny` / `hasAll` / `isSuperAdmin` / `currentUserId` / `currentAuthorities`。统一入口比到处 `SecurityContextHolder.getContext()...` 更可控。

### 5.4 缓存与失效

`PermissionService` 用 `ConcurrentHashMap` + 短 TTL（`basic.rbac.cache-ttl-seconds`，默认 60 秒）。没上 Caffeine 是因为只需要「整键失效 + TTL」，JDK 自带结构足够，少一层配置依赖。

**授权变更后必须让缓存立刻失效**，否则运维在页面加了角色要等 1 分钟才生效，会让人误以为保存失败而反复点击。机制是 `@GrantChanged`：

```java
@GrantChanged
public void setRoles(Long userId, List<Long> roleIds) { ... }
```

用 `AnnotationMatchingPointcut` + `DefaultPointcutAdvisor` 实现，**零 AspectJ 依赖**（`spring-boot-starter-aspectj` 在 Boot 4 已改名，为一个清 Map 的动作引入字节码织入不划算）。只对标注了注解的方法生效，不会误伤 `setTitle` 这类普通实体 setter——这点比按方法名通配的切面安全得多。

只有**正常返回**才清缓存：抛异常说明事务回滚了，缓存本来就不该动。

---

## 6. 灰度开关

`basic.rbac.enabled`，**默认 `false`**。

| 值 | 行为 |
|---|---|
| `false` | 所有接口 `permitAll`。代码全部就位但不生效，可先合入主干部署验证不影响线上 |
| `true` | `/api/**` 走 `UrlAuthorizationManager`，其余 `authenticated()` |

切换方式（无需改代码）：

```bash
java -jar app.jar --basic.rbac.enabled=true
# 或 application.properties
basic.rbac.enabled=true
```

**为什么默认关**：项目接近尾声，直接全量收紧会让所有既有调用方（前端、网关、脚本）立刻 401/403。先关着合入、跑通初始化 SQL、用 `auditor` 账号验证，确认权限配置无误后再打开。出问题改一个配置项即可回滚，不用回滚代码。

启动时开关状态会打日志；为 `false` 时额外输出一条 `WARN`，避免上线时忘了打开。

相关配置项汇总：

```properties
basic.rbac.enabled=false                 # 总开关
basic.rbac.group-inherit-enabled=true    # 上级组角色向下继承
basic.rbac.app-code=dashboard            # 资源过滤用的 app_code
basic.rbac.cache-ttl-seconds=60          # 权限缓存 TTL
```

---

## 7. 前端契约

### 7.1 菜单路由

`GET /api/system/basic/menu/list?appId=1` 返回当前用户可见的菜单树，前端 `routerStore.getMenuRoutes()` 直接用它建路由表，**无需改动前端**即可实现目录级隔离。

返回规则：

- 超管 / `basic.rbac.enabled=false` → 全量（保持既有行为）
- 普通用户 → 只返回被授权的菜单，**并自动补齐祖先目录**

补齐祖先是关键：子菜单有权限但父目录没独立权限点时，前端建不出树，表现为「登录后菜单空白」。实现见 `BasicMenuController#listByApp`。

`menu_url` 即前端路由，如 `system/user/index`（`menu_type=3` 时为空）。

### 7.2 按钮显隐

`GET /api/currentUser` 下发：

```json
{
  "success": true,
  "data": {
    "name": "超级管理员",
    "userid": "1",
    "access": "admin",
    "authorityList": ["*"],
    "superAdmin": true
  }
}
```

- `authorityList`：按钮级权限标识清单，前端 `v-auth="'system:user:create'"` 据此显隐
- 超管下发 `["*"]`，前端按通配处理
- `access` 保留原 `admin`/`user` 语义，`access.ts` 仍在用

### 7.3 错误响应

401 / 403 统一返回 JSON 而非 HTML 错误页，前端 axios 拦截器按 HTTP 状态码分支，避免 `JSON.parse` 失败：

```json
{"code":401,"message":"未登录或登录已失效，请重新登录","data":null}
{"code":403,"message":"无权限访问该资源","data":null}
```

JSON 是手写的，没用 `ObjectMapper`——Boot 4 把 Jackson 3（`tools.jackson.*`）作为首选，注入 `ObjectMapper` 的包名会随版本变化，这里只有两个固定字段，手写可以彻底躲开这个坑。

---

## 8. 踩坑复盘（重要）

这一节是本文档的核心。下面每个坑都真实发生过，且症状高度相似（**都表现为「登录成功但所有接口 401」**），排查时极易走错方向。

### 8.1 【根因】deferred 模式下 `saveContext()` 不会自动调用

**症状**

```
POST /api/login/account  →  200，返回正常 token
GET  /api/system/basic/user  (带 Bearer token)  →  401
```

登录完全正常，token 也验签通过，但任何受保护接口都是 401。加探针在 `SecurityContextHolderFilter` 之后读 holder：

```
[D] uri=/api/system/basic/user ctxDone=true holder=SecurityContextImpl [Null authentication]
```

`ctxDone=true` 说明过滤器已经跑过，但 holder 里是**空的**——而 JWT 过滤器里明明已经写入了认证态。

**根因**

Spring Security 6+ 的 `SecurityContextHolderFilter` 默认 `requireExplicitSave=true`（deferred 模式）。这个模式下：

- 它只负责**读**：`repository.loadDeferredContext(request)` 塞进 holder；
- 它**不会主动调用** `repository.saveContext(...)` ——这是设计变更，为了性能和显式性。

于是：

1. `SecurityContextHolderFilter` 先跑，把 request 里那个**空的初始 context** 装进 holder；
2. JWT 过滤器后跑，`SecurityContextHolder.setContext(newContext)` 只改了 holder 指向；
3. 下一个过滤器若通过 `repository` 重新取（而非读 holder），拿到的还是 request attribute 里那个空的；
4. 最终 `AuthorizationFilter` 看到的认证态是空的 → 401。

**修复**

认证方自己持久化。`JwtAuthenticationFilter`：

```java
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);

// 显式落盘：deferred 模式（SecurityContextHolderFilter）不会自动调用
// SecurityContextRepository#saveContext，必须由认证方自己保存，
// 否则 request attribute 里始终是那个空的初始 context。
request.setAttribute(RequestAttributeSecurityContextRepository.ATTR_NAME, context);
```

**排查过程中被误判的四个方向**（记下来避免重走）：

| 误判 | 排除方式 |
|---|---|
| `finally` 里 `clearContext()` 把认证态清了 | 移除后仍 401。`filterChain.doFilter()` 内部会依次跑后续 Security 过滤器，它们在 `filterChain` 返回**之前**就要读认证态，此时还没到 `finally` |
| `getContext().setAuthentication()` 用法不对 | 改 `setContext()` 后仍 401 |
| 缺少自定义 `SecurityContextRepository` | 加上后仍 401（因为缺的是 save 那一步） |
| `.securityContext(...)` 位置放错 | 调到 `authorizeHttpRequests` 之前，仍 401 |

四个方向都对，但都不充分——真正的缺口是**「写 holder」与「写 repository」是两件事**。

### 8.2 自定义 repository 时不要在 `deferred.get()` 里回头读 holder

**症状**：`StackOverflowError`，栈里是 `get` ↔ `supplier` 的无限循环。

**根因**：`SecurityContextHolderFilter` 会把 `loadDeferredContext` 的返回值放进 holder 当 Supplier。若在 `deferred.get()` 里调 `SecurityContextHolder.getContext()`，就形成 `get → supplier → get` 的递归。

**修复**：`get()` 直接返回构造时捕获的实例。

```java
private record FixedDeferredSecurityContext(SecurityContext context)
        implements DeferredSecurityContext {
    @Override public SecurityContext get() { return this.context; }
    @Override public boolean isGenerated() { return true; }
}
```

这个坑还有个隐蔽变体：为了让 context「每次取都是最新」，很容易想在 `get()` 里做一次 holder 查询——**绝对不能**。deferred 对象必须是自包含的。

### 8.3 `NullSecurityContextRepository` 每次都给新的空 context

不显式注册仓库时，Spring Security 退化成 `NullSecurityContextRepository`。它的 `loadDeferredContext` 走接口默认实现：每次求值都 `new HttpRequestResponseHolder` 再 `loadContext()`，**拿到的是全新的空 context**。

holder 里装的就是这个「每次求值都不同」的 Supplier，于是 JWT 过滤器写入的认证态在下游读取时被新生成的空 context 顶掉。

所以 `RequestAttributeSecurityContextRepository` 的 `load()` 必须做到「首次访问即创建，同一请求内反复取到同一实例」：

```java
private SecurityContext load(HttpServletRequest request) {
    if (request.getAttribute(ATTR) instanceof SecurityContext existing) {
        return existing;
    }
    SecurityContext ctx = holderStrategy.createEmptyContext();
    request.setAttribute(ATTR, ctx);
    return ctx;
}
```

生命周期绑定 request，请求结束随请求销毁，不跨请求保留，符合无状态要求。

### 8.4 `.securityContext(...)` 必须写在 `authorizeHttpRequests(...)` 之前

`SecurityContextConfigurer#securityContextRepository(...)` 内部是 `setSharedObject`。而 `AuthorizeHttpRequestsConfigurer` 在 **init 阶段**就会读取该 sharedObject 来构建 `AuthorizationFilter`。

顺序反了 → 读不到 → 退回默认仓库 → 症状与「完全不配置」一模一样。

正确顺序（`SecurityConfig`）：

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.securityContext(sc -> sc.securityContextRepository(
        new RequestAttributeSecurityContextRepository()))   // ← 先
.authorizeHttpRequests(auth -> { ... })                     // ← 后
.exceptionHandling(...)
```

**Lambda DSL 的隐藏陷阱**：这些调用是链式的，看起来「都是配置」，实际上有 init 顺序依赖。Spring Security 6/7 的 Lambda DSL 移除了不少编译期提示，顺序错了不会报错，只会静默失效。

### 8.5 `AuthorizationManager` 在 7.0 改名为 `authorize`

6.x 的 `check` 在 7.0 已被 `authorize` 取代，返回 `AuthorizationResult`（`AuthorizationDecision` 是其实现）。网上大量 6.x 教程写的是 `check`，直接抄会编译失败。

```java
@Override
public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                     RequestAuthorizationContext context) { ... }
```

### 8.6 `record` 的两个编译陷阱

```java
// ✗ 紧凑构造器里不能给 final 字段赋值（只能在尾部 this.x = ...）
public record UserGrant(boolean superAdmin, ...) {
    public UserGrant { this.superAdmin = superAdmin; }   // 编译错误
}

// ✗ 静态工厂名与访问器签名冲突
public static UserGrant superAdmin() { ... }   // 与 record 自动生成的 superAdmin() 冲突
```

修复：显式写完整构造器（做防御性拷贝 + 不可变包装），静态工厂改名 `fullAccess()`。

### 8.7 切点 `*.set*` 会误伤实体 setter

最初想用按方法名通配的切面实现「授权变更清缓存」，结果 `setTitle`、`setRemark` 这类普通 setter 也触发了全局缓存清空——缓存形同虚设。

修复：改用注解驱动 `@GrantChanged`，精确标注 14 个授权写方法。顺带的好处是**全局搜索注解就能列出所有授权变更入口**，评审时一眼看完。

### 8.8 其他小坑

| 坑 | 表现 | 处理 |
|---|---|---|
| `spring-boot-starter-aspectj` 离线不可用 | 依赖拉不下来 | 改用 `AnnotationMatchingPointcut` |
| Boot 4 的 Jackson 3 | 注入 `ObjectMapper` 包名漂移 | 手写固定结构 JSON |
| Jansi 缺失告警 | `Failed to create AnsiPrintStream` | 无害，日志颜色降级 |
| Boot 自动生成 security password 的 WARN | 启动日志噪音 | 已在 `SecurityConfig` 中禁用 formLogin/httpBasic |
| `mvn test` 离线失败 | `surefire-junit-platform:3.5.6` 拉不到 | 改用直接启动 Spring 上下文验证 |
| JDK 25 反射读 `ThreadLocal` 内部字段 | `InaccessibleObjectException` | 放弃该诊断路径，改用探针读 holder |
| 日志里中文乱码 | `grep` 匹配不到 | 用 `grep -a` 按字节处理 |
| BCrypt 哈希与注释明文不匹配 | 登录失败，报错与「密码输错」一模一样 | 生成后用 `matches()` 自验（见 1.3） |

### 8.9 排查这类问题的方法论

1. **先确认「探针位置」**。在 `SecurityContextHolderFilter` 之后插一个探针读 holder，能一次性区分「没写进去」和「写进去了但下游看不见」。
2. **每次改动都要重新编译**。本项目多次出现「改了但日志没变」，原因是 `mvn compile` 没跑到——`spring-boot:run` 前的 `-am` 不能省。
3. **不要靠猜，要看 holder 的实际内容**。`holder=SecurityContextImpl [Null authentication]` 这一行直接终结了前面四轮误判。
4. **记住 deferred 两条铁律**：写 holder ≠ 写 repository；deferred 对象必须自包含。

---

## 9. 新增一个功能页的完整步骤

以「新增一个『证书管理』页面」为例：

**1) 数据库：菜单 + 按钮**

```sql
-- 页面
INSERT INTO basic_menu (id, app_id, parent_id, menu_type, menu_name, menu_url, menu_identification, menu_icon, sort_num, menu_status, del_flag, create_time, update_time)
VALUES (1209, 1, 1102, 2, '证书管理', 'system/cert/index', 'system:cert:view', 'certificate', 90, 1, 0, NOW(), NOW());

-- 按钮（1209 页面对应 1400 段，见第 4 节说明）
INSERT INTO basic_menu (...) VALUES
 (1401, 1, 1209, 3, '新增证书', '', 'system:cert:create', '', 10, 1, 0, NOW(), NOW()),
 (1402, 1, 1209, 3, '删除证书', '', 'system:cert:delete', '', 20, 1, 0, NOW(), NOW());
```

**2) 数据库：URL 资源**

```sql
INSERT INTO basic_resource (id, app_id, app_code, name, path, http_method, match_type, status, created_at, updated_at)
VALUES
 (2090, 1, 'dashboard', '证书列表', '/api/system/basic/cert', 'GET',    'prefix', 1, NOW(), NOW()),
 (2091, 1, 'dashboard', '新增证书', '/api/system/basic/cert', 'POST',   'exact',  1, NOW(), NOW()),
 (2092, 1, 'dashboard', '删除证书', '/api/system/basic/cert', 'DELETE', 'prefix', 1, NOW(), NOW());
```

**3) 数据库：菜单 ↔ 资源关联**

```sql
INSERT INTO basic_menu_resource (menu_id, resource_id, deleted, create_time, update_time)
VALUES (1209, 2090, 0, NOW(), NOW()),
       (1401, 2091, 0, NOW(), NOW()),
       (1402, 2092, 0, NOW(), NOW());
```

不做这一步的后果：菜单授了，前端能看见按钮，但点下去 **403**——因为 URL 层没放行。

**4) 给角色授权**

在「角色管理 → 分配菜单」里勾上，或直接写 `basic_role_permission`（`type=1` 授菜单，`type=2` 授资源）。

**5) 后端加注解**

```java
@PreAuthorize("hasAuthority('system:cert:create')")
@PostMapping
public Result<Long> create(@RequestBody BasicCert cert) { ... }
```

写方法上加 `@GrantChanged`。

**6) 前端加指令**

```vue
<a-button v-auth="'system:cert:create'">新增</a-button>
```

**7) 自检**

```sql
-- 孤儿菜单应为空
SELECT m.id, m.menu_name FROM basic_menu m
 LEFT JOIN basic_menu p ON p.id = m.parent_id
 WHERE m.parent_id <> 0 AND p.id IS NULL;
```

重启后看到 `RBAC 就绪自检通过：启用菜单 54 条`（52 + 2）说明菜单已入库。若数字没变，检查 SQL 是不是跑到了别的库。

---

## 10. 安全加固清单

以下是接入过程中发现、**尚未全部处理**的问题，按优先级排列。

### 10.1 高：`jwt.secret` 硬编码明文

```properties
jwt.secret=autumn-gateway-secret-key-must-be-at-least-256-bits-long
```

提交在 `application.properties` 里，任何拿到代码的人都能签发合法 token。

**建议**：

```properties
jwt.secret=${JWT_SECRET}
```

通过环境变量或配置中心注入；不同环境用不同密钥；轮换时保留旧密钥一段时间的验证能力（双密钥过渡）。

### 10.2 高：token 支持从 query 参数读取

```java
private String resolveToken(HttpServletRequest request) {
    // ... Authorization 头优先
    String param = request.getParameter("token");   // ← 回退
    return (param == null || param.isBlank()) ? null : param.trim();
}
```

token 在 URL 里会被 **Nginx access_log、浏览器历史、Referer 头、代理缓存**记录，属于凭据泄露的常见来源。

保留此回退仅为兼容既有网关透传方式。**建议后续只接受 `Authorization` 头**，并给一个过渡期：先记 WARN 日志统计调用量，确认无调用方后再摘掉。

### 10.3 中：`NamespaceDataInitializer` 默认密码

`Autumn@123456` 硬编码在初始化器里。与 V2 脚本的初始密码一样，都属于「交付便利 vs 生产安全」的权衡。

**建议**：首次启动时若检测到仍是默认密码，强制跳转改密页；或在生产 profile 下拒绝启动并提示设置 `INIT_ADMIN_PASSWORD`。

### 10.4 中：Spring Boot 自动生成 security password 的 WARN

启用 `spring-boot-starter-security` 后，若没有自定义 `UserDetailsService`，Boot 会生成随机密码并打 WARN。本项目走 JWT 无状态认证，这个默认用户完全无用，属于日志噪音，也容易让人误以为存在隐藏后门。

**建议**：`SecurityConfig` 已禁用 `formLogin` / `httpBasic`，可再排除 `UserDetailsServiceAutoConfiguration`：

```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
```

### 10.5 中：菜单列表接口的越权风险（已修复）

原 `GET /api/system/basic/menu/list` 把全部菜单返回给任何已登录用户，等于把完整的权限结构（含所有按钮标识）暴露给低权限账号。

已改为按权限过滤 + 补齐祖先目录（见 7.1）。**这是「不同角色看到不同目录」的核心落地接口**，改动前务必确认前端 `routerStore` 依赖的字段未变。

### 10.6 低：`PermissionService` 缓存无容量上限

`ConcurrentHashMap` 只做了 TTL，没做 LRU 上限。用户量上万时内存会持续增长。

**建议**：用户数预期较大的部署，换成 Caffeine 的 `maximumSize` + `expireAfterWrite`。改动集中在 `PermissionService` 一个类，接口不变。

### 10.7 低：`LoginUser#isEnabled()` 恒返回 true

用户被停用（`status=2`）后，已签发的 token 在过期前仍然可用。这是 JWT 无状态的固有代价。

**建议**：如果停用需要立即生效，在 `PermissionService#load` 里顺带校验 `basic_user.status`（已有一次查询，白拿），停用则返回 `UserGrant.empty()`。或者维护一个短 TTL 的「已吊销用户」集合。

### 10.8 已做的加固

- ✅ 401/403 返回 JSON，不用 HTML 错误页（避免信息泄露与前端解析失败）
- ✅ CSRF 关闭（无 Cookie 会话，无攻击面；这是**正确**选择而非偷懒）
- ✅ `SessionCreationPolicy.STATELESS`（不创建 Session，水平扩容无需粘性会话）
- ✅ URL 鉴权**默认拒绝**（漏配资源 = 403 报错，不是静默放行）
- ✅ `LoginUser#toString()` 不含 token
- ✅ `LoginUser#getPassword()` 返回空串，不返回真实哈希
- ✅ 匿名被拒静默，非匿名异常才告警（避免日志被刷屏淹没真问题）
- ✅ 权限缓存授权变更后主动失效（避免「改了不生效」导致反复点击）

---

## 11. 自检与排查

### 11.1 启动自检

`PermissionCacheInvalidator#onReady` 在 `ApplicationReadyEvent` 时统计启用菜单数：

```
RBAC 就绪自检通过：启用菜单 52 条         ← 正常
basic_menu 中没有启用状态的菜单，RBAC 初始化脚本可能未执行   ← 检查 V2 是否执行
```

这比让用户登录后看到满屏 403 再去逐层排查要友好得多。

### 11.2 常用排查 SQL

```sql
-- 某用户的最终权限标识（走完 用户→角色 链路）
SELECT DISTINCT m.menu_identification
  FROM basic_user_role ur
  JOIN basic_role_permission p ON p.role_id = ur.role_id AND p.type = 1 AND p.del_flag = 0
  JOIN basic_menu m ON m.id = p.ref_id AND m.del_flag = 0 AND m.menu_status = 1
 WHERE ur.user_id = ? AND ur.del_flag = 0;

-- 某用户可访问的 URL 资源（type=2 直接授 + type=1 菜单带出）
SELECT DISTINCT r.id, r.path, r.http_method, r.match_type
  FROM basic_resource r
 WHERE r.status = 1 AND r.app_code = 'dashboard'
   AND ( r.id IN (SELECT p.ref_id FROM basic_role_permission p
                   JOIN basic_user_role ur ON ur.role_id = p.role_id AND ur.del_flag = 0
                  WHERE ur.user_id = ? AND p.type = 2 AND p.del_flag = 0)
      OR r.id IN (SELECT mr.resource_id FROM basic_menu_resource mr
                   JOIN basic_role_permission p ON p.ref_id = mr.menu_id AND p.type = 1 AND p.del_flag = 0
                   JOIN basic_user_role ur ON ur.role_id = p.role_id AND ur.del_flag = 0
                  WHERE ur.user_id = ? AND mr.deleted = 0) );

-- 某个 URL 有没有被任何资源配置覆盖（漏配会导致 403）
SELECT id, path, http_method, match_type FROM basic_resource
 WHERE status = 1 AND path LIKE '/api/system/basic/cert%';

-- 每个角色实际拿到的权限点数量
SELECT r.code, r.master,
       SUM(p.type = 1) AS menu_cnt, SUM(p.type = 2) AS resource_cnt
  FROM basic_role r
  LEFT JOIN basic_role_permission p ON p.role_id = r.id AND p.del_flag = 0
 WHERE r.del_flag = 0 GROUP BY r.id, r.code, r.master ORDER BY r.id;
```

### 11.3 症状对照表

| 症状 | 最可能原因 | 看哪里 |
|---|---|---|
| 登录成功但所有接口 401 | deferred 模式未持久化 context | [8.1](#81-根因deferred-模式下-savecontext-不会自动调用) |
| 启动即 `StackOverflowError` | deferred 对象里回头读 holder | [8.2](#82-自定义-repository-时不要在-deferredget-里回头读-holder) |
| 配了仓库还是 401 | `.securityContext` 顺序在 `authorizeHttpRequests` 之后 | [8.4](#84-securitycontext-必须写在-authorizehttprequests-之前) |
| 某些接口 403，其他正常 | `basic_resource` 漏配或 `match_type` 不匹配 | 11.2 第 3 条 |
| 菜单能看见但按钮点了 403 | 菜单授了但资源没授 / 没关联 | 检查 `basic_role_permission` type=2 与 `basic_menu_resource` |
| 登录后菜单空白 | 子菜单有权限但父目录缺失 | 7.1（已自动补齐祖先） |
| 改了权限不生效 | 缓存未失效 | 检查写方法是否标了 `@GrantChanged` |
| `basic_menu` 自检为 0 | V2 脚本没跑 / 跑错库 | 11.1 |
| 重启后密码变回初始值 | `ON DUPLICATE KEY UPDATE` 含 `password` | 检查 V2 的 user 段（应不含） |
| 中文日志 `grep` 不到 | 编码 | 用 `grep -a` |

---

## 附：相关文件索引

```
src/main/java/xyz/wewin/autumn/gateway/dashboard/
├── config/
│   ├── SecurityConfig.java                        # 过滤器链、开关、401/403 JSON
│   └── ...
└── security/
    ├── JwtAuthenticationFilter.java               # 认证 + 显式持久化 context（8.1）
    ├── RequestAttributeSecurityContextRepository.java  # 请求级仓库（8.2 / 8.3）
    ├── UrlAuthorizationManager.java               # URL 级动态授权（8.5）
    ├── Permissions.java                           # 按钮级编程式入口
    ├── PermissionService.java                     # 权限装配 + 缓存
    ├── PermissionCacheInvalidator.java            # 启动自检、缓存事件
    ├── UserGrant.java / LoginUser.java / UrlResource.java
    ├── GrantChanged.java                          # 授权变更注解（8.7）
    ├── GrantChangedInterceptor.java               # 清缓存实现
    └── GrantChangedRegistrar.java                 # 注解驱动织入（零 AspectJ）

src/main/resources/db/rbac/
├── V1__rbac_schema_mysql.sql / _postgresql.sql    # 唯一键 + 索引（幂等）
└── V2__rbac_init_data_mysql.sql / _postgresql.sql # 菜单/资源/角色/账号（幂等）
```

# 项目长期约定（autumn-gateway-dashboard-ui 前端）

## UI 风格
- 参照 **maku-admin 原生**风格：主色 `#409eff`（`src/store/theme/config.js`），尽量多用蓝色。
- 列表页统一布局：**查询栏 → 操作栏（`.pro-toolbar`）→ 表格 → 分页**，卡片顶部不再放标题行和「新增」按钮。
- 状态标签用蓝色 `type="primary" effect="light"`（正常/启用），异常用 `danger`。
- 操作列链接统一 `type="primary"`（蓝色），表头居中 `align="center"`，操作列宽 340。
- 所有表格都有内部边框（网格线），全局生效。

## 全局样式
- 统一放在 **`src/styles/pro.scss`**（在 `src/styles/index.scss` 里 `@use './pro.scss'`）：
  `.pro-toolbar`（操作条）、`.link-op`（操作项间距 10px）、`.el-table` 单元格竖线、`.group-tag`。
- 不要在单个页面里重复写 scoped 同名样式，改全局那一处。

## 能力边界（重要）
- **只有 user、group 有删除能力**：`src/api/basic.js` 里仅 `basicUserApi`、`basicGroupApi` 有 `remove`；后端 `DeleteMapping` 也只在 `BasicUserController` / `BasicGroupController`。app/role/resource/menu/permission **没有删除接口**，不要给它们加删除/批量删除。
- 用户列表接口 `/basic/user` **不返回用户组**，需按行调 `basicUserApi.groups(id)` 拉取。

## 数据库与 schema 变更（重要）
- **用户的真实开发库是 MySQL**：`192.168.0.102:3306/autumn-security`（2026-10-18 由 192.168.0.115 切换），账号 `admin/123456`。
  该账号权限只有 `SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, CREATE VIEW, SHOW VIEW`——**没有 ALTER/DROP**。
- **MySQL 跑在 102 虚拟机的 Docker 容器里**（容器名 `mysql`，端口 `0.0.0.0:3306->3306`；root 有密码，应用账号是 `admin/123456`）。
  虚拟机没启动容器时 Spring Boot 报 `Cannot resolve reference to bean 'org.springframework.data.jdbc.core.JdbcAggregateOperations'` 连环错——
  真实原因只是连不上库（`jdbcDialect` 要连库读 DatabaseMetaData，`namedParameterJdbcTemplate` 又绑了 `dataSourceScriptDatabaseInitializer`，
  连接被拒 → dialect 建不出 → 所有 Spring Data JDBC 仓库挂）。看到这个错先 `nc -z 192.168.0.102 3306`，别改代码。
- **远程启动/运维 102 虚拟机**：`ssh percy@192.168.0.102`，密码 `1`（2026-10-19 用户明确要求记住）。常用命令 `docker start mysql` / `docker ps`。
  本机 macOS 无 `sshpass`、无 `setsid`；免交互登录用 `/usr/bin/expect` 脚本（密码走 `send`，OpenSSH 9.9 + `-o StrictHostKeyChecking=no`）。- 因此**任何列宽/类型变更都不能靠应用启动时改表**：`spring.sql.init.mode=always` 只跑 `CREATE TABLE IF NOT EXISTS`，不会 ALTER 已存在的表。
  正确做法：在 `src/main/resources/db/` 下按既有模式写 `migration-vX-to-vY.sql`（幂等、information_schema 存在性判断），
  并提醒用户用有 ALTER 权限的账号执行（或 `GRANT ALTER ON autumn-security.* TO 'admin'@'%'`）。
  当前最新迁移是 `db/migration-v3-to-v4.sql`（basic_app/basic_role/basic_group 加 namespace 列；basic_role 删 app_id）。
- **种子数据全部由 `NamespaceDataInitializer`（Java, ApplicationRunner）幂等播种**，已无 `data-*.sql`：
  默认租户 default、登录账号 **admin / Autumn@123456**（密码取 `autumn.user.default-password`，运行时 BCrypt 编码）、
  basic_* 演示数据。缺 namespace 列的存量库上：回填/演示数据只 WARN 不崩，启动不再中断；但 basic 页面会 500，需先跑迁移。
- 沙箱里 bash 的 `ps` 被禁；查进程用 `lsof -i :端口` + `kill -0 PID`。
- 沙箱设有 `HTTP_PROXY`，Python urllib 走代理会挂起；访问 localhost 要用 `ProxyHandler({})` 或 curl `--noproxy '*'`。
- 后端启动后需等 SQL 初始化完成才可请求（约 40s+），过早请求会挂起；用轮询 `/api/system/basic/app/list` 判断就绪。

## 构建约定（后端）
- OAuth2 授权服务器 starter 统一用 **`spring-boot-starter-security-oauth2-authorization-server`**（Boot 4 新名，旧名 `spring-boot-starter-oauth2-authorization-server` 已废弃）；版本 4.1.1 锁在根 pom 的 `spring-boot-starter-security-oauth2-authorization-server.version`，底层库 `spring-security-oauth2-authorization-server:7.1.1`。当前 Boot 4.1.1，Java 25。
- 角色定位：`autumn-gateway-authorization` 是 SAS 授权服务器（IdP，发令牌、管登录/验证码/微信）；APISIX 只能当 RP 校验令牌、不能发令牌，二者互补不是替代。用户路线：暂不引入 APISIX。
- 验证「依赖改名是否生效」必须 `clean compile` 或 `dependency:tree`；`mvn compile` 的「Nothing to compile - all classes are up to date」会跳过类路径解析，会误判通过。

## 易踩坑
- **bash grep 查中文内容会误报为空**（locale 问题），必须用 Grep 工具（ripgrep）查含中文的字符串，否则会误判"已删除/已修改"。
- **菜单图标名必须在 iconfont 库里真实存在**：图标来自 `src/icons/iconfont/iconfont.js`（599 个 symbol，Ant Design 风格，如 `file-text`/`audit`/`appstore`），`src/icons/svg/` 只有 404.svg。
  写了不存在的图标名（如曾经的 `icon-log`）不会报错，只会静默不显示。新增菜单项后建议全量校验一遍。
- Vite 默认 **`.js` 优先于 `.ts`**。`router/index.js` 已删除（只留 `index.ts`，含命名空间菜单 id 25）；
  但 `store/modules/user`、`utils/request`、`utils/constant` 等仍是 `.js`/`.ts` 双份，**实际生效的是 `.js`**，改这些文件必须改 `.js`。
- 唯一权威 auth store 是 `src/store/modules/user`（Pinia id=`userStore`），路由守卫读它；不要新建重复 store。
- 后端 `/api/currentUser` 返回 `userid` 而非 `id`。
- 本地起后端用 H2 profile：`env -u SERVER__PORT SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run`（`./mvnw` 在仓库根目录）。
  h2 依赖已补进 pom（runtime）；`application-h2.properties` 需带 `CASE_INSENSITIVE_IDENTIFIERS=TRUE`，
  否则 Spring Data JDBC 生成的 `"ID"/"NAME"` 大写列名匹配不上 schema 里的小写列，basic_* 派生查询全报 Column not found。
- `BasicResourceRepository.findWithPage` 的 `LIMIT :offset OFFSET :limit` 看似参数写反，实为与 Service 传参顺序相互抵消的既有约定，**不要"修复"**。
- **Spring Boot 4 / Spring Web 7.0.9 的 API 迁移**：`ContentCachingRequestWrapper` 已从 `org.springframework.web.filter` **移到 `org.springframework.web.util`**；且其构造器从单参 `(HttpServletRequest)` 改为**必须传 `int maxContentLength`**（如 `new ContentCachingRequestWrapper(request, 1024*1024)`）。写可重复读请求体的过滤器/拦截器要注意这两点。

## 命名空间（租户）模型（2026-10-18 定型）
- `basic_namespace`（default 默认租户）→ `basic_app`、`basic_role`、`basic_group` 都有 `namespace_id/namespace_code`，按公司/企业隔离。
- `basic_menu` / `basic_resource` **不加** namespace：它们已有 `app_id`/`app_code` 维度，而 app 归属 namespace，间接隔离，保持平台级语义。
- 角色跨应用：`basic_user_role.app_id` 是僵尸列，统一写 0；`BasicRole` 已无 app_id。
- 用户详情弹窗「关联角色」页签显示「所属命名空间」（namespaceCode），勿再改回「应用」列。

## 协作提醒
- 用户对列表列的增删需求会反复（如「最近登录」先删后加），改动列前先确认当前期望。
- 关联维护菜单（用户组-角色/用户-用户组/用户-角色/角色-菜单/角色-资源）2026-10-18 起从侧边栏隐藏，`router/index.ts` 留有注释块可快速放开；用户后续可能为权限管理重新放开，**放开时交互大概率要调整**，勿原样恢复。

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

## 易踩坑
- Vite 默认 **`.js` 优先于 `.ts`**，改 `src/store/modules/user` 等文件时要 `.ts`、`.js` 同步改。
- 唯一权威 auth store 是 `src/store/modules/user`（Pinia id=`userStore`），路由守卫读它；不要新建重复 store。
- 后端 `/api/currentUser` 返回 `userid` 而非 `id`。
- 本地起后端用 H2 profile：`env -u SERVER__PORT SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run`（`./mvnw` 在仓库根目录）。

## 协作提醒
- 用户对列表列的增删需求会反复（如「最近登录」先删后加），改动列前先确认当前期望。

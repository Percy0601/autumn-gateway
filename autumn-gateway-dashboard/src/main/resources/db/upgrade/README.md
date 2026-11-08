# 存量库升级（db/upgrade）

## 只有这一个文件需要维护

`upgrade-to-latest.sql` —— **唯一**的存量库升级脚本。新增改动往里追加一段，不要再建
`migration-vN-to-vN+1.sql`。

```sql
-- ============================================================
-- @@STEP 103 你的段名 [mysql|h2|postgresql]   ← 方言可省略，省略即三种通用
-- ============================================================
```

## 为什么能「不重复、不污染」

两层保护：

| 保护 | 做法 | 效果 |
|---|---|---|
| 版本台账 | 每段执行成功后写 `basic_schema_version`（step_id / step_name / dialect / applied_at） | 已执行过的段永久跳过，不会重复执行 |
| 语句幂等 | 段内只用 `CREATE TABLE IF NOT EXISTS`、`INSERT ... WHERE NOT EXISTS` 这类写法 | 台账丢失、手工重复执行也只是「无变化」，不会插出重复菜单/重复授权 |

执行器：`xyz.wewin.autumn.gateway.dashboard.config.SchemaUpgradeRunner`
（排在 `NamespaceDataInitializer`、`GwDataInitializer` 之后，保证段里挂菜单时父目录已存在）。

关掉它（数据库账号无 DDL 权限、或要纯手工管控）：

```properties
autumn.dashboard.db-upgrade.enabled=false
```

关掉后需要人工执行：

```bash
# MySQL
mysql -u<user> -p <db> < db/upgrade/upgrade-to-latest.sql
# H2（MODE=MySQL）
java -cp h2-2.4.240.jar org.h2.tools.Shell \
  -url "jdbc:h2:tcp://localhost:9092/autumn_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE" \
  -user sa -password "$DB_PASSWORD" -script db/upgrade/upgrade-to-latest.sql
```

## 方言

段标记末尾可写方言标签，执行器按数据源的 JDBC URL 自动挑段；**不带标签 = 三种方言通用**。
因此不再需要 `-h2database` 这类平行副本。

## 历史归档（只读）

`db/migration/archive/` 里的 `migration-v1-to-v2.sql` … `migration-v7-to-v8*.sql` 是改造前
逐版本增量的原始脚本，已整体归档、**不再改动**，仅供追溯和「仍停在 v8 之前的库逐级补齐」。

从 v8 起，所有存量变更都只进 `db/upgrade/upgrade-to-latest.sql`。

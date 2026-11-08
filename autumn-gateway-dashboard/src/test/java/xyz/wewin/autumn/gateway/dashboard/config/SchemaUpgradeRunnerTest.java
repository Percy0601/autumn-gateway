package xyz.wewin.autumn.gateway.dashboard.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单文件升级机制的行为验证（H2 内存库，不碰本地 TCP 联调库）：
 * <ol>
 *   <li>首跑：建表 → 插菜单 → 授权 → 三个段全部记账；</li>
 *   <li>再跑：三段都在台账里，一行数据都不新增（不重复）；</li>
 *   <li>台账删掉后再跑：靠语句自身幂等，仍然一行都不新增（不污染）。</li>
 * </ol>
 */
class SchemaUpgradeRunnerTest {

    private static final String URL =
            "jdbc:h2:mem:upgrade_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                    + "CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    private JdbcTemplate jdbcTemplate;

    private SchemaUpgradeRunner runner;

    @BeforeEach
    void setUp() {
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(URL, "sa", ""));
        // 同一个内存库在测试方法间共享，先清空，避免上一个用例的菜单/台账残留污染断言
        this.jdbcTemplate.execute("DROP ALL OBJECTS");
        // 模拟初始化器已播种的「网关配置」目录 + 一个管理员角色
        this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `basic_menu` ("
                + " `id` BIGINT NOT NULL AUTO_INCREMENT, `app_id` BIGINT NOT NULL DEFAULT 0,"
                + " `parent_id` BIGINT NOT NULL DEFAULT 0, `menu_type` TINYINT NOT NULL DEFAULT 2,"
                + " `menu_name` VARCHAR(128) NOT NULL DEFAULT '', `menu_url` VARCHAR(200) NOT NULL DEFAULT '',"
                + " `menu_identification` VARCHAR(200) NOT NULL DEFAULT '', `menu_icon` VARCHAR(64) NOT NULL DEFAULT '',"
                + " `sort_num` INT NOT NULL DEFAULT 0, `menu_status` TINYINT NOT NULL DEFAULT 1,"
                + " `del_flag` TINYINT NOT NULL DEFAULT 0, `create_user` BIGINT NOT NULL DEFAULT 0,"
                + " `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " `update_user` BIGINT NOT NULL DEFAULT 0,"
                + " `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " `remark` VARCHAR(200) NOT NULL DEFAULT '', PRIMARY KEY (`id`))");
        this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `basic_role` ("
                + " `id` BIGINT NOT NULL AUTO_INCREMENT, `code` VARCHAR(50) NOT NULL DEFAULT '',"
                + " `status` TINYINT NOT NULL DEFAULT 1, `del_flag` TINYINT NOT NULL DEFAULT 0,"
                + " `master` INT NOT NULL DEFAULT 2, PRIMARY KEY (`id`))");
        this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `basic_role_permission` ("
                + " `id` BIGINT NOT NULL AUTO_INCREMENT, `role_id` BIGINT NOT NULL, `ref_id` BIGINT NOT NULL,"
                + " `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP, `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + " `del_flag` INT NOT NULL DEFAULT 0, `op_user_id` BIGINT NOT NULL DEFAULT 0,"
                + " `type` INT NOT NULL DEFAULT 1, PRIMARY KEY (`id`))");

        this.jdbcTemplate.update("INSERT INTO `basic_menu` (`app_id`,`parent_id`,`menu_type`,`menu_name`,"
                + "`menu_url`,`menu_identification`,`menu_icon`,`sort_num`)"
                + " VALUES (0, 0, 1, '网关配置', '', 'gateway', 'gateway', 0)");
        this.jdbcTemplate.update("INSERT INTO `basic_role` (`code`,`master`) VALUES ('SUPER_ADMIN', 1)");
        this.jdbcTemplate.update("INSERT INTO `basic_role` (`code`,`master`) VALUES ('VIEWER', 2)");

        this.runner = new SchemaUpgradeRunner(this.jdbcTemplate,
                this.jdbcTemplate.getDataSource(), true);
    }

    @Test
    void firstRunCreatesTablesMenuAndGrantThenRecordsLedger() {
        this.runner.run(null);

        assertThat(tableExists("gw_thrift_service")).isTrue();
        assertThat(tableExists("gw_thrift_conversion")).isTrue();

        List<Long> menuIds = this.jdbcTemplate.queryForList(
                "SELECT `id` FROM `basic_menu` WHERE `menu_identification` = 'gateway:thrift'", Long.class);
        assertThat(menuIds).as("协议转换菜单应当被插入").hasSize(1);
        Long parentId = this.jdbcTemplate.queryForObject(
                "SELECT `parent_id` FROM `basic_menu` WHERE `menu_identification` = 'gateway:thrift'", Long.class);
        assertThat(parentId).as("菜单要挂在 gateway 目录下，不能落成根菜单").isEqualTo(
                this.jdbcTemplate.queryForObject(
                        "SELECT `id` FROM `basic_menu` WHERE `menu_identification` = 'gateway'", Long.class));

        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `basic_role_permission` WHERE `type` = 1", Integer.class))
                .as("只授权给 master=1 的管理员角色，普通角色不授权")
                .isEqualTo(1);

        assertThat(ledgerCount()).isEqualTo(3);
    }

    @Test
    void secondRunIsNoOpBecauseLedgerSkipsEverything() {
        this.runner.run(null);
        int menuBefore = menuCount();
        int grantBefore = grantCount();

        this.runner.run(null);

        assertThat(ledgerCount()).isEqualTo(3);
        assertThat(menuCount()).as("台账命中后不得再插菜单").isEqualTo(menuBefore);
        assertThat(grantCount()).isEqualTo(grantBefore);
    }

    @Test
    void replayAfterLosingLedgerStillDoesNotDuplicateAnything() {
        this.runner.run(null);
        int menuBefore = menuCount();
        int grantBefore = grantCount();

        // 模拟台账被清空 / 换库后重放：段内语句自身幂等，结果不应有任何新增
        this.jdbcTemplate.execute("DELETE FROM `basic_schema_version`");
        this.runner.run(null);

        assertThat(menuCount()).as("重复执行不得插出第二条协议转换菜单").isEqualTo(menuBefore);
        assertThat(grantCount()).as("重复执行不得插出重复授权").isEqualTo(grantBefore);
        assertThat(ledgerCount()).as("重放后应重新记账").isEqualTo(3);
    }

    @Test
    void disabledSwitchSkipsEverything() {
        new SchemaUpgradeRunner(this.jdbcTemplate, this.jdbcTemplate.getDataSource(), false).run(null);
        assertThat(tableExists("gw_thrift_service")).isFalse();
        assertThat(tableExists("basic_schema_version")).as("关闭后连台账都不建").isFalse();
    }

    private boolean tableExists(String table) {
        Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = ?", Integer.class, table);
        return count != null && count > 0;
    }

    private int ledgerCount() {
        return this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `basic_schema_version`", Integer.class);
    }

    private int menuCount() {
        return this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `basic_menu` WHERE `menu_identification` = 'gateway:thrift'", Integer.class);
    }

    private int grantCount() {
        return this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `basic_role_permission`", Integer.class);
    }
}

package xyz.wewin.autumn.gateway.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 存量库升级执行器：跑 {@code db/upgrade/upgrade-to-latest.sql} 里尚未执行过的段。
 *
 * <h3>为什么这么做</h3>
 * <p>以前是「每个版本一个 migration-vN-to-vN+1.sql」，还要 MySQL / H2 各来一份。
 * 文件越攒越多、口径容易漂移，运维也得记着「这个库该跑哪几个」。改成单文件后，
 * 靠两层保护保证「不重复、不污染」：</p>
 * <ol>
 *   <li><b>版本台账</b>：本文件里每个 {@code -- @@STEP <id>} 段执行成功后写入
 *       {@code basic_schema_version}。下次启动直接跳过 —— 不会重复执行；</li>
 *   <li><b>语句幂等</b>：每段正文本身也写成 {@code CREATE TABLE IF NOT EXISTS} /
 *       {@code INSERT ... WHERE NOT EXISTS}。哪怕台账被清空、手工重复跑，
 *       结果也只是「没有变化」，不会插出重复菜单 / 重复授权。</li>
 * </ol>
 *
 * <h3>方言</h3>
 * <p>段标记末尾可以带方言（{@code mysql} / {@code h2} / {@code postgresql}），不带即三种通用。
 * 执行器按当前数据源的 JDBC URL 自动挑段，因此一个文件同时服务三种库，不再维护平行副本。</p>
 *
 * <h3>顺序</h3>
 * <p>排在 {@link NamespaceDataInitializer}、{@link GwDataInitializer} 之后：
 * 有些升级段要往这些初始化器播种出来的菜单上挂东西（例如「协议转换」挂在「网关配置」目录下），
 * 先有父目录，段里的 {@code INSERT ... SELECT} 才选得出父行。</p>
 *
 * <p>关掉它（例如数据库账号没有 DDL 权限、或想完全手工管控）：
 * {@code autumn.dashboard.db-upgrade.enabled=false}。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SchemaUpgradeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgradeRunner.class);

    /** 唯一维护的升级脚本 */
    private static final String SCRIPT_PATH = "db/upgrade/upgrade-to-latest.sql";

    /** 段标记：-- @@STEP 100 thrift-generic-tables [mysql|h2|postgresql] */
    private static final Pattern STEP_HEADER =
            Pattern.compile("^--\\s*@@STEP\\s+(\\S+)(?:\\s+(.*))?$");

    private static final Set<String> DIALECTS = Set.of("mysql", "h2", "postgresql");

    private static final String LEDGER_TABLE = "basic_schema_version";

    private final JdbcTemplate jdbcTemplate;

    private final DataSource dataSource;

    private final boolean enabled;

    public SchemaUpgradeRunner(JdbcTemplate jdbcTemplate, DataSource dataSource,
                               @Value("${autumn.dashboard.db-upgrade.enabled:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!this.enabled) {
            log.info("存量库升级已关闭（autumn.dashboard.db-upgrade.enabled=false），跳过 {}", SCRIPT_PATH);
            return;
        }
        List<Section> sections;
        try {
            sections = parse(readScript());
        } catch (IOException e) {
            log.error("读取升级脚本 {} 失败：{}", SCRIPT_PATH, e.getMessage());
            return;
        }
        if (sections.isEmpty()) {
            log.warn("升级脚本 {} 里没有找到任何 @@STEP 段，跳过", SCRIPT_PATH);
            return;
        }

        String dialect = detectDialect();
        ensureLedger();
        Map<String, Section> pending = pendingSections(sections, dialect);
        if (pending.isEmpty()) {
            log.info("存量库已是最新（{} 个升级段全部执行过，方言 {}）", sections.size(), dialect);
            return;
        }
        for (Section section : pending.values()) {
            apply(section, dialect);
        }
        log.info("存量库升级完成：本次执行 {} 段（方言 {}），库结构已到最新", pending.size(), dialect);
    }

    // ==================== 执行 ====================

    private void apply(Section section, String dialect) {
        if (section.dialects() != null && !section.dialects().isEmpty()
                && !section.dialects().contains(dialect)) {
            return;
        }
        try (Connection connection = this.dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new ByteArrayResource(section.sql().getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
        } catch (SQLException | RuntimeException e) {
            // 升级失败必须让启动停下来：半套结构比「起不来」更难排查
            log.error("存量库升级段 [{} {}] 执行失败：{}", section.id(), section.name(), e.getMessage(), e);
            throw new IllegalStateException("存量库升级失败于段 " + section.id() + "：" + e.getMessage(), e);
        }
        this.jdbcTemplate.update(
                "INSERT INTO `" + LEDGER_TABLE + "` (`step_id`, `step_name`, `dialect`, `applied_at`)"
                        + " VALUES (?, ?, ?, ?)",
                section.id(), section.name(), dialect, LocalDateTime.now());
        log.info("存量库升级段 [{} {}] 已执行并记账", section.id(), section.name());
    }

    /** 台账里没有记录的段，按脚本里的顺序返回 */
    private Map<String, Section> pendingSections(List<Section> sections, String dialect) {
        Map<String, Section> pending = new LinkedHashMap<>();
        for (Section section : sections) {
            if (section.dialects() != null && !section.dialects().isEmpty()
                    && !section.dialects().contains(dialect)) {
                continue;
            }
            if (!isApplied(section.id())) {
                pending.put(section.id(), section);
            }
        }
        return pending;
    }

    private boolean isApplied(String stepId) {
        Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `" + LEDGER_TABLE + "` WHERE `step_id` = ?", Integer.class, stepId);
        return count != null && count > 0;
    }

    private void ensureLedger() {
        this.jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `" + LEDGER_TABLE + "` ("
                        + " `step_id`    VARCHAR(64)  NOT NULL COMMENT '升级段 id（脚本里的 @@STEP 编号）',"
                        + " `step_name`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '段名称，便于人工核对',"
                        + " `dialect`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '执行时命中的方言',"
                        + " `applied_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',"
                        + " PRIMARY KEY (`step_id`)"
                        + ") COMMENT='存量库升级台账：每个升级段只执行一次'");
    }

    private String detectDialect() {
        try (Connection connection = this.dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
            if (lower.contains(":h2:") || lower.startsWith("jdbc:h2")) {
                return "h2";
            }
            if (lower.contains(":postgresql:") || lower.startsWith("jdbc:postgresql")) {
                return "postgresql";
            }
            if (lower.contains(":mysql:") || lower.startsWith("jdbc:mysql")) {
                return "mysql";
            }
            log.warn("无法识别数据源方言（{}），按 mysql 处理", url);
            return "mysql";
        } catch (SQLException e) {
            throw new IllegalStateException("读取数据源方言失败：" + e.getMessage(), e);
        }
    }

    // ==================== 解析 ====================

    private String readScript() throws IOException {
        ClassPathResource resource = new ClassPathResource(SCRIPT_PATH);
        return new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 把脚本切成段。段的正文原样保留（含注释），交给 ScriptUtils 执行。
     * 第一个 @@STEP 之前的头部说明直接丢弃。
     */
    static List<Section> parse(String script) {
        List<Section> sections = new ArrayList<>();
        String id = null;
        String name = "";
        Set<String> dialects = Set.of();
        StringBuilder body = null;

        for (String rawLine : script.split("\\R")) {
            Matcher matcher = STEP_HEADER.matcher(rawLine.trim());
            if (matcher.matches()) {
                if (id != null && body != null) {
                    sections.add(new Section(id, name, dialects, body.toString()));
                }
                id = matcher.group(1);
                String rest = matcher.group(2) == null ? "" : matcher.group(2).trim();
                List<String> tokens = rest.isEmpty() ? List.of() : Arrays.asList(rest.split("\\s+"));
                Set<String> tags = new LinkedHashSet<>();
                int cut = tokens.size();
                while (cut > 0 && DIALECTS.contains(tokens.get(cut - 1).toLowerCase(Locale.ROOT))) {
                    tags.add(tokens.get(cut - 1).toLowerCase(Locale.ROOT));
                    cut--;
                }
                dialects = tags.isEmpty() ? Set.of() : tags;
                name = cut == 0 ? "" : String.join(" ", tokens.subList(0, cut));
                body = new StringBuilder();
            } else if (body != null) {
                body.append(rawLine).append('\n');
            }
        }
        if (id != null && body != null) {
            sections.add(new Section(id, name, dialects, body.toString()));
        }
        return sections;
    }

    /** 一个升级段：编号、名称、适用方言（空 = 通用）、SQL 正文 */
    record Section(String id, String name, Set<String> dialects, String sql) {
    }
}

package xyz.wewin.autumn.gateway.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.NamingStrategy;

/**
 * 兼容 MySQL 与 PostgreSQL 的关键配置。
 *
 * <p>Spring Data JDBC 自动生成的 SQL（派生查询、save/delete/findById 等）默认不会对表名加引号，
 * 而 {@code user} 是 SQL 保留字，两库都会报保留字错误。这里统一给表名包裹双引号：
 * <ul>
 *   <li>PostgreSQL 原生支持双引号标识符；</li>
 *   <li>MySQL 配合数据源已开启的 {@code ANSI_QUOTES}（见 application-mysql.properties）同样把双引号当作标识符引用。</li>
 * </ul>
 * 列名无需加引号（项目现有 @Query 也仅对 {@code "user"} 表名加引号），与手写 SQL 风格保持一致。
 */
@Configuration
public class JdbcConfig {

    @Bean
    public NamingStrategy namingStrategy() {
        return new DefaultNamingStrategy() {
            @Override
            public String getTableName(Class<?> type) {
                return "\"" + super.getTableName(type) + "\"";
            }
        };
    }
}

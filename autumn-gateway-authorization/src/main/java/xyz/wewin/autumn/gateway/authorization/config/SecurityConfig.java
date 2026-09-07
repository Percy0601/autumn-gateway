package xyz.wewin.autumn.gateway.authorization.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 普通安全配置：账号登录、页面权限
 * 对应职责：账号登录
 *
 * <p>用户数据源为数据库（见 {@link xyz.wewin.autumn.gateway.authorization.security.JdbcUserDetailsService}），
 * 与 dashboard 共用 user + user_auth_account(password) 这套账号体系。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 普通请求安全过滤器链
     * 处理登录页面、静态资源等非协议端点
     */
    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()  // 所有请求都需要登录
                )
                // 开启表单登录（Spring Security 默认登录页，生产建议自定义）
                .formLogin(form -> form.permitAll());

        return http.build();
    }

    /**
     * 密码编码器
     *
     * <p>注意：容器中存在该 Bean 时，Spring Authorization Server 会直接用它校验客户端密钥，
     * 因此 oauth2_registered_client.client_secret 存的是无算法前缀的 BCrypt 串。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

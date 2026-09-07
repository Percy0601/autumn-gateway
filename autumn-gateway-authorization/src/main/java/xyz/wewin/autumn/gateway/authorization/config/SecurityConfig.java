package xyz.wewin.autumn.gateway.authorization.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 普通安全配置：账号登录、页面权限
 * 对应职责：账号登录
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
                // 开启表单登录（Spring Security 默认登录页，生产可自定义）
                .formLogin(form -> form.permitAll());

        return http.build();
    }

    /**
     * 用户数据源（示例用内存用户）
     * 生产环境请实现自定义 UserDetailsService，从数据库/用户中心加载用户
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username("user")
                .password("{bcrypt}$2a$10$7aH8vQeNl7dY9zG0w5X6uOY8Z7X9V0W1U2S3R4Q5P6I7U8Y9T0R1E2") // password
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin")
                .password("{bcrypt}$2a$10$7aH8vQeNl7dY9zG0w5X6uOY8Z7X9V0W1U2S3R4Q5P6I7U8Y9T0R1E2") // admin123
                .roles("ADMIN", "USER")
                .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}


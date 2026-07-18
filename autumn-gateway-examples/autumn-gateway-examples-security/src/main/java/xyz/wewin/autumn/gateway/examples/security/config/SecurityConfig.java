package xyz.wewin.autumn.gateway.examples.security.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import xyz.wewin.autumn.gateway.examples.security.util.KeyUtils;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Autowired
    DynamicReactiveAuthorizationManager dynamicAuthManager;
    /**
     * 配置安全过滤链
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                // 1. 关闭 CSRF（JWT 是无状态的，不需要 CSRF 防护）
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // 2. 开启并配置 JWT 资源服务器
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                        )
                )

                // 3. 配置请求授权规则
//                .authorizeExchange(exchanges -> exchanges
//                        // OPTIONS 请求放行（解决跨域预检请求被拦截的问题）
//                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
//                        // 登录接口放行
//                        .pathMatchers("/auth/**").permitAll()
//                        // 其他所有请求都需要认证
//                        .anyExchange().authenticated()
//                )

                .authorizeExchange(exchanges -> exchanges
                        // OPTIONS 请求放行（解决跨域预检请求被拦截的问题）
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // 登录接口放行
                        .pathMatchers("/auth/**").permitAll()
                        // 其他所有请求都需要认证
                        .anyExchange().access(dynamicAuthManager)
                )


        ;

        return http.build();
    }

    /**
     * 配置 JWT 解码器
     * 这里使用 RSA 公钥验证签名（比对称加密更安全，生产环境推荐）
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        // 注意：这里应该是你的公钥字符串，或者从 classpath 读取 pem 文件
        // 为了方便演示，我假设有一个 KeyUtils 类来加载公钥
        PublicKey publicKey = KeyUtils.loadPublicKey();
        return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) publicKey).build();
    }
}

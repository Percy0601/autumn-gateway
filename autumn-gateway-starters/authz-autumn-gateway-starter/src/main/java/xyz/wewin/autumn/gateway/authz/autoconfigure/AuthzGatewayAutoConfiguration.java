package xyz.wewin.autumn.gateway.authz.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.wewin.autumn.gateway.authz.captcha.AuthzCaptchaService;
import xyz.wewin.autumn.gateway.authz.filter.AuthzGatewayFilterFactory;
import xyz.wewin.autumn.gateway.authz.jwt.AuthzJwtService;
import xyz.wewin.autumn.gateway.authz.rbac.AuthzRbacRepository;
import xyz.wewin.autumn.gateway.authz.web.AuthzLoginService;
import xyz.wewin.autumn.gateway.authz.web.EndpointController;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * 网关鉴权（AuthZ）自动配置。
 *
 * <h3>它注册了什么</h3>
 * <ul>
 *   <li>{@link AuthzJwtService}：HS256 签发 + 验签；</li>
 *   <li>{@link AuthzGatewayFilterFactory}：路由过滤器工厂，路由上写
 *       {@code Authz=authenticate,<appCode>} / {@code Authz=authorize,<appCode>}；</li>
 *   <li>{@link AuthzCaptchaService} / {@link AuthzLoginService} / {@link EndpointController}：
 *       本地登录与验证码端点。</li>
 * </ul>
 *
 * <h3>它刻意不注册什么</h3>
 * <ul>
 *   <li><b>没有 GlobalFilter</b>：不挂 {@code Authz} 的路由就是纯匿名转发，
 *       健康检查、静态资源、第三方回调不会被误拦；</li>
 *   <li><b>没有 SecurityWebFilterChain</b>：不接管 Spring Security 的过滤器链，
 *       因此能和 authn starter 的 OAuth2 客户端登录共存而不互相覆盖。</li>
 * </ul>
 *
 * <h3>缺 DataSource 时的降级</h3>
 * JDBC 相关 bean 以 {@code @ConditionalOnBean(DataSource.class)} 注册；没有数据源时
 * {@code authenticate} 模式照常工作（只验令牌，不查库），{@code authorize} 模式按 fail-closed 拒绝，
 * 登录端点返回 503。不会因缺 bean 导致整个网关起不来。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "autumn.authz", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(AbstractGatewayFilterFactory.class)
@EnableConfigurationProperties(AuthzGatewayProperties.class)
public class AuthzGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthzGatewayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuthzJwtService authzJwtService(AuthzGatewayProperties properties) {
        AuthzGatewayProperties.Jwt jwt = properties.getJwt();
        return new AuthzJwtService(jwt.getSecret(), jwt.getIssuer(), Duration.ofSeconds(jwt.getTtlSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzCaptchaService authzCaptchaService(AuthzGatewayProperties properties) {
        return new AuthzCaptchaService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public AuthzRbacRepository authzRbacRepository(DataSource dataSource, AuthzGatewayProperties properties) {
        return new AuthzRbacRepository(dataSource, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public AuthzLoginService authzLoginService(AuthzRbacRepository repository,
                                               AuthzJwtService jwtService,
                                               AuthzGatewayProperties properties) {
        return new AuthzLoginService(repository, jwtService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzGatewayFilterFactory authzGatewayFilterFactory(AuthzJwtService jwtService,
                                                               ObjectProvider<AuthzRbacRepository> repository,
                                                               AuthzGatewayProperties properties) {
        AuthzRbacRepository rbac = repository.getIfAvailable();
        log.info("网关鉴权已启用：路由过滤器 Authz=authenticate|authorize,<appCode>，默认应用 {}，RBAC 数据源 {}",
                properties.getAppCode(), rbac == null ? "未配置" : "已配置");
        return new AuthzGatewayFilterFactory(jwtService, rbac, properties);
    }

    /**
     * 本地端点只在 WebFlux 环境注册：本模块是网关组件，
     * 放进非响应式（或根本没有 web）的应用里时不应该把 Controller 塞进去。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
    public static class AuthzEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EndpointController authzEndpointController(AuthzCaptchaService captchaService,
                                                          ObjectProvider<AuthzLoginService> loginService,
                                                          AuthzGatewayProperties properties) {
            return new EndpointController(captchaService, loginService.getIfAvailable(), properties);
        }
    }
}

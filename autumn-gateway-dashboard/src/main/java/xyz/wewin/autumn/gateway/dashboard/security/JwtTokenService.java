package xyz.wewin.autumn.gateway.dashboard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 令牌解析与验签（Nimbus 实现）。
 *
 * <h3>为什么换成 Nimbus</h3>
 * <ul>
 *   <li>本服务<b>不再自签令牌</b>：令牌统一由 {@code autumn-gateway-authorization}
 *       以 RS256 签发，这里只做验签（OAuth2 Resource Server 角色）；</li>
 *   <li>原 JJWT 的 {@code jjwt-impl / jjwt-jackson} 走反射，GraalVM native 下需要额外
 *       reachability hints；Nimbus 是 Spring Authorization Server 自带的依赖，
 *       Spring Boot 官方已提供 native 元数据。</li>
 * </ul>
 *
 * <h3>令牌契约（由 authorization 签发）</h3>
 * <pre>
 *   sub        = 用户 uuid（与 basic_user.uuid 对齐，跨表可关联）
 *   username   = 登录名
 *   authorities= 角色码集合（本服务不用它做 RBAC，权限仍从 basic_* 表装配）
 * </pre>
 */
@Component
public class JwtTokenService {

    /** 解析出来的身份 */
    public record Identity(String uuid, String username, List<String> authorities) {
    }

    private final JwtDecoder jwtDecoder;

    public JwtTokenService(@Value("${autumn.security.jwt.jwk-set-uri}") String jwkSetUri) {
        // 只按 JWKS 验签 + 有效期校验，不校验 issuer / audience：
        // 授权服务器可能经网关/反代暴露多个入口地址，绑死 iss 会制造很难排查的偶发 401。
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * 解析并验签令牌。签名不对 / 已过期会抛 {@link org.springframework.security.oauth2.jwt.JwtException}，
     * 由调用方按「匿名」处理。
     */
    public Identity resolve(String token) {
        Jwt jwt = this.jwtDecoder.decode(token);
        return new Identity(jwt.getSubject(),
                jwt.getClaimAsString("username"),
                readAuthorities(jwt));
    }

    @SuppressWarnings("unchecked")
    private static List<String> readAuthorities(Jwt jwt) {
        Object raw = jwt.getClaims().get("authorities");
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (item != null && StringUtils.hasText(item.toString())) {
                result.add(item.toString());
            }
        }
        return List.copyOf(result);
    }
}

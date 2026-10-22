package xyz.wewin.autumn.gateway.dashboard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 令牌解析与验签（Nimbus 实现，不使用 JJWT）。
 *
 * <h3>为什么换成 Nimbus</h3>
 * <ul>
 *   <li>本服务<b>不签发令牌</b>，只做验签（OAuth2 Resource Server 角色）；</li>
 *   <li>JJWT 的 {@code jjwt-impl / jjwt-jackson} 走反射，GraalVM native 下需要额外
 *       reachability hints；Nimbus 是 Spring Authorization Server 自带的依赖，
 *       Spring Boot 官方已提供 native 元数据。</li>
 * </ul>
 *
 * <h3>两条验签轨道（并存，任一通过即通过）</h3>
 * <ol>
 *   <li><b>共享密钥 HS256</b>（{@code autumn.security.jwt.secret}）—— 轻量模式。
 *       令牌由网关 starter 的 {@code /gateway/endpoint/login} 签发，
 *       本服务用同一把密钥验签，不需要任何外部依赖（不连授权服务器、不拉 JWKS）。</li>
 *   <li><b>JWKS RS256</b>（{@code autumn.security.jwt.jwk-set-uri}）——
 *       令牌由 {@code autumn-gateway-authorization} 签发时用此轨道。保留是为了
 *       不破坏已有方案，两者可同时配置、平滑切换。</li>
 * </ol>
 *
 * <p>顺序是先 HS256 再 JWKS：HS256 是本地计算，没有网络往返；失败（签名不匹配 /
 * 算法不同）才落到 JWKS。两轨道都由 Nimbus 默认校验器校验 {@code exp} / {@code nbf}。</p>
 *
 * <h3>令牌契约</h3>
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

    /** 共享密钥（HS256）验签器；未配置 secret 时为 {@code null} */
    private final JwtDecoder sharedSecretDecoder;

    /** JWKS（RS256）验签器；未配置 jwk-set-uri 时为 {@code null} */
    private final JwtDecoder jwkSetDecoder;

    public JwtTokenService(@Value("${autumn.security.jwt.secret:}") String secret,
                           @Value("${autumn.security.jwt.algorithm:HS256}") String algorithm,
                           @Value("${autumn.security.jwt.jwk-set-uri:}") String jwkSetUri) {
        this.sharedSecretDecoder = StringUtils.hasText(secret) ? hmacDecoder(secret, algorithm) : null;
        // 只按 JWKS 验签 + 有效期校验，不校验 issuer / audience：
        // 授权服务器可能经网关/反代暴露多个入口地址，绑死 iss 会制造很难排查的偶发 401。
        this.jwkSetDecoder = StringUtils.hasText(jwkSetUri) ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build() : null;

        if (this.sharedSecretDecoder == null && this.jwkSetDecoder == null) {
            throw new IllegalStateException(
                    "JWT 验签未配置：至少要给 autumn.security.jwt.secret（共享密钥 HS256）"
                            + "或 autumn.security.jwt.jwk-set-uri（RS256 公钥集）中的一个");
        }
    }

    /**
     * 解析并验签令牌：先用共享密钥验 HS256，失败再用 JWKS 验 RS256，任一成功即可。
     *
     * <p>签名不对 / 已过期会抛 {@link JwtException}（或 {@link IllegalArgumentException}），
     * 由调用方按「匿名」处理。</p>
     */
    public Identity resolve(String token) {
        RuntimeException failure = null;

        if (this.sharedSecretDecoder != null) {
            try {
                return toIdentity(this.sharedSecretDecoder.decode(token));
            } catch (JwtException | IllegalArgumentException ex) {
                // 不是本轨道签的（或已过期）：记下来，交给下一轨道，最后统一抛。
                // 只保留第一条报错：HS256 是主轨道，它的原因（比如「Jwt expired at ...」）
                // 比 JWKS 轨道的「算法不匹配」更有诊断价值。
                failure = ex;
            }
        }

        if (this.jwkSetDecoder != null) {
            try {
                return toIdentity(this.jwkSetDecoder.decode(token));
            } catch (JwtException | IllegalArgumentException ex) {
                if (failure == null) {
                    failure = ex;
                }
            }
        }

        throw (failure != null) ? failure : new JwtException("没有可用的 JWT 验签器");
    }

    private static Identity toIdentity(Jwt jwt) {
        return new Identity(jwt.getSubject(), jwt.getClaimAsString("username"), readAuthorities(jwt));
    }

    /** 共享密钥验签器。密钥过短直接启动失败 —— 运行时才发现会表现为「登录成功但接口全 401」。 */
    private static JwtDecoder hmacDecoder(String secret, String algorithm) {
        MacAlgorithm mac = macAlgorithm(algorithm);
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("autumn.security.jwt.secret 至少需要 32 个字节（HS256 要求 256 位密钥），当前只有 "
                    + keyBytes.length + " 字节");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA" + mac.getName().substring(2));
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(mac).build();
    }

    /** 共享密钥只支持 HMAC 系列：{@code HS256 / HS384 / HS512}（默认 HS256） */
    private static MacAlgorithm macAlgorithm(String algorithm) {
        String name = (algorithm == null || algorithm.isBlank()) ? "HS256" : algorithm.trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "HS256" -> MacAlgorithm.HS256;
            case "HS384" -> MacAlgorithm.HS384;
            case "HS512" -> MacAlgorithm.HS512;
            default -> throw new IllegalStateException(
                    "不支持的 autumn.security.jwt.algorithm=" + algorithm + "，共享密钥只支持 HS256 / HS384 / HS512");
        };
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

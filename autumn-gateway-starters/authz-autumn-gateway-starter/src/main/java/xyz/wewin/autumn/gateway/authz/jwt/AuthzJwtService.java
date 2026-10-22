package xyz.wewin.autumn.gateway.authz.jwt;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 令牌签发与验签：Spring Security Nimbus 实现（{@link NimbusJwtEncoder} / {@link NimbusJwtDecoder}），HS256。
 *
 * <h3>为什么是 Nimbus 而不是 JJWT</h3>
 * <ul>
 *   <li>{@code jjwt-impl / jjwt-jackson} 走反射，GraalVM native 下要补一堆 reachability hints；
 *       Nimbus 是 Spring Authorization Server 的既有依赖，Boot 官方已提供 native 元数据；</li>
 *   <li>签发与验签由同一个服务完成（网关自签自用），只需要一把共享密钥，不必引入 JWK 端点。</li>
 * </ul>
 *
 * <h3>载荷契约</h3>
 * <pre>
 *   sub         用户 uuid（uuid 为空时回退 user_id）
 *   user_id     basic_user.id
 *   username    登录名
 *   authorities 角色编码集合
 *   admin       是否管理员
 *   iss / iat / exp / jti
 * </pre>
 * {@code iss} 按配置原样写入，不强制是 URL（很多部署方填的是应用名）；
 * 本模块只验签名与有效期，不校验 iss / aud。
 *
 * <p>本类是纯同步的：编解码是 CPU 运算，单次几十微秒，放进 Reactor 链里直接调用即可，
 * 不需要额外切线程。</p>
 */
public final class AuthzJwtService {

    /** HS256 要求密钥不少于 32 字节；不足直接拒绝启动，避免「签了等于没签」 */
    public static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_USER_ID = "user_id";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String CLAIM_ADMIN = "admin";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final Duration ttl;

    public AuthzJwtService(String secret, String issuer, Duration ttl) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "autumn.authz.jwt.secret 至少 " + MIN_SECRET_BYTES + " 字节（HS256 要求 256 bit 密钥）");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.issuer = StringUtils.hasText(issuer) ? issuer : "autumn-gateway";
        this.ttl = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? Duration.ofHours(24) : ttl;
    }

    /** 签发令牌。subject 传空时回退 user_id，保证 sub 永远有值。 */
    public String issue(String subject, String userId, String username,
                        Collection<String> authorities, boolean admin) {
        Instant now = Instant.now();
        String sub = StringUtils.hasText(subject) ? subject : userId;

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(this.issuer)
                .subject(sub)
                .issuedAt(now)
                .expiresAt(now.plus(this.ttl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_AUTHORITIES, authorities == null ? List.of() : List.copyOf(authorities))
                .claim(CLAIM_ADMIN, admin)
                .build();

        return this.encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * 验签并解析。签名不对、已过期、缺 user_id 都返回 {@link Optional#empty()}——
     * 调用方一律按「未认证」处理，不区分具体原因，避免给攻击者反馈细节。
     */
    public Optional<AuthzClaims> parse(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        Jwt jwt;
        try {
            jwt = this.decoder.decode(token);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        String userId = readUserId(jwt);
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        String username = jwt.getClaimAsString(CLAIM_USERNAME);
        return Optional.of(new AuthzClaims(jwt.getSubject(),
                userId,
                username == null ? "" : username,
                readAuthorities(jwt),
                Boolean.TRUE.equals(jwt.getClaim(CLAIM_ADMIN))));
    }

    private static String readUserId(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM_USER_ID);
        return raw == null ? null : String.valueOf(raw);
    }

    private static List<String> readAuthorities(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM_AUTHORITIES);
        if (!(raw instanceof Collection<?> items)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item != null && StringUtils.hasText(item.toString())) {
                result.add(item.toString());
            }
        }
        return List.copyOf(result);
    }
}

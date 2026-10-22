package xyz.wewin.autumn.gateway.authz.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JWT 签发/验签的核心契约测试（HS256 + Nimbus，不依赖 Spring 容器）。 */
class AuthzJwtServiceTest {

    private static final String SECRET = "autumn-gateway-authz-jwt-secret-0123456789abcdef";
    private static final String ISSUER = "autumn-gateway-test";

    private final AuthzJwtService service = new AuthzJwtService(SECRET, ISSUER, Duration.ofHours(24));

    @Test
    void issueThenParseKeepsEveryClaim() {
        String token = this.service.issue("uuid-1001", "42", "alice", List.of("admin", "ops"), true);

        Optional<AuthzClaims> parsed = this.service.parse(token);
        assertTrue(parsed.isPresent());
        AuthzClaims claims = parsed.get();
        assertEquals("uuid-1001", claims.subject());
        assertEquals("42", claims.userId());
        assertEquals("alice", claims.username());
        assertEquals(List.of("admin", "ops"), claims.authorities());
        assertTrue(claims.admin());
        assertEquals("admin,ops", claims.rolesHeader());
    }

    /** 标准 claims 必须由 Nimbus 写入：iss / iat / exp / jti 缺一个下游就可能误判。 */
    @Test
    void issuedTokenCarriesStandardClaims() {
        String token = this.service.issue("uuid-1", "1", "bob", List.of(), false);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(secretKey(SECRET)).build().decode(token);
        // iss 按原样写入（部署方常填应用名而非 URL），因此读 claim 而不是 getIssuer()（后者强转 URL）
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertTrue(jwt.getIssuedAt().isBefore(jwt.getExpiresAt()));
        assertEquals(Duration.ofHours(24).toSeconds(),
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        assertFalse(jwt.getId().isBlank());
    }

    /** uuid 为空时 sub 回退 user_id，保证 sub 永远有值（OIDC 侧大量代码假设 sub 非空）。 */
    @Test
    void subjectFallsBackToUserId() {
        String token = this.service.issue(null, "77", "carol", List.of("user"), false);

        AuthzClaims claims = this.service.parse(token).orElseThrow();
        assertEquals("77", claims.subject());
    }

    @Test
    void tokenSignedByAnotherSecretIsRejected() {
        String token = this.service.issue("uuid-1", "1", "bob", List.of(), false);
        AuthzJwtService other = new AuthzJwtService(SECRET + "-rotated", ISSUER, Duration.ofHours(1));

        assertTrue(other.parse(token).isEmpty());
    }

    @Test
    void garbageTokenIsRejected() {
        assertTrue(this.service.parse("not-a-jwt").isEmpty());
        assertTrue(this.service.parse("").isEmpty());
    }

    /** 没有 user_id 的令牌视为无效：鉴权链路上处处要用它查角色 */
    @Test
    void tokenWithoutUserIdIsRejected() {
        String token = this.service.issue("uuid-1", "", "bob", List.of(), false);

        assertTrue(this.service.parse(token).isEmpty());
    }

    /** HS256 的 32 字节下限必须在构造期就挡住，而不是等签名时抛个看不懂的错 */
    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthzJwtService("too-short", ISSUER, Duration.ofHours(1)));
    }

    private static SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}

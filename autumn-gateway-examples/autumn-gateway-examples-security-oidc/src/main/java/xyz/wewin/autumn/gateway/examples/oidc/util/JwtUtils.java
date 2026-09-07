package xyz.wewin.autumn.gateway.examples.oidc.util;

import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

public class JwtUtils {

    private static final PrivateKey PRIVATE_KEY = KeyUtils.loadPrivateKey();

    public static String generateToken(String username, String userId, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("user_id", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1小时过期
                .signWith(PRIVATE_KEY, Jwts.SIG.RS256) // 使用私钥签名
                .compact();
    }
}

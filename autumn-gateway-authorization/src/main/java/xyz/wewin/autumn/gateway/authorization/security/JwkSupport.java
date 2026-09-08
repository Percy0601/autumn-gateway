package xyz.wewin.autumn.gateway.authorization.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;

/**
 * JWK 密钥对的生成、序列化与反序列化。
 *
 * <p>内存 / 文件 / 数据库三种存储方式共用这套逻辑。</p>
 */
public final class JwkSupport {

    private JwkSupport() {
    }

    /**
     * 生成 RSA 2048 密钥对
     */
    public static JWKSet generate(String keyId) {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();

        return new JWKSet(rsaKey);
    }

    /**
     * 序列化为 JWK Set JSON。
     *
     * <p>必须显式传 false：JWKSet 的默认序列化可能只输出公钥部分，
     * 那样存进去再读出来就没有私钥、无法签名。</p>
     */
    public static String serialize(JWKSet jwkSet) {
        return jwkSet.toString(false);
    }

    /**
     * 从 JWK Set JSON 还原，并校验私钥存在
     */
    public static JWKSet parse(String json) {
        JWKSet jwkSet;
        try {
            jwkSet = JWKSet.parse(json);
        } catch (ParseException ex) {
            throw new IllegalStateException("持久化密钥解析失败，请检查 oauth2_jwk 内容是否正确: " + ex.getMessage(), ex);
        }
        boolean hasPrivate = jwkSet.getKeys().stream()
                .anyMatch(com.nimbusds.jose.jwk.JWK::isPrivate);
        if (!hasPrivate) {
            throw new IllegalStateException("持久化的 JWK 中不含私钥，无法用于签名；请删除 oauth2_jwk 对应记录后重启以重新生成");
        }
        return jwkSet;
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("生成RSA密钥失败", ex);
        }
    }
}

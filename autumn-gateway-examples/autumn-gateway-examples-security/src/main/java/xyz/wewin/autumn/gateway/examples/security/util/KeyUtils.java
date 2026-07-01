package xyz.wewin.autumn.gateway.examples.security.util;

import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public class KeyUtils {
    private static final String KEY_STORE_PATH = "keystore.p12";
    // ⚠️ 生产环境严禁明文写死，应通过环境变量或 @Value 注入
    private static final String KEY_STORE_PASSWORD = "123456";
    private static final String KEY_ALIAS = "jwt";


    public static PublicKey loadPublicKey() {
        /* 从 classpath 或文件加载公钥 */
        try {
            // 1. 加载 KeyStore 文件
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream inputStream = new ClassPathResource(KEY_STORE_PATH).getInputStream();
            keyStore.load(inputStream, KEY_STORE_PASSWORD.toCharArray());

            // 2. 获取证书（Certificate）
            Certificate certificate = keyStore.getCertificate(KEY_ALIAS);
            if (certificate == null) {
                throw new RuntimeException("未找到别名为 '" + KEY_ALIAS + "' 的证书");
            }

            // 3. 从证书中提取公钥
            return certificate.getPublicKey();
        } catch (Exception e) {
            throw new RuntimeException("加载公钥失败", e);
        }
    }
    public static PrivateKey loadPrivateKey() {
        /* 从 keystore 加载私钥 */
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream inputStream = new ClassPathResource(KEY_STORE_PATH).getInputStream();
            keyStore.load(inputStream, KEY_STORE_PASSWORD.toCharArray());

            // getKey 需要 key 的密码（第二个参数）
            // 如果 key 密码和 keystore 密码一致，直接用前者即可
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, KEY_STORE_PASSWORD.toCharArray());

            if (privateKey == null) {
                throw new RuntimeException("未找到别名为 '" + KEY_ALIAS + "' 的私钥");
            }
            return privateKey;
        } catch (Exception e) {
            throw new RuntimeException("加载私钥失败", e);
        }
    }
}

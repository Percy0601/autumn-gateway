package xyz.wewin.autumn.gateway.authorization.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.List;

/**
 * GraalVM native 反射提示：授权服务器的 JDBC 持久化需要 Jackson 读取带类型信息的 JSON。
 *
 * <h3>为什么必须显式注册</h3>
 * <p>{@code oauth2_registered_client.client_settings / token_settings} 与
 * {@code oauth2_authorization} 的元数据列，都是 Spring Authorization Server 用
 * <b>开启了默认多态类型（default typing）的 Jackson</b> 写进去的，JSON 里带
 * {@code "@class"} 类型标识，例如：</p>
 * <pre>{@code
 * {"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true}
 * }</pre>
 * <p>读回来时 Jackson 要按类型标识 {@code Class.forName(...)} 解析出具体类型。
 * JVM 下没问题，native image 下这些 JDK 内部类不在镜像里，解析失败会退化成
 * 「PolymorphicTypeValidator denied resolution」：</p>
 * <pre>
 * tools.jackson.databind.exc.InvalidTypeIdException:
 *   Could not resolve type id 'java.util.Collections$UnmodifiableMap' ...
 * </pre>
 * <p>典型症状：<b>启动只有 0.1 秒、端口也起来了，但第一个请求就 500</b>。</p>
 *
 * <h3>两类注册缺一不可（踩过的坑）</h3>
 * <ol>
 *   <li><b>JDK 集合内部类</b>（{@code java.util.Collections$UnmodifiableMap} 等）——
 *       多态类型标识要 {@code Class.forName} 解析得到。</li>
 *   <li><b>Spring Security 的 Jackson 模块类</b>（{@code CoreJacksonModule}、
 *       {@code OAuth2AuthorizationServerJacksonModule} 等）—— 这才是真正的根因。
 *       这些模块是 {@code SecurityJacksonModules.getModules()} 通过
 *       {@code Class.forName(...).getConstructor().newInstance()} 反射加载的，
 *       native 下没注册就<b>静默失败</b>（源码里 catch 掉只打 DEBUG 日志），
 *       于是开启默认多态类型时用的 {@code BasicPolymorphicTypeValidator} 少了全部白名单条目，
 *       表现得像是「JDK 类没注册」，实际是<b>白名单根本没建起来</b>。</li>
 * </ol>
 * <p>排查手法：用 {@code --logging.level.org.springframework.security.jackson=DEBUG}
 * 启动 native 二进制，看到满屏 {@code Cannot load module org.springframework.security...}
 * 就是这个原因。</p>
 */
public class AuthorizationRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Spring Security 的 Jackson 模块：反射实例化，必须注册构造器。
     * 用 registerTypeIfPresent 逐个尝试 —— 模块是否在当前 classpath 上取决于引入了哪些 starter。
     */
    private static final List<String> JACKSON_MODULES = List.of(
            "org.springframework.security.jackson.CoreJacksonModule",
            "org.springframework.security.web.jackson.WebJacksonModule",
            "org.springframework.security.web.jackson.WebServletJacksonModule",
            "org.springframework.security.web.server.jackson.WebServerJacksonModule",
            "org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule",
            "org.springframework.security.oauth2.client.jackson.OAuth2ClientJacksonModule",
            "org.springframework.security.ldap.jackson.LdapJacksonModule",
            "org.springframework.security.saml2.jackson.Saml2JacksonModule",
            "org.springframework.security.cas.jackson.CasJacksonModule"
    );

    /** 按类名注册：其中 java.util.Collections$* 是包级私有类，代码里引用不到，只能按名字注册 */
    private static final List<String> TYPES = List.of(
            // ClientSettings / TokenSettings 的 Map 包装类型
            "java.util.Collections$UnmodifiableMap",
            "java.util.Collections$UnmodifiableSortedMap",
            "java.util.Collections$UnmodifiableNavigableMap",
            "java.util.Collections$UnmodifiableList",
            "java.util.Collections$UnmodifiableRandomAccessList",
            "java.util.Collections$UnmodifiableCollection",
            "java.util.Collections$UnmodifiableSet",
            "java.util.Collections$UnmodifiableSortedSet",
            "java.util.Collections$UnmodifiableNavigableSet",
            "java.util.Collections$SingletonMap",
            "java.util.Collections$SingletonList",
            "java.util.Collections$SingletonSet",
            "java.util.Collections$EmptyMap",
            "java.util.Collections$EmptyList",
            "java.util.Collections$EmptySet",
            // 反序列化时的实际承载类型
            "java.util.LinkedHashMap",
            "java.util.HashMap",
            "java.util.ArrayList",
            "java.util.LinkedHashSet",
            // token_settings 里出现的值类型
            "java.time.Duration",
            "org.springframework.security.oauth2.jose.jws.SignatureAlgorithm",
            "org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat"
    );

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String type : TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
        for (String module : JACKSON_MODULES) {
            hints.reflection().registerTypeIfPresent(classLoader, module,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }
}

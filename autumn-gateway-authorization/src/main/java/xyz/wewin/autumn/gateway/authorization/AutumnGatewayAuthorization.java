package xyz.wewin.autumn.gateway.authorization;

//import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import xyz.wewin.autumn.gateway.authorization.config.AuthorizationRuntimeHints;

/**
 * 授权服务器启动类。
 *
 * <p>{@link ImportRuntimeHints}：注册 GraalVM native 下 Jackson 读
 * {@code oauth2_registered_client} 所需的反射元数据。缺了它，native 镜像能启动（0.1s）
 * 但第一个用到客户端的请求就会因为类型解析失败而崩 —— 详见 {@link AuthorizationRuntimeHints}。</p>
 */
@SpringBootApplication
@ImportRuntimeHints(AuthorizationRuntimeHints.class)
public class AutumnGatewayAuthorization {

	static void main(String[] args) {
		SpringApplication.run(AutumnGatewayAuthorization.class, args);
	}


}

package xyz.wewin.autumn.gateway.dashboard;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文启动依赖外部 MySQL（见 application.properties 的 spring.datasource.url）。
 * 无可用数据库的环境（CI / 离线开发）会连接失败，故默认跳过；
 * 需要验证时请在能连通数据库的环境下手动启用。
 */
@Disabled("依赖外部 MySQL 环境，无库环境下跳过")
@SpringBootTest
class AutumnGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}

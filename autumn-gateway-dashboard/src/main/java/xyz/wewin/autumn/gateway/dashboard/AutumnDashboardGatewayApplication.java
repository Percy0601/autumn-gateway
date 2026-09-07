package xyz.wewin.autumn.gateway.dashboard;

//import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@MapperScan(basePackages = "xyz.wewin.autumn.gateway.dashboard.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
@SpringBootApplication
public class AutumnDashboardGatewayApplication {

	static void main(String[] args) {
		SpringApplication.run(AutumnDashboardGatewayApplication.class, args);
	}


}

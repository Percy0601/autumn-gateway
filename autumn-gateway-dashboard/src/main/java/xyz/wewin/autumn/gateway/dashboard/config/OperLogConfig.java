package xyz.wewin.autumn.gateway.dashboard.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册操作日志拦截器，仅对 /api/** 下的请求生效（静态资源 / 健康检查等不拦截）。
 * 是否落库、落哪些字段由 {@link OperLogInterceptor} 内部按方法（POST/PUT/DELETE）再过滤。
 */
@Configuration
public class OperLogConfig implements WebMvcConfigurer {

    @Autowired
    private OperLogInterceptor operLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operLogInterceptor).addPathPatterns("/api/**");
    }
}

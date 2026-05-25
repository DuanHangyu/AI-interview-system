package system.assessment.defense.infrastructure.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author taoHouChao
 * @Date 15:25 2025/6/9
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns("/login",
                        "/actuator/**",
                        "/gemini/**",
                        "/swagger-ui/**",          // 排除Swagger UI路径
                        "/v3/api-docs/**",       // 排除API文档路径
                        "/swagger-resources/**",   // 新增静态资源路径
                        "/webjars/**",           // 新增WebJAR资源路径
                        "/swagger-ui.html",
                        "/filetrans/callback/result");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 2. 优化资源映射（添加WebJAR路径）
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc/openapi-ui/");
    }
}

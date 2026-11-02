package system.assessment.defense.infrastructure.config;

import cn.dev33.satoken.router.SaRouter;
import org.springframework.context.annotation.Lazy;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;
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

    private static final int STUDENT = 0;
    private static final int TEACHER = 1;
    private static final int ADMIN = 2;
    private static final String USER_TYPE_KEY = "userType";

    private final UserService userService;

    public SaTokenConfig(@Lazy UserService userService) {
        this.userService = userService;
    }

    /**
     * 按账号类型限制接口访问。类型缓存在登录会话里，避免每个请求都查库。
     */
    private void requireUserType(int... allowed) {
        Object cached = StpUtil.getSession().get(USER_TYPE_KEY, () -> userService.findById(StpUtil.getLoginIdAsInt())
                .map(UserPO::getType)
                .orElse(-1));
        int type = cached instanceof Number number ? number.intValue() : -1;
        for (int t : allowed) {
            if (t == type) {
                return;
            }
        }
        throw new BusinessException(403, "当前账号无权访问该功能");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    // 教师账号管理仅管理员可用；协作教师下拉列表教师也需要
                    SaRouter.match("/backend/teacher/**").notMatch("/backend/teacher/all-teacher")
                            .check(r -> requireUserType(ADMIN));
                    SaRouter.match("/backend/**").check(r -> requireUserType(TEACHER, ADMIN));
                    SaRouter.match("/front/**", "/assessment/**").check(r -> requireUserType(STUDENT));
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/login",
                        "/actuator/**",
                        "/file/raw",
                        "/swagger-ui/**",          // 排除Swagger UI路径
                        "/v3/api-docs/**",       // 排除API文档路径
                        "/swagger-resources/**",   // 新增静态资源路径
                        "/webjars/**",           // 新增WebJAR资源路径
                        "/swagger-ui.html",
                        "/error");
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

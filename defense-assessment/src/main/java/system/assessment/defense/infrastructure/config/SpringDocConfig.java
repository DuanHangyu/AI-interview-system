package system.assessment.defense.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author taoHouChao
 * @Date 15:36 2025/6/9
 */
@OpenAPIDefinition(
        servers = {
                @Server(description = "开发环境服务器", url = "http://localhost:8081"),
                @Server(description = "测试环境服务器", url = "https://web.youningtao.cn")
        }
)
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        // 1. 定义 SecurityScheme（安全方案）
        SecurityScheme securityScheme = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT") // 可选，说明是 JWT
                .in(SecurityScheme.In.HEADER);

        // 2. 定义全局 SecurityRequirement（哪些接口需要认证）
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("Authorization");
        return new OpenAPI()
                // 配置接口文档基本信息
                .info(this.getApiInfo())
                .addSecurityItem(securityRequirement) // 全局生效
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Authorization", securityScheme));
    }

    private Info getApiInfo() {
        return new Info()
                // 配置文档标题
                .title("SpringBoot3集成Swagger3")
                // 配置文档描述
                .description("SpringBoot3集成Swagger3示例文档")
                // 配置版本号
                .version("2.0");
    }
}

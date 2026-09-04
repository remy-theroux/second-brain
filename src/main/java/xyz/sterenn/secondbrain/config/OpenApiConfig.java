package xyz.sterenn.secondbrain.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI secondBrainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Second Brain API")
                        .description("API du projet Second Brain")
                        .version("v0.0.1")
                        .license(new License().name("Proprietary")));
    }
}

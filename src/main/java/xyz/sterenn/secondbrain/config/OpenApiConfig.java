package xyz.sterenn.secondbrain.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Métadonnées exposées par Swagger UI / OpenAPI (springdoc).
 */
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

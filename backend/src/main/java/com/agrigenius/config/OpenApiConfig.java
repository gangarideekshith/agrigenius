package com.agrigenius.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. The document is only served when {@code springdoc.api-docs.enabled}
 * is true (local profile). The bearer scheme is declared now so endpoints added with JWT
 * authentication in Milestone 2 can reference it.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI agriGeniusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgriGenius API")
                        .version("v1")
                        .description("Public REST API of the AgriGenius backend. "
                                + "The ML service is internal and not part of this API."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}

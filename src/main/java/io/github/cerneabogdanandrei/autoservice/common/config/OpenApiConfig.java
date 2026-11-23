package io.github.cerneabogdanandrei.autoservice.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI autoServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auto Service API")
                        .version("v1")
                        .description("""
                                REST API for managing an auto repair shop: clients, vehicles, appointments, \
                                interventions and parts inventory. Errors follow RFC 9457 (Problem Details) \
                                and carry a machine-readable `code` property.

                                Authenticate with `POST /api/auth/login`, then click **Authorize** and paste \
                                the returned `accessToken`."""))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}

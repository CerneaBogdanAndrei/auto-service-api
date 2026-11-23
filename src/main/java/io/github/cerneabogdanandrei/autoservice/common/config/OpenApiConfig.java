package io.github.cerneabogdanandrei.autoservice.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI autoServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auto Service API")
                        .version("v1")
                        .description("""
                                REST API for managing an auto repair shop: clients, vehicles, appointments, \
                                interventions and parts inventory. Errors follow RFC 9457 (Problem Details) \
                                and carry a machine-readable `code` property."""));
    }
}

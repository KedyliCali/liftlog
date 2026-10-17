package com.behlul.liftlog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI liftlogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LiftLog API")
                .version("v1")
                .description("Workout logging API for the LiftLog Android app"));
    }
}

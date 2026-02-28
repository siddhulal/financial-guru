package com.financialguru.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI financialGuruOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("FinancialGuru API")
                .description("Personal Financial Advisor System — Private, Local, Intelligent")
                .version("1.0.0")
                .contact(new Contact()
                    .name("FinancialGuru")
                    .email("local@financialguru.local")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local Development")
            ));
    }
}

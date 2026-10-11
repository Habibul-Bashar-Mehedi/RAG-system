package com.example.ragsystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ragSystemOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RAG System API")
                .version("v1")
                .description("Upload PDFs, process them into embeddings, and ask grounded questions with citations."));
    }
}

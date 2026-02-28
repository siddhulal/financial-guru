package com.financialguru.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.timeout-seconds:120}")
    private int timeoutSeconds;

    @Bean(name = "ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        RestTemplate rt = new RestTemplate(factory);
        // Prepend base URL via interceptor
        rt.getInterceptors().add((request, body, execution) -> {
            if (!request.getURI().toString().startsWith("http")) {
                throw new IllegalArgumentException("Use full URL with ollamaRestTemplate");
            }
            return execution.execute(request, body);
        });
        // Store base URL for use in service
        return rt;
    }

    @Bean(name = "defaultRestTemplate")
    public RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    @Bean
    public String ollamaBaseUrl() {
        return ollamaBaseUrl;
    }
}

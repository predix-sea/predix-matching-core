package com.predix.matching.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, PredixProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getClients().getMarketSchema().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getClients().getMarketSchema().getReadTimeoutMs()))
                .build();
    }
}

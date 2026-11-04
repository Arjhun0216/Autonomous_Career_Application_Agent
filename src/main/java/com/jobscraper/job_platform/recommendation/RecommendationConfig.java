package com.jobscraper.job_platform.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RecommendationConfig {

    @Bean
    public RestClient embeddingRestClient(
            @Value("${recommendation.embedding-service.url}")
            String baseUrl) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers ->
                        headers.setContentType(
                                MediaType.APPLICATION_JSON
                        )
                )
                .build();
    }
}
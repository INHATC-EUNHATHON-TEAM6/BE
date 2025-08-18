package com.words_hanjoom.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DictionaryClientConfig {

    @Bean
    public WebClient dicWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://opendict.korean.go.kr/api")
                .build();
    }
}
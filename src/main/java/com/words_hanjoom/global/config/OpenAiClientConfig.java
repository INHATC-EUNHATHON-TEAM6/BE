// com.words_hanjoom.global.config.OpenAiClientConfig
package com.words_hanjoom.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiClientConfig {

    @Bean("openAiWebClient")
    public WebClient openAiWebClient(
            @Value("${openai.api.base}") String baseUrl,
            @Value("${openai.api.key}")  String apiKey
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                        .build())
                .build();
    }
}
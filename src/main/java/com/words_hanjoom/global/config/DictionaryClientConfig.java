package com.words_hanjoom.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DictionaryClientConfig {

    // 표준국어대사전(https://stdict.korean.go.kr) 용
    @Bean("niklWebClient")
    public WebClient niklWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://stdict.korean.go.kr") // <- /api 없음. 요청에서 /api 붙여라.
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build())
                .build();
    }

}
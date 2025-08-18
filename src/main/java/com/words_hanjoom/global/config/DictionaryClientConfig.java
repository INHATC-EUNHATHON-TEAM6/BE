package com.words_hanjoom.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DictionaryClientConfig {

    @Value("${nikl.base-url}")
    private String baseUrl;

    @Bean("dicWebClient")
    public WebClient dicWebClient(ObjectMapper mapper) {
        MediaType TEXT_JSON = new MediaType("text", "json");

        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> {
                    c.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON, TEXT_JSON));
                    c.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON, TEXT_JSON));
                })
                .build();
    }
}
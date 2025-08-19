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

    @Bean("dicWebClient")
    public WebClient dicWebClient(WebClient.Builder builder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    ObjectMapper om = new ObjectMapper();
                    c.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(om, MediaType.valueOf("text/json")));
                    c.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(om, MediaType.valueOf("text/json")));
                })
                .build();

        return builder
                .baseUrl("https://stdict.korean.go.kr/api") // 표준국어대사전 사용 시
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.ACCEPT, "text/json;charset=UTF-8")
                .build();
    }
}
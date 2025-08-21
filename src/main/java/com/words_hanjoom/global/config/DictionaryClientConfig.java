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
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
public class DictionaryClientConfig {

    @Value("${nikl.base-url}")
    private String baseUrl;

    @Bean("dicWebClient")
    public WebClient dicWebClient(WebClient.Builder builder) {
        // baseUrl이 null인지 확인하고 오류 발생
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("nikl.base-url is not set in application.properties");
        }

        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(baseUrl);
        // 값만 안전하게 인코딩 (한글 포함 쿼리 파라미터 인코딩)
        f.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
        // 또는 f.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    ObjectMapper om = new ObjectMapper();
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(om, MediaType.valueOf("text/json")));
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(om, MediaType.valueOf("text/json")));
                })
                .build();

        return builder
                .uriBuilderFactory(f)
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.ACCEPT, "text/json")
                .build();
    }
}
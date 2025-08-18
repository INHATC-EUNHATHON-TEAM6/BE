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

    @Bean
    public WebClient dicWebClient(WebClient.Builder builder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    // Jackson ObjectMapper 인스턴스 생성
                    ObjectMapper om = new ObjectMapper();

                    // "text/json" 미디어 타입을 명시적으로 정의
                    MediaType textJson = new MediaType("text", "json");

                    // 디코더: 서버로부터 받은 응답을 객체로 변환
                    // text/json과 application/json을 모두 허용하도록 설정
                    c.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(om, textJson, MediaType.APPLICATION_JSON)
                    );

                    // 인코더: 클라이언트가 서버로 보낼 요청 본문을 변환
                    // text/json과 application/json을 모두 허용하도록 설정
                    c.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(om, textJson, MediaType.APPLICATION_JSON)
                    );
                })
                .build();


        return builder
                .baseUrl(baseUrl) // properties 파일의 baseUrl 사용
                .exchangeStrategies(strategies)
                // Accept 헤더에 text/json만 추가하여 JSON만 받도록 유도
                .defaultHeader(HttpHeaders.ACCEPT, "application/json,text/json")
                .build();
    }
}
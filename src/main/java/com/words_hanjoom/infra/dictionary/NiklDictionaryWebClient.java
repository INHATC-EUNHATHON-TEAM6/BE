package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

//@Profile("stub")
@Component
@RequiredArgsConstructor
public class NiklDictionaryWebClient implements NiklDictionaryClient {

    @Qualifier("dicWebClient")
    private final WebClient niklWebClient;

    @Value("${nikl.api.key}")
    private String apiKey;

    @Override
    public Mono<SearchResponse> search(SearchRequest req) {
        System.out.println(">>> Search called!");
        return niklWebClient.get()
                .uri(uri -> uri.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", req.getQ())
                        .queryParam("req_type", "json")
                        .build())
                //.retrieve()
                //.bodyToMono(SearchResponse.class);
                .exchangeToMono(resp -> {
                    System.out.println(">>> Response Content-Type = " + resp.headers().contentType());
                    return resp.bodyToMono(SearchResponse.class);
                });
    }

    @Override
    public Mono<ViewResponse> view(long targetCode) {
        return niklWebClient.get()
                .uri(uri -> uri.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("target_code", targetCode)
                        .queryParam("req_type", "json")
                        .build())
                //.retrieve()
                //.bodyToMono(ViewResponse.class);
                .exchangeToMono(resp -> {
                    System.out.println(">>> Response Content-Type = " + resp.headers().contentType());
                    return resp.bodyToMono(ViewResponse.class);
                });
    }

    @Override
    public Optional<String> findLemma(String surface) {
        throw new UnsupportedOperationException("findLemma not implemented yet");
    }

    @Override
    public Optional<Lexeme> lookup(String lemma) {
        throw new UnsupportedOperationException("lookup not implemented yet");
    }
}
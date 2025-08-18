package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
public class DictionaryService {

    private final WebClient dicWebClient;
    private final String apiKey;

    public DictionaryService(
            @Qualifier("dicWebClient") WebClient dicWebClient,
            @Value("${nikl.api.key}") String apiKey
    ) {
        this.dicWebClient = dicWebClient;
        this.apiKey = apiKey;
    }

    public Mono<SearchResponse> search(String q) {
        return dicWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", q)
                        .queryParam("req_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(SearchResponse.class);
    }

    public Mono<ViewResponse> view(String targetCode) {
        return dicWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("target_code", targetCode)
                        .queryParam("req_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(ViewResponse.class);
    }

    public Mono<List<ViewResponse.Item>> searchWithDetail(SearchRequest req) {
        return search(req.getQ())
                .flatMapMany(sr -> Flux.fromIterable(
                        Optional.ofNullable(sr.getChannel().getItem()).orElse(List.of())
                ))
                .map(item -> String.valueOf(item.getTargetCode()))
                .flatMap(this::view)
                .flatMap(vr -> Flux.fromIterable(
                        Optional.ofNullable(vr.getChannel().getItem()).orElse(List.of())
                ))
                .collectList();
    }
}
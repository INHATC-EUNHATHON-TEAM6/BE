package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final NiklDictionaryClient client;

    public Mono<SearchResponse> searchOnly(SearchRequest req) {
        return client.search(req);
    }

    public Mono<List<ViewResponse.Item>> searchWithDetail(SearchRequest req) {
        return client.search(req)
                .flatMapMany(sr -> Flux.fromIterable(
                        Optional.ofNullable(sr.getChannel().getItem()).orElse(List.of())
                ))
                .map(SearchResponse.Item::getTargetCode)
                .flatMap(client::view) // target_code별 상세 호출
                .flatMap(vr -> Flux.fromIterable(
                        Optional.ofNullable(vr.getChannel().getItem()).orElse(List.of())
                ))
                .collectList();
    }
}

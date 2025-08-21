package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
// import lombok.RequiredArgsConstructor; // ✅ 이 부분은 삭제하는 것이 좋음
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DictionaryService {

    private final NiklDictionaryClient dictClient;

    // ✅ @RequiredArgsConstructor 미사용 시 수동 생성자
    public DictionaryService(NiklDictionaryClient dictClient) {
        this.dictClient = dictClient;
    }

    public Mono<SearchResponse> search(SearchRequest req) {
        return dictClient.search(req);
    }

    public Mono<List<ViewResponse.Item>> searchWithDetail(SearchRequest req) {
        return dictClient.search(req)
                .flatMapMany(sr -> Flux.fromIterable(Optional.ofNullable(sr)
                        .map(SearchResponse::getChannel)
                        .map(SearchResponse.Channel::getItem)
                        .orElseGet(Collections::emptyList)))
                .map(SearchResponse.Item::getTargetCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(tc -> {
                    try {
                        long code = Long.parseLong(tc);
                        return dictClient.view(code);
                    } catch (NumberFormatException e) {
                        return Mono.empty();
                    }
                })
                .flatMap(vr -> Flux.fromIterable(Optional.ofNullable(vr)
                        .map(ViewResponse::getChannel)
                        .map(ViewResponse.Channel::getItem)
                        .orElseGet(Collections::emptyList)))
                .collectList();
    }

    public Mono<ViewResponse> view(long targetCode) {
        return dictClient.view(targetCode);
    }
}

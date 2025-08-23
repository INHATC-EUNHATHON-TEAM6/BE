package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NiklDictionaryClient {
    Mono<SearchResponse> search(SearchRequest req);
    Mono<ViewResponse> view(long targetCode);

    Mono<DictEntry> enrichFromView(DictEntry base);

    // 단어 1개에 대한 빠른 조회 및 정제
    Mono<DictEntry> quickLookup(String q);

    // ★ 동일 표제어의 모든 뜻(가능한 범위) 반환
    reactor.core.publisher.Mono<java.util.List<com.words_hanjoom.domain.wordbooks.dto.response.DictEntry>>
    lookupAllSenses(String surface);
}
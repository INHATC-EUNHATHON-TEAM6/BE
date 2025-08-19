package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import reactor.core.publisher.Mono;

public interface NiklDictionaryClient {
    Mono<SearchResponse> search(SearchRequest req);
    Mono<ViewResponse> view(long targetCode);
    Mono<DictEntry> quickLookup(String q); // ✅ 추가: 단어 1개에 대한 빠른 조회 및 정제
}
package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import reactor.core.publisher.Mono;

public interface NiklDictionaryClient {
    Mono<SearchResponse> search(SearchRequest req);
    Mono<ViewResponse>   view(long targetCode);
    Mono<DictEntry>      quickLookup(String q); // exact → include 폴백, 첫 항목만 정제
}

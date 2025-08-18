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

    private final NiklDictionaryClient dictClient;

    public Mono<SearchResponse> search(SearchRequest req) {
        return dictClient.search(req);
    }

    public Mono<ViewResponse> view(long targetCode) {
        return dictClient.view(targetCode);
    }

    /** search → 각 item의 target_code로 view까지 합쳐 상세 리스트 반환 */
    public Mono<List<ViewResponse.Item>> searchWithDetail(SearchRequest req) {
        return dictClient.search(req)
                .flatMapMany(sr -> Flux.fromIterable(
                        sr.getChannel() == null || sr.getChannel().getItem() == null
                                ? List.<SearchResponse.Item>of()
                                : sr.getChannel().getItem()
                ))
                .map(SearchResponse.Item::getTargetCode)
                .flatMap(dictClient::view)
                .flatMap(vr -> Flux.fromIterable(
                        vr.getChannel() == null || vr.getChannel().getItem() == null
                                ? List.<ViewResponse.Item>of()
                                : vr.getChannel().getItem()
                ))
                .collectList();
    }
}
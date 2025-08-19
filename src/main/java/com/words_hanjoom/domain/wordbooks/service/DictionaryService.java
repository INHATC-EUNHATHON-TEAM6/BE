package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final NiklDictionaryClient dictClient;

    public Mono<SearchResponse> search(SearchRequest req) {
        return dictClient.search(req);
    }

    /**
     * search → 각 item.target_code 로 view 호출 → 상세 item 리스트
     */
    public Mono<List<ViewResponse.Item>> searchWithDetail(SearchRequest req) {
        return dictClient.search(req)
                .flatMapMany(sr ->
                        Flux.fromIterable(
                                Optional.ofNullable(sr)
                                        .map(SearchResponse::getChannel)
                                        .map(SearchResponse.Channel::getItem)
                                        .orElseGet(Collections::emptyList)
                        )
                )
                .map(SearchResponse.Item::getTargetCode)       // String ("404765")
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(tc -> {
                    try {
                        long code = Long.parseLong(tc);
                        return dictClient.view(code);          // Mono<ViewResponse>
                    } catch (NumberFormatException e) {
                        return Mono.empty();                   // 숫자 아님 → 스킵
                    }
                })
                .flatMap(vr ->
                        Flux.fromIterable(
                                Optional.ofNullable(vr)
                                        .map(ViewResponse::getChannel)
                                        .map(ViewResponse.Channel::getItem)
                                        .orElseGet(Collections::emptyList)
                        )
                )
                .collectList();                                 // Mono<List<ViewResponse.Item>>
    }

    /**
     * 컨트롤러 /view 에서 쓰는 단건 상세
     */
    public Mono<ViewResponse> view(long targetCode) {
        return dictClient.view(targetCode);
    }

    // (선택) String 받아도 되게 오버로드
    public Mono<ViewResponse> view(String targetCode) {
        try {
            return view(Long.parseLong(targetCode));
        } catch (NumberFormatException e) {
            return Mono.error(new IllegalArgumentException("invalid target_code: " + targetCode));
        }
    }
}

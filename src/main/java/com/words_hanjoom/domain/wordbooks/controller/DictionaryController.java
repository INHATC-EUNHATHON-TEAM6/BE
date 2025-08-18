package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/wordbooks/dict")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService service;

    @GetMapping("/search")
    public Mono<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "false") boolean detail,
            @RequestParam(defaultValue = "json") String req_type,
            @RequestParam(defaultValue = "1") int start,
            @RequestParam(defaultValue = "10") int num,
            @RequestParam(defaultValue = "n") String advanced
    ) {
        if (detail) {
            var req = new SearchRequest(
                    q, req_type, start, num, advanced,
                    Optional.empty(), // target
                    Optional.empty(), // method
                    Optional.empty(), // type1
                    Optional.empty(), // type2
                    Optional.empty(), // pos
                    Optional.empty(), // cat
                    Optional.empty(), // multimedia
                    Optional.empty(), // letterS
                    Optional.empty(), // letterE
                    Optional.empty(), // updateS
                    Optional.empty()  // updateE
            );
            // 상세모드: 검색 후 각 item의 상세 조회까지
            return service.searchWithDetail(req);
        }

        // 단순 검색(q만 사용)
        return service.search(q);
    }
}

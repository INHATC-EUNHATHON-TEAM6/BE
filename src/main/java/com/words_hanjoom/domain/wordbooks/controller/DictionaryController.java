package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.domain.wordbooks.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/wordbooks/dict")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService service;  // ← 서비스만 주입

    @GetMapping("/search")
    public Mono<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "json") String req_type,
            @RequestParam(defaultValue = "1") int start,
            @RequestParam(defaultValue = "10") int num,
            @RequestParam(defaultValue = "y") String advanced,
            @RequestParam(defaultValue = "exact") String method,
            @RequestParam(defaultValue = "false") boolean detail
    ) {
        var req = new SearchRequest(
                q, req_type, start, num, advanced,
                Optional.of(method), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty()
        );

        return detail ? service.searchWithDetail(req) : service.search(req);
    }

    @GetMapping("/view")
    public Mono<ViewResponse> view(@RequestParam("target_code") long targetCode) {
        return service.view(targetCode);
    }
}
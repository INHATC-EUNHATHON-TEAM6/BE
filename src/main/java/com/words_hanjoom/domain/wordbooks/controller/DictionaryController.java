package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

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

        return detail ? service.searchWithDetail(req)
                : service.searchOnly(req);
    }
}

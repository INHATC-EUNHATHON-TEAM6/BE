package com.words_hanjoom.domain.crawling.controller;

import com.words_hanjoom.domain.crawling.dto.request.CrawlRequest;
import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.service.CrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping
    public ResponseEntity<CrawlResult> crawl(@RequestBody CrawlRequest request) {
        CrawlResult result = crawlService.crawl(request);
        return ResponseEntity.ok(result);
    }
}

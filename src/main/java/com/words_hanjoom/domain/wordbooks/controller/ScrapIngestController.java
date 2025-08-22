package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.service.ScrapIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scraps")
@RequiredArgsConstructor
public class ScrapIngestController {
    private final ScrapIngestService ingestService;

    @PostMapping("/import-unknown-words")
    public Map<String, Integer> run() {
        return ingestService.importUnknownWordsFromScraps();
    }
}
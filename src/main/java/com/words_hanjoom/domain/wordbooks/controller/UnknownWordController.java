package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.service.ScrapIngestService;
import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class UnknownWordController {

    private final UnknownWordService unknownWordService;
    private final ScrapIngestService scrapIngestService;

    /**
     * JSON 배열: ["실수","사과","반성"]
     */
    @PostMapping(
            value = "/save",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UnknownWordService.Result> saveWord(
            @RequestParam Long userId,
            @RequestBody List<String> tokens
    ) {
        if (tokens == null || tokens.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(unknownWordService.saveAll(userId, tokens));
    }

    /**
     * CSV 문자열: "실수, 사과, 반성"
     */
    @PostMapping(
            value = "/save-csv",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UnknownWordService.Result> saveWordCsv(
            @RequestParam Long userId,
            @RequestBody String csv
    ) {
        return ResponseEntity.ok(unknownWordService.processCsv(userId, csv));
    }
}
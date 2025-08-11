package com.words_hanjoom.api;

import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wordbook")
@RequiredArgsConstructor
public class WordbookController {

    private final UnknownWordService service;

    /** CSV 직접 입력 */
    @PostMapping("/unknown-words")
    public ResponseEntity<UnknownWordService.Result> addUnknownWords(@RequestBody CsvReq req) {
        var result = service.processCsv(req.getUserId(), req.getUnknownWords());
        return ResponseEntity.ok(result);
    }

    /** answer_comparisons 기반 처리 */
    @PostMapping("/unknown-words/by-comparison/{comparisonId}")
    public ResponseEntity<UnknownWordService.Result> addUnknownWordsFromComparison(@PathVariable Long comparisonId) {
        var result = service.processFromComparison(comparisonId);
        return ResponseEntity.ok(result);
    }

    @Data
    public static class CsvReq {
        @NotNull private Long userId;
        @NotBlank private String unknownWords; // 예: "변형된, 형벌, 독점"
    }
}
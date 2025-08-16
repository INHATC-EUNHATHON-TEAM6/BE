package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자가 입력한 모르는 어휘를 단어장에 저장.
 * - CSV 직접 입력
 * - answer_comparisons에서 UNKNOWN_WORD 읽어 저장
 */
@RestController
@RequestMapping("/api/wordbook")
@RequiredArgsConstructor
@Validated
public class UnknownWordController {

    private final UnknownWordService unknownWordService;

    /** CSV 직접 처리 */
    @PostMapping("/{userId}/unknown-words")
    public ResponseEntity<UnknownWordService.Result> saveFromCsv(
            @PathVariable Long userId,
            @Valid @RequestBody SaveCsvRequest req
    ) {
        UnknownWordService.Result result = unknownWordService.processCsv(userId, req.getCsv());
        return ResponseEntity.ok(result);
    }

    /** answer_comparisons에서 UNKNOWN_WORD 읽어 처리 */
    @PostMapping("/unknown-words/from-comparison/{comparisonId}")
    public ResponseEntity<UnknownWordService.Result> saveFromComparison(@PathVariable Long comparisonId) {
        UnknownWordService.Result result = unknownWordService.processFromComparison(comparisonId);
        return ResponseEntity.ok(result);
    }

    // ===== DTO =====
    @Getter @Setter
    public static class SaveCsvRequest {
        /** 예: "빈곤하다, 형벌, 독점, 법치주의" */
        @NotBlank(message = "csv는 비어있을 수 없습니다.")
        private String csv;
    }
}

package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/words")
@RequiredArgsConstructor
public class UnknownWordController {

    private final UnknownWordService unknownWordService;

    // 단어 저장 테스트
    @PostMapping("/save")
    public ResponseEntity<String> saveWord(
            @RequestParam Long userId,
            @RequestBody List<String> tokens
    ) {
        unknownWordService.saveAll(userId, tokens);
        return ResponseEntity.ok("Saved successfully");
    }
}
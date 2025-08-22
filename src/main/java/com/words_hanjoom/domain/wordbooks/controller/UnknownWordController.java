package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.wordbooks.dto.response.WordDto;
import com.words_hanjoom.domain.wordbooks.entity.Word;
import com.words_hanjoom.domain.wordbooks.repository.WordRepository;
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
    private final WordRepository wordRepository;

    // 단건 조회: /api/words/{id}
    @GetMapping("/{id}")
    public WordDto getWord(@PathVariable Long id) {
        Word w = wordRepository.findById(id).orElseThrow();
        return WordDto.from(w);
    }

    // 전체 목록: /api/words
    @GetMapping
    public List<WordDto> list() {
        return wordRepository.findAll()
                .stream()
                .map(WordDto::from)
                .toList();
    }

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
            @RequestBody List<String> tokens,
            @RequestParam(required = false, defaultValue = "") String context
    ) {
        if (tokens == null || tokens.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(unknownWordService.saveAll(userId, tokens, context));
    }

}
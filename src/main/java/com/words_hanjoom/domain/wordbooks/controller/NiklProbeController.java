package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.infra.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/nikl")
@RequiredArgsConstructor
public class NiklProbeController {

    private final NiklDictionaryClient client;

    /** 예: GET /api/dev/nikl/probe?q=사과 */
    @GetMapping("/probe")
    public ResponseEntity<Map<String, Object>> probe(@RequestParam String q) {
        var opt = client.lookup(q);

        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "hit", false,
                    "word", q
            ));
        }

        var lx = opt.get(); // 레코드 스타일 액세서 사용
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hit", true);
        body.put("word", q);
        body.put("definition", lx.definition());
        body.put("categories", lx.categories());
        body.put("shoulderNo", lx.shoulderNo());
        body.put("synonyms", lx.synonyms());   // 검색 API만 쓰면 빈 리스트일 수 있음
        body.put("antonyms", lx.antonyms());   // 검색 API만 쓰면 빈 리스트일 수 있음
        body.put("example", lx.example());     // 검색 API만 쓰면 빈 문자열일 수 있음
        return ResponseEntity.ok(body);
    }

    @GetMapping("/probe-raw")
    public ResponseEntity<Map<String, Object>> probeRaw(@RequestParam String q) {
        String raw = client.searchRaw(q);
        return ResponseEntity.ok(Map.of("raw", raw));
    }
}
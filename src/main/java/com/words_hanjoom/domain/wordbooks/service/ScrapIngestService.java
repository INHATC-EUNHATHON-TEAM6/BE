package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.ScrapActivity;
import com.words_hanjoom.domain.wordbooks.repository.ScrapActivityRepository;
import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScrapIngestService {

    private final ScrapActivityRepository scrapRepo;
    private final UnknownWordService unknownWordService;
    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 처리 안 된 UNKNOWN_WORD만 가져와서 단어 저장 */
    @Transactional
    public Map<String, Integer> importUnknownWordsFromScraps() {
        var scraps = scrapRepo.findUnknownsNative("UNKNOWN_WORD");

        int total = scraps.size();
        int saved = 0, failed = 0;

        for (ScrapActivity s : scraps) {
            try {
                var result = unknownWordService.processCsv(s.getUserId(), s.getUserAnswer());
                saved += result.words().size();
                // 마킹/업데이트 없음 (ai_*는 건드리지 않음)
            } catch (Exception e) {
                failed++;
            }
        }

        Map<String, Integer> out = new HashMap<>();
        out.put("rows", total);
        out.put("saved", saved);
        out.put("failed", failed);
        return out;
    }

    /** 스크랩의 userAnswer가 JSON 배열이면 안전하게 파싱, 아니면 원문 반환 */
    private String normalizePayload(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            try {
                java.util.List<String> arr = om.readValue(s,
                        om.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
                return String.join(",", arr); // UnknownWordService는 CSV/구분자로 토크나이즈함
            } catch (Exception ignore) { /* fall through */ }
        }
        return s;
    }

    private int countTokens(String csv) {
        if (csv == null || csv.isBlank()) return 0;
        // UnknownWordService와 동일한 구분자와 괄호제거 규칙을 간단히 근사
        String noParen = csv.replaceAll("[(\\[].*?[)\\]]", "");
        String[] toks = noParen.split("[,，;；/／·・|]+");
        int n = 0;
        for (String t : toks) if (!t.replaceAll("\\s+","").isBlank()) n++;
        return n;
    }
}
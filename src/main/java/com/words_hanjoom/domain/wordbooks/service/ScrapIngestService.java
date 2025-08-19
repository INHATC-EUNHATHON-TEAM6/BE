package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.ScrapActivity;
import com.words_hanjoom.domain.wordbooks.repository.ScrapActivityRepository;
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

    @Transactional
    public Map<String, Integer> importUnknownWordsFromScraps() {
        var scraps = scrapRepo.findUnknownsNative("UNKNOWN_WORD");

        int total = scraps.size();
        int ok = 0, fail = 0;

        for (ScrapActivity s : scraps) {
            try {
                var result = unknownWordService.processCsv(s.getUserId(), s.getUserAnswer());
                ok += result.words().size(); // 저장된 단어 수 누적
            } catch (Exception e) {
                fail++;
            }
        }

        Map<String, Integer> out = new HashMap<>();
        out.put("total", total);
        out.put("ok", ok);
        out.put("fail", fail);
        return out;
    }
}
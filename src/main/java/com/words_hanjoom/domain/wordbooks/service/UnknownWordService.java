package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.domain.wordbooks.service.WordImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnknownWordService {

    private final WordbookRepository wordbookRepository;
    private final ScrapActivityRepository scrapRepo;
    private final WordImportService wordImportService; // ← per-word 저장 서비스

    private static final Pattern TOKEN_SEP   = Pattern.compile("[,，;；/／·・|]+");
    private static final Pattern TOKEN_PAREN = Pattern.compile("[(\\[].*?[)\\]]");

    @Transactional
    public Result saveAll(Long userId, List<String> tokens) {
        Wordbook wordbook = wordbookRepository.getOrCreateEntity(userId);
        List<SavedWord> saved = new ArrayList<>();

        for (String raw : tokens) {
            // ⬇️ Wordbook 대신 userId 전달
            wordImportService.saveOne(userId, raw)
                    .ifPresent(saved::add);
        }

        return new Result(wordbook.getWordbookId(), saved);
    }

    @Transactional
    public Result processCsv(Long userId, String csv) {
        return saveAll(userId, tokenizeUnknownWords(csv));
    }

    @Transactional
    public Result processFromComparison(Long comparisonId) {
        ScrapActivity ac = scrapRepo.findById(comparisonId)
                .orElseThrow(() -> new NoSuchElementException("comparison not found: " + comparisonId));
        if (ac.getComparisonType() != ScrapActivity.ComparisonType.UNKNOWN_WORD) {
            throw new IllegalArgumentException("comparison_type must be UNKNOWN_WORD");
        }
        return saveAll(ac.getUserId(), tokenizeUnknownWords(ac.getUserAnswer()));
    }

    private List<String> tokenizeUnknownWords(String text) {
        if (text == null || text.isBlank()) return List.of();
        String noParen = TOKEN_PAREN.matcher(text).replaceAll("");
        return Arrays.stream(TOKEN_SEP.split(noParen))
                .map(s -> s.replaceAll("\\s+", ""))
                .filter(s -> !s.isBlank())
                .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKC))
                .distinct()
                .collect(Collectors.toList());
    }

    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}
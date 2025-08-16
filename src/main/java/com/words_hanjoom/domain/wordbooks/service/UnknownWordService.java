package com.words_hanjoom.domain.wordbooks.service;

// words는 거의 전 컬럼이 NOT NULL이라, 국어원에서 상세 정보를 안 쓰더라도 기본값을 채워서 저장한다.
import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.AnswerComparisonRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookWordRepository;
import com.words_hanjoom.infra.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnknownWordService {

    private final WordRepository wordRepository;
    private final WordbookRepository wordbookRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final AnswerComparisonRepository comparisonRepository;
    private final NiklDictionaryClient dictClient;

    /** CSV 직접 처리 */
    @Transactional
    public Result processCsv(Long userId, String csv) {
        List<String> tokens = parseCsv(csv);
        return saveAll(userId, tokens);
    }

    /** answer_comparisons에서 UNKNOWN_WORD 읽어 처리 */
    @Transactional
    public Result processFromComparison(Long comparisonId) {
        AnswerComparison ac = comparisonRepository.findById(comparisonId)
                .orElseThrow(() -> new NoSuchElementException("comparison not found: " + comparisonId));
        if (ac.getComparisonType() != AnswerComparison.ComparisonType.UNKNOWN_WORD) {
            throw new IllegalArgumentException("comparison_type must be UNKNOWN_WORD");
        }
        List<String> tokens = parseCsv(ac.getUserAnswer());
        return saveAll(ac.getUserId(), tokens);
    }

    // ===== 내부 저장 파이프라인 =====
    private Result saveAll(Long userId, List<String> tokens) {
        Wordbook wordbook = wordbookRepository.findByUserId(userId)
                .orElseGet(() -> wordbookRepository.save(Wordbook.builder().userId(userId).build()));

        List<SavedWord> saved = new ArrayList<>();
        for (String token : tokens) {
            String surface = normalizeKo(token);
            String lemma = dictClient.findLemma(surface).orElse(surface);

            Word word = wordRepository.findByWordName(lemma)
                    .orElseGet(() -> wordRepository.save(
                            Word.builder()
                                    .wordName(lemma)
                                    .synonym("")
                                    .antonym("")
                                    .definition("")
                                    .wordCategory("UNKNOWN")
                                    .shoulderNo((byte) 0)
                                    .example("")
                                    .build()
                    ));

            WordbookWordId id = new WordbookWordId(wordbook.getWordbookId(), word.getWordId());
            if (!wordbookWordRepository.existsById(id)) {
                wordbookWordRepository.save(WordbookWord.builder().id(id).build());
            }
            saved.add(new SavedWord(surface, lemma, word.getWordId()));
        }
        return new Result(wordbook.getWordbookId(), saved);
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }

    // ===== DTO =====
    public record SavedWord(String surface, String wordName, Integer wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}
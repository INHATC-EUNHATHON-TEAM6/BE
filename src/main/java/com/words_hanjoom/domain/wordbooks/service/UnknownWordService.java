package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnknownWordService {

    private static final int LEN_WORD_NAME = 100;
    private static final int LEN_SYNONYM   = 100;
    private static final int LEN_ANTONYM   = 100;
    private static final int LEN_CATEGORY  = 30;
    private static final int LEN_EXAMPLE   = 2000;
    private static final int LEN_DEF       = 4000;

    private final WordRepository wordRepository;
    private final WordbookRepository wordbookRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final AnswerComparisonRepository comparisonRepository;
    private final NiklDictionaryClient dictClient;

    @Transactional
    public Result processCsv(Long userId, String csv) {
        return saveAll(userId, parseCsv(csv));
    }

    @Transactional
    public Result processFromComparison(Long comparisonId) {
        ScrapActivity ac = comparisonRepository.findById(comparisonId)
                .orElseThrow(() -> new NoSuchElementException("comparison not found: " + comparisonId));
        if (ac.getComparisonType() != ScrapActivity.ComparisonType.UNKNOWN_WORD) {
            throw new IllegalArgumentException("comparison_type must be UNKNOWN_WORD");
        }
        return saveAll(ac.getUserId(), parseCsv(ac.getUserAnswer()));
    }

    // ===== 내부 파이프라인 =====
    private Result saveAll(Long userId, List<String> tokens) {
        // B안: 엔티티 get-or-create
        Wordbook wordbook = wordbookRepository.getOrCreateEntity(userId);

        List<SavedWord> saved = new ArrayList<>();

        for (String raw : tokens) {
            final String surface  = normalizeKo(raw);
            final String lemma    = dictClient.findLemma(surface).orElse(surface);
            final String wordName = cut(lemma, LEN_WORD_NAME);

            var lexOpt       = dictClient.lookup(wordName); // 네 클라의 필드명에 맞춰 사용
            var synonyms     = lexOpt.map(NiklDictionaryClient.Lexeme::synonyms).orElseGet(List::of);
            var antonyms     = lexOpt.map(NiklDictionaryClient.Lexeme::antonyms).orElseGet(List::of);
            var definition   = cut(lexOpt.map(NiklDictionaryClient.Lexeme::definition).orElse(""), LEN_DEF);
            var categories   = lexOpt.map(NiklDictionaryClient.Lexeme::categories).orElseGet(List::of);
            var example      = cut(lexOpt.map(NiklDictionaryClient.Lexeme::example).orElse(""), LEN_EXAMPLE);
            var shoulderNo   = lexOpt.map(NiklDictionaryClient.Lexeme::shoulderNo).orElse((byte) 0);

            final String synonymStr = cut(joinAndLimitEach(dedup(synonyms), ", ", LEN_SYNONYM), LEN_SYNONYM);
            final String antonymStr = cut(joinAndLimitEach(dedup(antonyms), ", ", LEN_ANTONYM), LEN_ANTONYM);
            final String categoryStr= cut(joinAndLimitEach(dedup(categories), ", ", LEN_CATEGORY), LEN_CATEGORY);

            // words upsert/find
            Word word = wordRepository.findByWordName(wordName).orElseGet(() -> {
                try {
                    return wordRepository.save(Word.builder()
                            .wordName(wordName)
                            .synonym(synonymStr)
                            .antonym(antonymStr)
                            .definition(definition)
                            .wordCategory(categoryStr)
                            .shoulderNo(shoulderNo)
                            .example(example)
                            .build());
                } catch (DataIntegrityViolationException e) {
                    return wordRepository.findByWordName(wordName).orElseThrow(() -> e);
                }
            });

            boolean needUpdate = false;
            if (!Objects.equals(safe(word.getSynonym()), synonymStr))      { word.setSynonym(synonymStr); needUpdate = true; }
            if (!Objects.equals(safe(word.getAntonym()), antonymStr))       { word.setAntonym(antonymStr); needUpdate = true; }
            if (!Objects.equals(safe(word.getDefinition()), definition))    { word.setDefinition(definition); needUpdate = true; }
            if (!Objects.equals(safe(word.getWordCategory()), categoryStr)) { word.setWordCategory(categoryStr); needUpdate = true; }
            if (!Objects.equals(safe(word.getExample()), example))          { word.setExample(example); needUpdate = true; }
            if (!Objects.equals(word.getShoulderNo(), shoulderNo))          { word.setShoulderNo(shoulderNo); needUpdate = true; }
            if (needUpdate) wordRepository.save(word);

            // 단어장-단어 매핑
            WordbookWordId id = new WordbookWordId(wordbook.getWordbookId(), word.getWordId());
            if (!wordbookWordRepository.existsById(id)) {
                wordbookWordRepository.save(WordbookWord.builder().id(id).build());
            }

            saved.add(new SavedWord(surface, wordName, word.getWordId()));
        }

        return new Result(wordbook.getWordbookId(), saved);
    }

    // ===== 유틸 =====
    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("[,，;；/／·・\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
    }
    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private String cut(String s, int max) { return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max)); }
    private String joinAndLimitEach(List<String> list, String delim, int eachMax) {
        if (list == null || list.isEmpty()) return "";
        return list.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.length() > eachMax ? v.substring(0, eachMax) : v)
                .collect(Collectors.joining(delim));
    }
    private <T> List<T> dedup(List<T> in) { return (in == null || in.isEmpty()) ? List.of() : new ArrayList<>(new LinkedHashSet<>(in)); }

    // DTO
    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}

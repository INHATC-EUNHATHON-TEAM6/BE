package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnknownWordService {

    private final WordbookRepository wordbookRepository;
    private final WordRepository wordRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final NiklDictionaryClient dictClient;

    private static final int LEN_WORD_NAME = 100;
    private static final int LEN_DEF = 4000;
    private static final int LEN_EXAMPLE = 2000;
    private static final String DELIM = ",";
    private static final Pattern TOKEN_SEP = Pattern.compile("[,，;；/／·・|]+");
    private static final Pattern TOKEN_PAREN = Pattern.compile("[(\\[].*?[)\\]]");

    @Transactional
    public Result processCsv(Long userId, String csv) {
        return saveAll(userId, tokenizeUnknownWords(csv));
    }

    @Transactional
    public Result saveAll(Long userId, List<String> tokens) {
        Wordbook wordbook = wordbookRepository.getOrCreateEntity(userId);
        List<SavedWord> saved = new ArrayList<>();

        for (String raw : tokens) {
            saveOne(userId, raw).ifPresent(saved::add);
        }

        return new Result(wordbook.getWordbookId(), saved);
    }

    // ★ 단어 1개만 새 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SavedWord> saveOne(Long userId, String raw) {
        // ✅ 수정: API 호출 전에 입력 값이 유효한지 확인
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        final String surface = normalizeKo(raw);
        DictEntry entry = dictClient.quickLookup(surface).blockOptional().orElse(null);
        if (entry == null) {
            return Optional.empty(); // 사전 미히트 → 스킵
        }

        final String wordName = cut(entry.getLemma(), LEN_WORD_NAME);
        final String definition = cut(entry.getDefinition(), LEN_DEF);
        final String example = cut(entry.getExample(), LEN_EXAMPLE);

        // words upsert/find
        Word word = wordRepository.findByWordName(wordName).orElseGet(() -> {
            try {
                return wordRepository.save(Word.builder()
                        .wordName(wordName)
                        .definition(definition == null ? "" : definition)
                        .wordCategory(entry.getFieldType())
                        .shoulderNo(entry.getShoulderNo())
                        .example(example == null ? "" : example)
                        .synonym("")
                        .antonym("")
                        .build());
            } catch (DataIntegrityViolationException e) {
                return wordRepository.findByWordName(wordName).orElseThrow(() -> e);
            }
        });

        // 단어장-단어 매핑
        WordbookWordId id = new WordbookWordId(wordbookRepository.getOrCreateEntity(userId).getWordbookId(), word.getWordId());
        if (!wordbookWordRepository.existsById(id)) {
            wordbookWordRepository.save(WordbookWord.builder().id(id).build());
        }

        return Optional.of(new SavedWord(surface, wordName, word.getWordId()));
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

    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        trimmed = trimmed.replaceAll("[(\\[].*?[)\\]]", "");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }
    private String cut(String s, int max) { return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max)); }

    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}
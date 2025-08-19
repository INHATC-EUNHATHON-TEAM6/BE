package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordSaveTxService {

    private static final int LEN_WORD_NAME = 100;
    private static final int LEN_SYNONYM   = 100;
    private static final int LEN_ANTONYM   = 100;
    private static final int LEN_CATEGORY  = 30;
    private static final int LEN_EXAMPLE   = 2000;
    private static final int LEN_DEF       = 4000;
    private static final String DELIM = ",";

    private final WordRepository wordRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final NiklDictionaryClient dictClient;

    // ★ 단어 1개만 새 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UnknownWordService.SavedWord> saveOne(Wordbook wordbook, String raw) {
        final String surface  = normalizeKo(raw);
        final String lemma    = dictClient.findLemma(surface).orElse(surface);
        final String wordName = cut(lemma, LEN_WORD_NAME);

        var lexOpt = dictClient.lookup(wordName);
        if (lexOpt.isEmpty()) {
            // 사전 미히트 → 이 단어만 스킵(트랜잭션 커밋 없이 종료)
            return Optional.empty();
        }
        var lex = lexOpt.get();

        var synonyms   = lex.synonyms();     // List<String>
        var antonyms   = lex.antonyms();     // List<String>
        var definition = cut(lex.definition(), LEN_DEF);
        var categories = lex.categories();   // List<String>
        var examples   = lex.examples();     // List<String>
        var shoulderNo = lex.shoulderNo();   // Integer 등

        final long targetCode = lex.targetCode();   // primitive long
        final short senseNo    = lex.senseNo();      // primitive long
        final boolean hasKeys = (targetCode > 0 && senseNo > 0);

        final String synonymStr  = cut(joinAndLimitEach(dedup(synonyms),   DELIM, LEN_SYNONYM), LEN_WORD_NAME);
        final String antonymStr  = cut(joinAndLimitEach(dedup(antonyms),   DELIM, LEN_ANTONYM), LEN_WORD_NAME);
        final String categoryStr = cut(joinAndLimitEach(dedup(categories), DELIM, LEN_CATEGORY), LEN_WORD_NAME);
        final String exampleStr  = cut(joinAndLimitEach(dedup(examples),   DELIM, LEN_EXAMPLE), LEN_EXAMPLE);

        // (target_code, sense_no)가 있으면 그걸로 우선 조회, 없으면 word_name으로 조회
        Optional<Word> existing = hasKeys
                ? wordRepository.findByTargetCodeAndSenseNo(targetCode, senseNo)
                : wordRepository.findByWordName(wordName);


        // 신규 저장 또는 동시성 대비
        Word word = existing.orElseGet(() -> {
            try {
                return wordRepository.save(Word.builder()
                        .wordName(wordName)
                        .synonym(synonymStr)
                        .antonym(antonymStr)
                        .definition(definition)
                        .wordCategory(categoryStr)
                        .shoulderNo(shoulderNo)
                        .example(exampleStr)
                        .targetCode(targetCode)
                        .senseNo(senseNo)
                        .build());
            } catch (DataIntegrityViolationException e) {
                // 동시 삽입으로 인한 유니크 충돌 발생 시 재조회
                return hasKeys
                        ? wordRepository.findByTargetCodeAndSenseNo(targetCode, senseNo).orElseThrow(() -> e)
                        : wordRepository.findByWordName(wordName).orElseThrow(() -> e);
            }
        });

        boolean needUpdate = false;
        if (!Objects.equals(safe(word.getSynonym()),       synonymStr))  { word.setSynonym(synonymStr);       needUpdate = true; }
        if (!Objects.equals(safe(word.getAntonym()),       antonymStr))  { word.setAntonym(antonymStr);       needUpdate = true; }
        if (!Objects.equals(safe(word.getDefinition()),    definition))  { word.setDefinition(definition);    needUpdate = true; }
        if (!Objects.equals(safe(word.getWordCategory()),  categoryStr)) { word.setWordCategory(categoryStr); needUpdate = true; }
        if (!Objects.equals(safe(word.getExample()),       exampleStr))  { word.setExample(exampleStr);       needUpdate = true; }
        if (!Objects.equals(word.getShoulderNo(),          shoulderNo))  { word.setShoulderNo(shoulderNo);   needUpdate = true; }
        if (!Objects.equals(word.getTargetCode(),          targetCode))  { word.setTargetCode(targetCode);    needUpdate = true; }
        if (!Objects.equals(word.getSenseNo(),             senseNo))     { word.setSenseNo(senseNo);          needUpdate = true; }

        if (needUpdate) {
            wordRepository.save(word); // 이 메서드는 REQUIRES_NEW 트랜잭션 안에서 커밋됨
        }

        // 단어장-단어 매핑
        WordbookWordId id = new WordbookWordId(wordbook.getWordbookId(), word.getWordId());
        if (!wordbookWordRepository.existsById(id)) {
            wordbookWordRepository.save(WordbookWord.builder().id(id).build());
        }

        // UnknownWordService.Result 에 넣을 요약 DTO 반환
        return Optional.of(new UnknownWordService.SavedWord(surface, wordName, word.getWordId()));
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private String cut(String s, int max) {
        return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max));
    }


    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        // 괄호와 내부 설명 제거: 배(과일) -> 배
        trimmed = trimmed.replaceAll("[(\\[].*?[)\\]]", "");
        return java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);
    }


    private String joinAndLimitEach(List<String> list, String delim, int eachMax) {
        if (list == null || list.isEmpty()) return "";
        return list.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.length() > eachMax ? v.substring(0, eachMax) : v)
                .collect(Collectors.joining(delim));
    }

    private <T> List<T> dedup(List<T> in) {
        return (in == null || in.isEmpty()) ? List.of() : new ArrayList<>(new LinkedHashSet<>(in));
    }

}

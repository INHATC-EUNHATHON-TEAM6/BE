package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWordId;
import com.words_hanjoom.domain.wordbooks.repository.WordRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookWordRepository;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient.Lexeme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordImportService {

    private static final int LEN_WORD_NAME = 100;
    private static final int LEN_SYNONYM   = 100;
    private static final int LEN_ANTONYM   = 100;
    private static final int LEN_CATEGORY  = 30;
    private static final int LEN_EXAMPLE   = 2000;  // 예문 한 항목 컷
    private static final int LEN_DEF       = 4000;
    private static final String DELIM = ",";

    private final WordRepository wordRepository;
    private final WordbookRepository wordbookRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final NiklDictionaryClient dictClient;

    private static final Pattern TOKEN_SEP   = Pattern.compile("[,，;；/／·・|\\s]+"); // 구분자+공백
    private static final Pattern TOKEN_PAREN = Pattern.compile("[(\\[].*?[)\\]]");   // 괄호/브라켓 안 제거


    /**
     * 단어 1개 저장(사전 조회 → words upsert → 단어장 매핑)
     * - 반드시 다른 Bean에서 호출할 것(그래야 REQUIRES_NEW 적용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UnknownWordService.SavedWord> saveOne(Long userId, String raw) {
        log.info("[IMPORT] raw='{}' -> surface='{}'", raw, normalizeKo(raw));
        try {
            Wordbook wordbook = wordbookRepository.getOrCreateEntity(userId);

            final String surface  = normalizeKo(raw);
            final String lemma    = dictClient.findLemma(surface).orElse(surface);
            String lemmaTmp;
            try {
                lemmaTmp = dictClient.findLemma(surface).orElse(surface);
            } catch (Exception e) {
                log.warn("[IMPORT] findLemma EX surface='{}' : {}", surface, e.toString());
                lemmaTmp = surface; // fallback
            }
            final String wordName = cut(lemmaTmp, LEN_WORD_NAME);

            Optional<Lexeme> lexOpt;
            try {
                lexOpt = dictClient.lookup(wordName);
                if (lexOpt.isEmpty() && !surface.equals(wordName)) {
                    // ★ fallback: 정규화 전 표면형으로 한 번 더
                    lexOpt = dictClient.lookup(surface);
                }
            } catch (Exception e) {
                log.warn("[IMPORT] lookup EX wordName={} msg={}", wordName, e.toString());
                return Optional.empty();
            }
            if (lexOpt.isEmpty()) {
                log.info("[IMPORT] no lexeme for {} (surface:{})", wordName, surface);
                return Optional.empty();
            }
            Lexeme lex = lexOpt.get();

            var synonyms   = lex.synonyms();
            var antonyms   = lex.antonyms();
            var definition = cut(lex.definition(), LEN_DEF);
            var categories = lex.categories();
            var examples   = lex.examples();
            var shoulderNo = lex.shoulderNo();  // byte
            final long  targetCode = lex.targetCode(); // long
            final short senseNo    = lex.senseNo();    // short

            final String synonymStr  = cut(joinAndLimitEach(dedup(synonyms),   DELIM, LEN_SYNONYM), LEN_WORD_NAME);
            final String antonymStr  = cut(joinAndLimitEach(dedup(antonyms),   DELIM, LEN_ANTONYM), LEN_WORD_NAME);
            final String categoryStr = cut(joinAndLimitEach(dedup(categories), DELIM, LEN_CATEGORY), LEN_WORD_NAME);
            final String exampleStr  = cut(joinAndLimitEach(dedup(examples),   DELIM, LEN_EXAMPLE), LEN_EXAMPLE);

            // (target_code, sense_no) 우선 조회, 없으면 단어명으로 조회
            boolean hasKeys = (targetCode > 0 && senseNo > 0);
            Optional<Word> existing = hasKeys
                    ? wordRepository.findByTargetCodeAndSenseNo(targetCode, Short.valueOf(senseNo))
                    : wordRepository.findByWordName(wordName);

            // 신규 생성(동시성 대비)
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
                            .senseNo(Short.valueOf(senseNo))
                            .build());
                } catch (DataIntegrityViolationException e) {
                    return hasKeys
                            ? wordRepository.findByTargetCodeAndSenseNo(targetCode, Short.valueOf(senseNo)).orElseThrow(() -> e)
                            : wordRepository.findByWordName(wordName).orElseThrow(() -> e);
                }
            });

            // 변경점만 업데이트
            boolean needUpdate = false;
            if (!Objects.equals(safe(word.getSynonym()),       synonymStr))  { word.setSynonym(synonymStr);       needUpdate = true; }
            if (!Objects.equals(safe(word.getAntonym()),       antonymStr))  { word.setAntonym(antonymStr);       needUpdate = true; }
            if (!Objects.equals(safe(word.getDefinition()),    definition))  { word.setDefinition(definition);    needUpdate = true; }
            if (!Objects.equals(safe(word.getWordCategory()),  categoryStr)) { word.setWordCategory(categoryStr); needUpdate = true; }
            if (!Objects.equals(safe(word.getExample()),       exampleStr))  { word.setExample(exampleStr);       needUpdate = true; }
            if (!Objects.equals(word.getShoulderNo(),          shoulderNo))  { word.setShoulderNo(shoulderNo);   needUpdate = true; }
            if (!Objects.equals(word.getTargetCode(),          targetCode))  { word.setTargetCode(targetCode);    needUpdate = true; }
            if (!Objects.equals(word.getSenseNo(),   Short.valueOf(senseNo))) { word.setSenseNo(Short.valueOf(senseNo)); needUpdate = true; }

            if (needUpdate) {
                wordRepository.save(word);
            }

            // 단어장-단어 매핑
            WordbookWordId id = new WordbookWordId(wordbook.getWordbookId(), word.getWordId());
            if (!wordbookWordRepository.existsById(id)) {
                wordbookWordRepository.save(WordbookWord.builder().id(id).build());
            }

            log.info("[IMPORT] saved word={} target={} sense={} syn#={} ant#={} ex#={}",
                    wordName, targetCode, senseNo, synonyms.size(), antonyms.size(), examples.size());

            return Optional.of(new UnknownWordService.SavedWord(surface, wordName, word.getWordId()));

        } catch (Exception e) {
            // 어떤 이유든 이 단어만 실패 처리
            log.warn("[IMPORT] saveOne failed raw={} msg={}", raw, e.toString());
            return Optional.empty();
        }
    }

    @Transactional
    public int importBySurface(Long userId, String q) {
        if (q == null || q.isBlank()) return 0;
        int ok = 0;
        for (String raw : tokenize(q)) {
            try {
                if (saveOne(userId, raw).isPresent()) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("[IMPORT] importBySurface token={} err={}", raw, e.toString());
            }
        }
        return ok;
    }

    // ===== 유틸 =====
    private static String safe(String s) { return s == null ? "" : s; }

    private static String cut(String s, int max) {
        return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    private static String normalizeKo(String s) {
        if (s == null) return "";
        String trimmed = s.replaceAll("\\s+", "");
        trimmed = trimmed.replaceAll("[(\\[].*?[)\\]]", ""); // 괄호/브라켓 제거
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }

    private static String joinAndLimitEach(List<String> list, String delim, int eachMax) {
        if (list == null || list.isEmpty()) return "";
        return list.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> v.length() > eachMax ? v.substring(0, eachMax) : v)
                .collect(Collectors.joining(delim));
    }

    private static <T> List<T> dedup(List<T> in) {
        return (in == null || in.isEmpty()) ? List.of() : new ArrayList<>(new LinkedHashSet<>(in));
    }

    private List<String> tokenize(String text) {
        String noParen = TOKEN_PAREN.matcher(text).replaceAll(""); // 예: 배(과일) → 배
        return Arrays.stream(TOKEN_SEP.split(noParen))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}

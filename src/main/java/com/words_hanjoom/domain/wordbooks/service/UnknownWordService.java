package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.AiDictionaryClient;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnknownWordService {

    private final WordbookRepository wordbookRepository;
    private final WordRepository wordRepository;
    private final WordbookWordRepository wordbookWordRepository;
    private final NiklDictionaryClient dictClient;
    private final AiDictionaryClient aiDictionaryClient;

    private static final int LEN_WORD_NAME = 100;
    private static final int LEN_DEF = 4000;
    private static final int LEN_EXAMPLE = 2000;
    private static final String DELIM = ",";
    private static final Pattern TOKEN_SEP = Pattern.compile("[,，;；/／·・|]+");
    private static final Pattern TOKEN_PAREN = Pattern.compile("[(\\[].*?[)\\]]");
    private static final Pattern NUM_CSV = Pattern.compile("^\\d+(,\\d+)*$");

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

    private static Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split("\\s*,\\s*"))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static final int AI_SAVE_RETRIES = 3;

    // 단어 1개만 새 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SavedWord> saveOne(Long userId, String raw) {

        // 0) 입력 값 점검 + 정규화
        if (raw == null || raw.isBlank()) return Optional.empty();
        final String surface = normalizeKo(raw);
        final String plain   = surface.replaceAll("[-·ㆍ‐–—]", "");
        log.debug("[WORD] saveOne userId={} surface='{}' plain='{}'", userId, surface, plain);

        // 1) DB 선조회(동일 표기 또는 유사 표기)
        Optional<Word> existing = wordRepository.findByWordName(surface);
        if (existing.isEmpty()) existing = wordRepository.findLooselyByName(surface, plain);

        if (existing.isPresent()) {
            Word w = existing.get();
            log.info("[WORD] DB HIT (NO API) '{}' → wordId={}", surface, w.getWordId());
            WordbookWordId id = new WordbookWordId(
                    wordbookRepository.getOrCreateEntity(userId).getWordbookId(),
                    w.getWordId());
            if (!wordbookWordRepository.existsById(id)) {
                wordbookWordRepository.save(WordbookWord.builder().id(id).build());
            }
            return Optional.of(new SavedWord(surface, w.getWordName(), w.getWordId()));
        }

        // 2) 사전 조회 (국어원 → 실패 시 OpenAI 폴백)
        log.debug("[WORD] DB MISS surface='{}' – calling DICT API", surface);

        DictEntry entry = null;
        try {
            entry = dictClient.quickLookup(surface).blockOptional().orElse(null);
            if (entry == null) {
                log.info("[WORD] DICT MISS – try OpenAI fallback for '{}'", surface);
                entry = aiDictionaryClient.defineFromAi(surface, null)
                        .onErrorResume(e -> {  // 안전망 한 번 더
                            log.warn("[AI] fallback error for '{}': {}", surface, e.toString());
                            return Mono.empty();
                        })
                        .blockOptional().orElse(null);
            }
        } catch (Exception e) {                // ★ 어떤 예외도 트랜잭션 밖으로 내보내지 않음
            log.warn("[WORD] lookup error for '{}': {}", surface, e.toString());
            entry = null;
        }

        if (entry == null) {
            log.debug("[WORD] DICT/AI MISS for '{}' – skip", surface);
            return Optional.empty();
        }



        // 3) 결과 정리(널/길이/기본값 보정)
        final String wordName   = cut(entry.getLemma(), LEN_WORD_NAME);
        final String definition = cut(entry.getDefinition(), LEN_DEF);
        final String example    = cut(entry.getExample(), LEN_EXAMPLE);
        final String cats       = (entry.getCategories()==null || entry.getCategories().isBlank())
                ? "일반" : entry.getCategories();

        final Long   targetCode = Optional.ofNullable(entry.getTargetCode()).orElse(0L);  // AI면 0

        // AI(entry.targetCode==0)면 sense_no 자동 배정
        Integer senseNo = entry.getSenseNo();
        if (targetCode == 0L) {
            int next = Optional.ofNullable(wordRepository.findMaxAiSenseNoByName(wordName)).orElse(0) + 1;
            senseNo = next; // auto-boxing -> Integer
        }
        final int senseNoSafe = (senseNo == null) ? 0 : senseNo; // 비교/세팅용 primitive

        final java.util.List<String> entrySyns = entry.getSynonyms() == null ? java.util.List.of() : entry.getSynonyms();
        final java.util.List<String> entryAnts = entry.getAntonyms() == null ? java.util.List.of() : entry.getAntonyms();
        final byte shoulderNo = entry.getShoulderNo();

        log.info("[WORD] resolved '{}' → lemma='{}', tc={}, senseNo={}, cats='{}'",
                surface, wordName, targetCode, senseNo, cats);

        // 4) 동일 표제어로 저장(있으면 보정/병합, 없으면 신규)
        Word saved = wordRepository.findByWordName(wordName)
                .map(w -> {
                    boolean dirty = false;

                    // 카테고리 보정
                    String curCat = w.getWordCategory();
                    boolean needCatUpdate =
                            curCat == null || curCat.isBlank()
                                    || "일반어".equals(curCat)
                                    || NUM_CSV.matcher(curCat).matches(); // 패턴 상수: Pattern.compile("^\\d+(,\\d+)*$")

                    if (needCatUpdate && !cats.isBlank() && !cats.equals(curCat)) {
                        w.setWordCategory(cats);
                        dirty = true;
                    }

                    if (w.getTargetCode() == null || w.getTargetCode() == 0L) {
                        w.setTargetCode(targetCode); dirty = true;
                    }
                    if ((w.getDefinition() == null || w.getDefinition().isBlank()) && !definition.isBlank()) {
                        w.setDefinition(definition); dirty = true;
                    }
                    if ((w.getExample() == null || w.getExample().isBlank()) && !example.isBlank()) {
                        w.setExample(example); dirty = true;
                    }
                    if (w.getShoulderNo() == 0 && shoulderNo != 0) {
                        w.setShoulderNo(shoulderNo); dirty = true;
                    }

                    // getSenseNo()가 원시형이면 0만 체크, 객체형이면 null/0 체크
                    if (w.getSenseNo() == null || w.getSenseNo() == 0) {
                        w.setSenseNo(senseNoSafe); // Integer/primitive 모두 OK (auto-boxing)
                    }

                    String mergedSyn = mergeCsv(w.getSynonym(), entrySyns);
                    if (!java.util.Objects.equals(nzCsv(w.getSynonym()), mergedSyn)) {
                        w.setSynonym(mergedSyn); dirty = true;
                    }

                    String mergedAnt = mergeCsv(w.getAntonym(), entryAnts);
                    if (!java.util.Objects.equals(nzCsv(w.getAntonym()), mergedAnt)) {
                        w.setAntonym(mergedAnt); dirty = true;
                    }

                    return dirty ? wordRepository.save(w) : w;
                })
                .orElseGet(() -> wordRepository.save(Word.builder()
                        .wordName(wordName)
                        .definition(definition)
                        .wordCategory(cats)
                        .shoulderNo(shoulderNo)
                        .example(example)
                        .targetCode(targetCode)   // NOT NULL (0 허용)
                        .senseNo(senseNoSafe)        // NOT NULL
                        .synonym(joinList(entrySyns))
                        .antonym(joinList(entryAnts))
                        .build()));

        // 5) 단어장 매핑
        WordbookWordId id = new WordbookWordId(
                wordbookRepository.getOrCreateEntity(userId).getWordbookId(),
                saved.getWordId());
        if (!wordbookWordRepository.existsById(id)) {
            wordbookWordRepository.save(WordbookWord.builder().id(id).build());
        }

        return Optional.of(new SavedWord(surface, saved.getWordName(), saved.getWordId()));
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

    private static String joinList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return ""; // NOT NULL 컬럼 대비
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        trimmed = trimmed.replaceAll("[(\\[].*?[)\\]]", "");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }
    private String cut(String s, int max) { return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max)); }

    private static String nzCsv(String s) { return (s == null) ? "" : s; }

    private static String mergeCsv(String existingCsv, List<String> add) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (existingCsv != null && !existingCsv.isBlank()) {
            for (String t : existingCsv.split("\\s*,\\s*")) if (!t.isBlank()) set.add(t.trim());
        }
        if (add != null) {
            for (String t : add) if (t != null && !t.isBlank()) set.add(t.trim());
        }
        return String.join(", ", set);
    }
    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}
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
    private static final Pattern TOKEN_SEP = Pattern.compile("[,，;；/／·・|]+");
    private static final Pattern TOKEN_PAREN = Pattern.compile("[(\\[].*?[)\\]]");
    private static final Pattern NUM_CSV = Pattern.compile("^\\d+(,\\d+)*$");
    private static final int AI_SAVE_RETRIES = 3;

    // === 맥락 스코어링 파라미터 ===
    private static final int MIN_SCORE = 3;       // 이 미만이면 맥락 부적합
    private static final int MARGIN_TO_NEXT = 2;  // 1,2위 점수차 작으면 보류

    // ====== 엔드포인트 ======

    // 기존(맥락 없음) — 하위 호환 유지
    @Transactional
    public Result processCsv(Long userId, String csv) {
        return saveAll(userId, tokenizeUnknownWords(csv), null);
    }

    @Transactional
    public void importUnknownWords(Long userId, String raw) {
        if (raw == null || raw.isBlank()) return;

        // 기존과 동일한 분리 규칙
        Set<String> tokens = Arrays.stream(raw.split("[,、/\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 핵심: 파이프라인 재사용 (DB 조회 → NIKL → AI 폴백 + 단어장 매핑)
        for (String term : tokens) {
            saveOne(userId, term, null); // 부작용으로 word/wordbook 저장됨
        }
    }

    // 컨트롤러에서 기사 맥락 문자열을 만들어 넘겨줄 때 사용
    @Transactional
    public Result processCsv(Long userId, String csv, String context) {
        return saveAll(userId, tokenizeUnknownWords(csv), context);
    }

    @Transactional
    public Result saveAll(Long userId, List<String> tokens, String context) {
        Wordbook wordbook = wordbookRepository.getOrCreateEntity(userId);
        List<SavedWord> saved = new ArrayList<>();
        for (String raw : tokens) {
            saveOne(userId, raw, context).ifPresent(saved::add);
        }
        return new Result(wordbook.getWordbookId(), saved);
    }

    // 기존 시그니처 유지(맥락 null)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SavedWord> saveOne(Long userId, String raw) {
        return saveOne(userId, raw, null);
    }

    // ★ 핵심: 맥락 기반 DB→NIKL→AI 순차 폴백
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SavedWord> saveOne(Long userId, String raw, String context) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        final String surface = normalizeKo(raw);
        final String plain   = surface.replaceAll("[-·ㆍ‐–—]", "");
        log.debug("[WORD] saveOne userId={} surface='{}' ctx='{}'", userId, surface, context);

        // 1) DB 다중 후보 조회 → 맥락 스코어링 선택
        List<Word> dbCands = new ArrayList<>();
        dbCands.addAll(wordRepository.findAllByWordName(surface));
        dbCands.addAll(wordRepository.findLooselyByNameMulti(surface, plain));

        Optional<Word> bestDb = selectBestDbCandidate(dbCands, context);
        if (bestDb.isPresent()) {
            Word w = bestDb.get();
            log.info("[WORD] DB HIT with context '{}' → wordId={}", surface, w.getWordId());
            mapIntoWordbook(userId, w.getWordId());
            return Optional.of(new SavedWord(surface, w.getWordName(), w.getWordId()));
        }

        // 2) 국립국어원 다의어 목록 → 맥락 스코어링
        List<DictEntry> niklSenses = fetchNiklAllSenses(surface);
        Optional<DictEntry> bestNikl = selectBestEntry(niklSenses, context);
        if (bestNikl.isPresent()) {
            Word saved = saveResolvedEntry(userId, surface, bestNikl.get());
            return Optional.of(new SavedWord(surface, saved.getWordName(), saved.getWordId()));
        }

        // 3) OpenAI 폴백(JSON) — 맥락 전달
        DictEntry ai = fetchAiEntry(surface, context);
        if (ai != null) {
            Word saved = saveResolvedEntry(userId, surface, ai);
            return Optional.of(new SavedWord(surface, saved.getWordName(), saved.getWordId()));
        }

        // 4) 전부 못 고르면 보류
        log.info("[WORD] No suitable sense for '{}' (context={})", surface, context);
        return Optional.empty();
    }

    // ===== 국립국어원/AI 호출 =====
    private List<DictEntry> fetchNiklAllSenses(String surface) {
        try {
            return dictClient.lookupAllSenses(surface)
                    .blockOptional().orElse(List.of());
        } catch (Exception e) {
            log.warn("[DICT] lookupAllSenses error '{}': {}", surface, e.toString());
            return List.of();
        }
    }

    private DictEntry fetchAiEntry(String surface, String context) {
        try {
            return aiDictionaryClient.defineFromAi(surface, context)
                    .onErrorResume(e -> {
                        log.warn("[AI] fallback error '{}': {}", surface, e.toString());
                        return Mono.empty();
                    })
                    .blockOptional().orElse(null);
        } catch (Exception e) {
            log.warn("[AI] error for '{}': {}", surface, e.toString());
            return null;
        }
    }

    // ===== “선택된 뜻”을 저장(기존 병합 로직 재사용) =====
    private Word saveResolvedEntry(Long userId, String surface, DictEntry entry) {
        final String wordName   = cut(entry.getLemma(), LEN_WORD_NAME);
        final String definition = cut(entry.getDefinition(), LEN_DEF);
        final String example    = cut(entry.getExample(), LEN_EXAMPLE);
        final String cats       = (entry.getCategories()==null || entry.getCategories().isBlank()) ? "일반" : entry.getCategories();
        final Long   targetCode = Optional.ofNullable(entry.getTargetCode()).orElse(0L); // AI면 0
        final List<String> syns = entry.getSynonyms()==null? List.of() : entry.getSynonyms();
        final List<String> ants = entry.getAntonyms()==null? List.of() : entry.getAntonyms();
        final byte shoulderNo   = entry.getShoulderNo();

        Integer senseNo = entry.getSenseNo();
        if (targetCode == 0L) {
            senseNo = allocateGlobalAiSenseNo(); // (0, MAX+1)
        }
        final int senseNoSafe = (senseNo == null) ? 0 : senseNo;

        Word saved = wordRepository.findByWordName(wordName)
                .map(w -> mergeIntoExistingWord(w, targetCode, senseNoSafe, definition, example, cats, shoulderNo, syns, ants))
                .orElseGet(() -> saveNewWordWithRetry(
                        Word.builder()
                                .wordName(wordName)
                                .definition(definition)
                                .wordCategory(cats)
                                .shoulderNo(shoulderNo)
                                .example(example)
                                .targetCode(targetCode)
                                .senseNo(senseNoSafe)
                                .synonym(joinList(syns))
                                .antonym(joinList(ants))
                                .build(),
                        targetCode
                ));

        mapIntoWordbook(userId, saved.getWordId());
        log.info("[WORD] saved '{}' → wordId={}, tc={}, sense={}", surface, saved.getWordId(), targetCode, senseNoSafe);
        return saved;
    }

    /** target_code=0(=AI) 전역 sense_no = MAX+1 */
    @Transactional(propagation = Propagation.MANDATORY)
    protected int allocateGlobalAiSenseNo() {
        Integer max = wordRepository.findMaxSenseNoByTargetCode(0L);
        return (max == null ? 0 : max) + 1;
    }

    /** 신규 Word 저장 시, AI 전역 유니크 충돌에 대해 짧게 재시도 */
    private Word saveNewWordWithRetry(Word candidate, Long targetCode) {
        if (!Objects.equals(targetCode, 0L)) {
            return wordRepository.save(candidate);
        }
        for (int attempt = 1; attempt <= AI_SAVE_RETRIES; attempt++) {
            try {
                return wordRepository.save(candidate);
            } catch (DataIntegrityViolationException e) {
                if (isUqTargetSenseDup(e)) {
                    int next = allocateGlobalAiSenseNo();
                    candidate.setSenseNo(next);
                    log.warn("[WORD] AI sense_no dup → retry {}/{} with sense_no={}", attempt, AI_SAVE_RETRIES, next);
                    continue;
                }
                throw e;
            }
        }
        return wordRepository.save(candidate);
    }

    private boolean isUqTargetSenseDup(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("Duplicate entry") && msg.contains("uq_words_target_sense")) return true;
            t = t.getCause();
        }
        return false;
    }

    private Word mergeIntoExistingWord(Word w, Long targetCode, int senseNoSafe,
                                       String definition, String example, String cats, byte shoulderNo,
                                       List<String> syns, List<String> ants) {
        boolean dirty = false;

        String curCat = w.getWordCategory();
        boolean needCatUpdate = curCat == null || curCat.isBlank() || "일반어".equals(curCat) || NUM_CSV.matcher(curCat).matches();
        if (needCatUpdate && !cats.isBlank() && !cats.equals(curCat)) { w.setWordCategory(cats); dirty = true; }

        if (w.getTargetCode() == null || w.getTargetCode() == 0L) { w.setTargetCode(targetCode); dirty = true; }
        if ((w.getDefinition() == null || w.getDefinition().isBlank()) && !definition.isBlank()) { w.setDefinition(definition); dirty = true; }
        if ((w.getExample() == null || w.getExample().isBlank()) && !example.isBlank()) { w.setExample(example); dirty = true; }
        if (w.getShoulderNo() == 0 && shoulderNo != 0) { w.setShoulderNo(shoulderNo); dirty = true; }
        if (w.getSenseNo() == null || w.getSenseNo() == 0) { w.setSenseNo(senseNoSafe); dirty = true; }

        String mergedSyn = mergeCsv(w.getSynonym(), syns);
        if (!Objects.equals(nzCsv(w.getSynonym()), mergedSyn)) { w.setSynonym(mergedSyn); dirty = true; }
        String mergedAnt = mergeCsv(w.getAntonym(), ants);
        if (!Objects.equals(nzCsv(w.getAntonym()), mergedAnt)) { w.setAntonym(mergedAnt); dirty = true; }

        return dirty ? wordRepository.save(w) : w;
    }

    private void mapIntoWordbook(Long userId, Long wordId) {
        Wordbook wb = wordbookRepository.getOrCreateEntity(userId);
        WordbookWordId id = new WordbookWordId(wb.getWordbookId(), wordId);
        if (!wordbookWordRepository.existsById(id)) {
            wordbookWordRepository.save(WordbookWord.builder().id(id).build());
        }
    }

    // ===== 스코어링 =====

    private Optional<Word> selectBestDbCandidate(List<Word> cands, String context) {
        if (cands == null || cands.isEmpty()) return Optional.empty();
        if (context == null || context.isBlank()) return Optional.of(cands.get(0));

        String ctx = normalizeCtx(context);
        List<Scored<Word>> scored = cands.stream()
                .map(w -> new Scored<>(w, scoreWord(w, ctx)))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .toList();

        if (scored.get(0).score < MIN_SCORE) return Optional.empty();
        if (scored.size() >= 2 && scored.get(0).score - scored.get(1).score < MARGIN_TO_NEXT)
            return Optional.empty();

        return Optional.of(scored.get(0).item);
    }

    private Optional<DictEntry> selectBestEntry(List<DictEntry> cands, String context) {
        if (cands == null || cands.isEmpty()) return Optional.empty();
        if (context == null || context.isBlank()) return Optional.of(cands.get(0));

        String ctx = normalizeCtx(context);
        List<Scored<DictEntry>> scored = cands.stream()
                .map(e -> new Scored<>(e, scoreEntry(e, ctx)))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .toList();

        if (scored.get(0).score < MIN_SCORE) return Optional.empty();
        if (scored.size() >= 2 && scored.get(0).score - scored.get(1).score < MARGIN_TO_NEXT)
            return Optional.empty();

        return Optional.of(scored.get(0).item);
    }

    private String normalizeCtx(String s) {
        return s.toLowerCase().replaceAll("\\s+", " ").replaceAll("[^0-9a-z가-힣·]", " ");
    }

    private int scoreWord(Word w, String ctx) {
        int score = 0;
        score += csvHits(ctx, w.getWordCategory(), 3); // 카테고리 매칭 가중치↑
        score += textHits(ctx, w.getDefinition(), 2);  // 정의 키워드
        score += csvHits(ctx, w.getSynonym(), 1);      // 유의어
        score += domainBoost(ctx, w.getWordName(), w.getDefinition());
        return score;
    }

    private int scoreEntry(DictEntry e, String ctx) {
        int score = 0;
        score += csvHits(ctx, e.getCategories(), 3);
        score += textHits(ctx, e.getDefinition(), 2);
        score += csvHits(ctx, joinList(e.getSynonyms()), 1);
        score += domainBoost(ctx, e.getLemma(), e.getDefinition());
        return score;
    }

    private int csvHits(String ctx, String csv, int weight) {
        if (csv == null || csv.isBlank()) return 0;
        int s = 0;
        for (String t : csv.toLowerCase().split("\\s*,\\s*")) {
            if (t.length() < 2) continue;
            if (ctx.contains(t)) s += weight;
        }
        return s;
    }

    private int textHits(String ctx, String text, int weight) {
        if (text == null || text.isBlank()) return 0;
        int s = 0;
        for (String t : text.toLowerCase().split("[\\s·,]+")) {
            if (t.length() < 2) continue;
            if (ctx.contains(t)) s += weight;
        }
        return s;
    }

    // 예시: '사과' 케이스 힌트(원하면 확장)
    private static final Set<String> APOLOGY_HINTS = Set.of("유감","사죄","입장","사과문","해명","사과하다","논란","피해자","책임","공식","사과의 뜻","유족");
    private static final Set<String> FRUIT_HINTS   = Set.of("과일","과수","농가","재배","품종","당도","수확","사과즙","사과나무","과일값","농업","식품");

    private int domainBoost(String ctx, String lemma, String def) {
        int s = 0;
        for (String k : APOLOGY_HINTS) if (ctx.contains(k)) s += 3;
        for (String k : FRUIT_HINTS)   if (ctx.contains(k)) s += 3;
        if (def != null) {
            String d = def.toLowerCase();
            for (String k : APOLOGY_HINTS) if (d.contains(k)) s += 1;
            for (String k : FRUIT_HINTS)   if (d.contains(k)) s += 1;
        }
        return s;
    }

    // ===== 토큰화/유틸 =====

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

    private static String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String normalizeKo(String s) {
        String trimmed = s.replaceAll("\\s+", "");
        trimmed = trimmed.replaceAll("[(\\[].*?[)\\]]", "");
        return Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
    }
    private String cut(String s, int max) { return (s == null) ? "" : (s.length() <= max ? s : s.substring(0, max)); }
    private static String nzCsv(String s) { return (s == null) ? "" : s; }

    private static String mergeCsv(String existingCsv, List<String> add) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (existingCsv != null && !existingCsv.isBlank()) {
            for (String t : existingCsv.split("\\s*,\\s*")) if (!t.isBlank()) set.add(t.trim());
        }
        if (add != null) for (String t : add) if (t != null && !t.isBlank()) set.add(t.trim());
        return String.join(", ", set);
    }

    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}

    private static class Scored<T> { final T item; final int score; Scored(T i, int s){ item=i; score=s; } }
}

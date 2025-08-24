package com.words_hanjoom.domain.wordbooks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseCandidate;
import com.words_hanjoom.domain.wordbooks.dto.response.AiPickResult;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.AiDictionaryClient;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager em;


    // 기존(맥락 없음) — 하위 호환 유지
    @Transactional
    public Result processCsv(Long userId, String csv) {
        return saveAll(userId, tokenizeUnknownWords(csv), null);
    }


    @Transactional
    public void importUnknownWords(Long userId, String raw, String context) {
        if (raw == null || raw.isBlank()) return;

        Set<String> tokens = Arrays.stream(raw.split("[,、/\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String term : tokens) {
            // 기존 파이프라인 재사용 + 컨텍스트 반영
            saveOne(userId, term, context);
        }
    }

    @Transactional
    public void importUnknownWords(Long userId, String raw) {
        importUnknownWords(userId, raw, null);
    }

    // 컨트롤러에서 기사 맥락 문자열을 만들어 넘겨줄 때 사용
    @Transactional
    public Result processCsv(Long userId, String csv, String context) {
        return saveAll(userId, tokenizeUnknownWords(csv), context);
    }

    @Transactional
    public Result saveAll(Long userId, List<String> tokens, String context) {
        Wordbook wordbook = wordbookRepository.ensureWordbook(userId);
        List<SavedWord> saved = new ArrayList<>();
        for (String raw : tokens) {
            saveOne(userId, raw, context).ifPresent(saved::add);
        }
        return new Result(wordbook.getWordbookId(), saved);
    }

    // 맥락 기반 DB → NIKL → AI 순차 폴백
    @Transactional
    public Optional<SavedWord> saveOne(Long userId, String raw, String context) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        final String surface = normalizeKo(raw);
        final String plain   = surface.replaceAll("[-·ㆍ‐–—]", "");
        log.debug("[WORD] saveOne userId={} surface='{}' ctx?={}", userId, surface, context != null);

        // === 1) DB 우선 + 중복 제거 ===
        List<Word> dbCands = new ArrayList<>();
        dbCands.addAll(wordRepository.findAllByWordName(surface));
        dbCands.addAll(wordRepository.findLooselyByNameMulti(surface, plain));
        dbCands = dedupBySense(dbCands); // ★ (tc, sense_no) 기준 중복 제거

        log.info("[CHK] DB candidates '{}' = {}", surface, dbCands.size());
        dbCands.stream().limit(5).forEach(w ->
                log.info("[CHK] DB[{}] tc={} sense={} cat={} def={}",
                        w.getWordId(), w.getTargetCode(), w.getSenseNo(),
                        w.getWordCategory(), cut(nz(w.getDefinition()), 40)));

        if (!dbCands.isEmpty()) {
            // 컨텍스트가 있으면: '맥락 적합' 후보만 남기기
            if (context != null && !context.isBlank()) {
                List<Word> fits = new ArrayList<>();
                for (Word w : dbCands) {
                    boolean ok = aiDictionaryClient.isDbCandidateFit(surface, context, w)
                            .onErrorReturn(false)
                            .blockOptional().orElse(false);
                    if (ok) fits.add(w);
                }

                if (!fits.isEmpty()) {
                    int pick = (fits.size() == 1) ? 0
                            : aiDictionaryClient.pickBestWordIndexFromDbCandidates(surface, context, fits)
                            .onErrorReturn(-1)
                            .blockOptional().orElse(-1);
                    Word chosen = (pick >= 0 && pick < fits.size()) ? fits.get(pick) : fits.get(0);

                    log.info("[WORD] DB-PICK '{}' (context-fit) → wordId={}, tc={}, sense={}",
                            surface, chosen.getWordId(), chosen.getTargetCode(), chosen.getSenseNo());
                    mapIntoWordbook(userId, chosen.getWordId());
                    return Optional.of(new SavedWord(surface, chosen.getWordName(), chosen.getWordId()));
                } else {
                    // 맥락에 맞는 DB 후보가 없으면 NIKL로 폴백
                    log.info("[WORD] DB has candidates, but none fit context → fallthrough to NIKL.");
                }
            } else {
                // 컨텍스트가 없으면 첫 후보로 확정 (기존 동작 유지)
                Word chosen = dbCands.get(0);
                log.info("[WORD] DB-PICK '{}' → wordId={}, tc={}, sense={}",
                        surface, chosen.getWordId(), chosen.getTargetCode(), chosen.getSenseNo());
                mapIntoWordbook(userId, chosen.getWordId());
                return Optional.of(new SavedWord(surface, chosen.getWordName(), chosen.getWordId()));
            }
        }

        // 2) NIKL 후보 조회 → (맥락 + AI[target_code]) → view 보강
        List<DictEntry> niklCands = fetchCandidates(surface, 20).blockOptional().orElse(List.of());
        log.info("[CHK] NIKL candidates '{}' = {}", surface, niklCands.size());
        niklCands.stream().limit(5).forEach(e ->
                log.info("[CHK] NIKL cand tc={} sense={} def={}",
                        e.getTargetCode(), e.getSenseNo(), cut(nz(e.getDefinition()), 40)));

        if (!niklCands.isEmpty()) {
            DictEntry picked = null;

            if (context != null && !context.isBlank()) {
                // V2: target_code 직접 선택
                AiPickResult res = aiDictionaryClient
                        .pickBestFromNiklCandidatesV2(surface, context, niklCands)
                        // idx=null, tc=null, senseNo=null, conf=0.0 으로 기본값
                        .onErrorReturn(new AiPickResult(null, null, null, 0.0))
                        .block();

                if (res != null && res.getTargetCode() != null) {
                    Long tc = res.getTargetCode();
                    picked = niklCands.stream()
                            .filter(e -> Objects.equals(e.getTargetCode(), tc))
                            .findFirst()
                            .orElse(null);


                    if (picked != null) {
                        // ★ 여기서 필드 보강
                        enrichFromNikl(niklCands, res, picked);
                    }

                    log.info("[AI] picked by target_code tc={}, conf={}", tc, res.getConfidence());
                }

                // 백업: 옛날 choice-index 응답
                if (picked == null) {
                    int idx0 = normalizeIdx(res != null ? res.getIdx() : null, niklCands.size()); // 0-base
                    picked = niklCands.get(idx0);
                    log.info("[AI] picked by index idx={}, tc={}, conf={}",
                            idx0 + 1, picked.getTargetCode(),
                            (res != null ? res.getConfidence() : null));
                }
            } else {
                picked = niklCands.get(0);
            }

            // view.do 로 유의어/반의어/카테고리/예문/sense_no 보강
            DictEntry enriched = dictClient.enrichFromView(picked)
                    .blockOptional().orElse(picked);

            Word saved = saveResolvedEntry(userId, surface, enriched);
            return Optional.of(new SavedWord(surface, saved.getWordName(), saved.getWordId()));
        }

        // === 3) 최후 폴백: AI 생성 ===
        DictEntry ai = fetchAiEntry(surface, context);
        if (ai != null) {
            Word saved = saveResolvedEntry(userId, surface, ai);
            return Optional.of(new SavedWord(surface, saved.getWordName(), saved.getWordId()));
        }

        log.info("[WORD] No sense resolved for '{}' (AI-only)", surface);
        return Optional.empty();
    }

    // (tc, sense_no) 기준 중복 제거
    private List<Word> dedupBySense(List<Word> list) {
        LinkedHashMap<String, Word> map = new LinkedHashMap<>();
        for (Word w : list) {
            String key = (w.getTargetCode()==null?0L:w.getTargetCode()) + "#" + (w.getSenseNo()==null?0:w.getSenseNo());
            map.putIfAbsent(key, w);
        }
        return new ArrayList<>(map.values());
    }


    private DictEntry enrichWithView(DictEntry base) {
        try {
            Long tc = base.getTargetCode();
            if (tc == null || tc <= 0) return base;

            ViewResponse vr = dictClient.view(tc).block();
            if (vr == null || vr.getChannel() == null || vr.getChannel().getItem() == null || vr.getChannel().getItem().isEmpty()) {
                return base;
            }
            var item = vr.getChannel().getItem().get(0);

            // sense 매칭: 같은 senseNo가 있으면 그걸, 없으면 첫 sense
            ViewResponse.Sense sense = null;
            if (item.getSense() != null && !item.getSense().isEmpty()) {
                Integer want = base.getSenseNo();
                if (want != null) {
                    for (var s : item.getSense()) {
                        if (String.valueOf(want).equals(s.getSenseNo())) { sense = s; break; }
                    }
                }
                if (sense == null) sense = item.getSense().get(0);
            }

            String def = base.getDefinition();
            String ex  = base.getExample();
            String cats = base.getCategories();
            List<String> syn = base.getSynonyms() == null ? List.of() : base.getSynonyms();
            List<String> ant = base.getAntonyms() == null ? List.of() : base.getAntonyms();

            if (sense != null) {
                if (sense.getDefinition() != null && !sense.getDefinition().isBlank()) def = sense.getDefinition();
                var exList = sense.getExample();
                if (exList != null && !exList.isEmpty()) ex = String.join(" | ", exList);

                if (sense.getCatInfo() != null && !sense.getCatInfo().isEmpty()) {
                    cats = sense.getCatInfo().stream()
                            .map(ViewResponse.CatInfo::getCat)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .collect(Collectors.joining(", "));
                }
                // 유/반의어 수집(lexical_info + relation)
                LinkedHashSet<String> synSet = new LinkedHashSet<>(syn);
                LinkedHashSet<String> antSet = new LinkedHashSet<>(ant);

                if (sense.getLexicalInfo() != null) {
                    for (var lx : sense.getLexicalInfo()) {
                        String t = lx.getType() == null ? "" : lx.getType().replace(" ", "");
                        String w = lx.getWord();
                        if (w == null || w.isBlank()) continue;
                        if (t.contains("유의") || t.contains("동의") || t.contains("비슷")) synSet.add(w);
                        if (t.contains("반의") || t.contains("반대") || t.contains("상반")) antSet.add(w);
                    }
                }
                if (sense.getRelation() != null) {
                    for (var r : sense.getRelation()) {
                        String t = r.getType() == null ? "" : r.getType().replace(" ", "");
                        String w = r.getWord();
                        if (w == null || w.isBlank()) continue;
                        if (t.contains("유의") || t.contains("동의") || t.contains("비슷")) synSet.add(w);
                        if (t.contains("반의") || t.contains("반대") || t.contains("상반")) antSet.add(w);
                    }
                }
                syn = new ArrayList<>(synSet);
                ant = new ArrayList<>(antSet);
            } else if (item.getDefinition() != null && !item.getDefinition().isBlank()) {
                def = item.getDefinition();
            }

            return DictEntry.builder()
                    .lemma(base.getLemma())
                    .targetCode(base.getTargetCode())
                    .shoulderNo(base.getShoulderNo())
                    .senseNo(base.getSenseNo())
                    .definition(def)
                    .example(ex)
                    .categories((cats == null || cats.isBlank()) ? "일반" : cats)
                    .synonyms(syn)
                    .antonyms(ant)
                    .build();
        } catch (Exception e) {
            log.warn("[DICT] enrichWithView error: {}", e.toString());
            return base;
        }
    }

    public Mono<List<DictEntry>> fetchCandidates(String surface, int limit) {
        int cap = Math.max(1, Math.min(limit, 20));
        return dictClient.lookupAllSenses(surface)
                .map(list -> list.stream().limit(cap).toList());
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
    private Word saveResolvedEntry(Long userId, String surface, DictEntry e) {
        final Byte shoulderSafe = Optional.ofNullable(e.getShoulderNo()).orElse((byte) 0);
        final Long tc = Optional.ofNullable(e.getTargetCode()).orElse(0L);
        final Integer sn = e.getSenseNo();
        final int senseSafe = (sn != null) ? sn : 0; // null일 경우 0으로 안전하게 변환

        // (target_code, sense_no)로만 존재 여부 판단
        Optional<Word> opt = wordRepository.findByTargetCodeAndSenseNo(e.getTargetCode(), senseSafe);

        Word saved;
        if (opt.isPresent()) {
            // 이미 있는 뜻이면 "보충"만 하고 tc/sense는 절대 바꾸지 말기
            Word w = opt.get();
            boolean dirty = false;

            if ((w.getDefinition() == null || w.getDefinition().isBlank()) && e.getDefinition() != null) {
                w.setDefinition(e.getDefinition()); dirty = true;
            }
            if ((w.getExample() == null || w.getExample().isBlank()) && e.getExample() != null) {
                w.setExample(e.getExample()); dirty = true;
            }
            if ((w.getWordCategory() == null || w.getWordCategory().isBlank()) && e.getCategories() != null) {
                w.setWordCategory(e.getCategories()); dirty = true;
            }

            String mergedSyn = mergeCsv(w.getSynonym(), e.getSynonyms());
            if (!Objects.equals(nzCsv(w.getSynonym()), mergedSyn)) {
                w.setSynonym(mergedSyn); dirty = true;
            }
            String mergedAnt = mergeCsv(w.getAntonym(), e.getAntonyms());
            if (!Objects.equals(nzCsv(w.getAntonym()), mergedAnt)) {
                w.setAntonym(mergedAnt); dirty = true;
            }


            saved = dirty ? wordRepository.saveAndFlush(w) : w;
        } else {
            // 새 뜻은 절대 기존 row 재사용/수정하지 말고 "신규 row" 생성
            Word w = new Word();
            w.setWordName(surface);
            w.setTargetCode(e.getTargetCode());
            w.setSenseNo(senseSafe);
            w.setWordCategory(Objects.toString(e.getCategories(), ""));
            w.setDefinition(Objects.toString(e.getDefinition(), ""));
            w.setExample(Objects.toString(e.getExample(), ""));
            w.setSynonym(joinList(e.getSynonyms()));
            w.setAntonym(joinList(e.getAntonyms()));
            saved = saveNewWordWithRetry(w, tc);
        }

        mapIntoWordbook(userId, saved.getWordId());
        log.info("[WORD] saved '{}' → id={}, tc={}, sense={}",
                surface, saved.getWordId(), saved.getTargetCode(), saved.getSenseNo());

        debugDumpWordRows(surface);
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
        if (candidate.getSenseNo() == null) {
            candidate.setSenseNo(0);
        }

        if (!Objects.equals(targetCode, 0L)) {
            // NIKL 등 AI가 아닌 단어는 바로 저장. 이제 senseNo가 null이 아니므로 안전.
            return wordRepository.saveAndFlush(candidate);
        }

        // 이 아래는 AI 단어(target_code=0)에 대한 충돌 처리 로직
        for (int attempt = 1; attempt <= AI_SAVE_RETRIES; attempt++) {
            try {
                return wordRepository.saveAndFlush(candidate);
            } catch (DataIntegrityViolationException e) {
                if (isUqTargetSenseDup(e)) {
                    // AI 단어의 sense_no 중복 시에만 새 번호 할당
                    int next = allocateGlobalAiSenseNo();
                    candidate.setSenseNo(next);
                    log.warn("[WORD] AI sense_no dup → retry {}/{} with sense_no={}", attempt, AI_SAVE_RETRIES, next);
                    continue;
                }
                throw e;
            }
        }
        return wordRepository.saveAndFlush(candidate);
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

    private Word mergeIntoExistingWord(
            Word w, Long targetCode, int senseNoSafe,
            String definition, String example, String cats, byte shoulderNo,
            List<String> syns, List<String> ants
    ) {
        boolean dirty = false;

        // target_code: 0(또는 null) → 양수로만 승격, 이미 값 있으면 그대로 유지
        if ((w.getTargetCode() == null || w.getTargetCode() == 0L)
                && targetCode != null && targetCode > 0) {
            w.setTargetCode(targetCode);
            dirty = true;
        }

        // sense_no: 비어 있으면만 채움(유니크 충돌 방지)
        if ((w.getSenseNo() == null || w.getSenseNo() == 0) && senseNoSafe > 0) {
            w.setSenseNo(senseNoSafe);
            dirty = true;
        }

        // 카테고리: 비었거나 ‘일반/일반어/숫자CSV’일 때만 갱신
        String curCat = w.getWordCategory();
        boolean needCatUpdate = curCat == null || curCat.isBlank()
                || "일반".equals(curCat) || "일반어".equals(curCat)
                || NUM_CSV.matcher(curCat).matches();
        if (needCatUpdate && cats != null && !cats.isBlank() && !cats.equals(curCat)) {
            w.setWordCategory(cats);
            dirty = true;
        }

        if ((w.getDefinition() == null || w.getDefinition().isBlank()) && definition != null && !definition.isBlank()) {
            w.setDefinition(definition);
            dirty = true;
        }
        if ((w.getExample() == null || w.getExample().isBlank()) && example != null && !example.isBlank()) {
            w.setExample(example);
            dirty = true;
        }

        if (w.getShoulderNo() == 0 && shoulderNo != 0) {
            w.setShoulderNo(shoulderNo);
            dirty = true;
        }

        String mergedSyn = mergeCsv(w.getSynonym(), syns);
        if (!Objects.equals(nzCsv(w.getSynonym()), mergedSyn)) {
            w.setSynonym(mergedSyn);
            dirty = true;
        }
        String mergedAnt = mergeCsv(w.getAntonym(), ants);
        if (!Objects.equals(nzCsv(w.getAntonym()), mergedAnt)) {
            w.setAntonym(mergedAnt);
            dirty = true;
        }

        return dirty ? wordRepository.saveAndFlush(w) : w;
    }

    private void mapIntoWordbook(Long userId, Long wordId) {
        log.info("[MAP] userId={}, wordId={}", userId, wordId);   // ← 여기!

        Wordbook wb = wordbookRepository.ensureWordbook(userId);
        int affected = wordbookWordRepository.insertIgnore(wb.getWordbookId(), wordId);

        if (affected == 0) {
            log.debug("[MAP] already mapped (wbId={}, wordId={})", wb.getWordbookId(), wordId);
        } else {
            log.info("[MAP] mapped (wbId={}, wordId={})", wb.getWordbookId(), wordId);
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

    /** LLM이 target_code만 준 경우, 동일 tc의 NIKL 후보로부터 필드 보강 */
    private void enrichFromNikl(List<DictEntry> niklCands, AiPickResult r, DictEntry e) {
        niklCands.stream()
                .filter(c -> Objects.equals(c.getTargetCode(), r.getTargetCode()))
                .findFirst()
                .ifPresent(c -> {
                    if (e.getShoulderNo() == null)
                        e.setShoulderNo(Optional.ofNullable(c.getShoulderNo()).orElse((byte) 0));
                    if ((e.getSenseNo() == null || e.getSenseNo() == 0) && c.getSenseNo() != null)
                        e.setSenseNo(c.getSenseNo());
                    if (e.getDefinition() == null)  e.setDefinition(c.getDefinition());
                    if (e.getCategories() == null)  e.setCategories(c.getCategories());
                    if (e.getExample() == null)     e.setExample(c.getExample());
                });
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

    private int normalizeIdx(Integer choice, int size) {
        if (choice == null) return -1;
        // choice가 1~N 으로 올 수도 있어 보정
        if (choice >= 1 && choice <= size) return choice - 1;
        // 0~N-1 로 왔으면 그대로
        if (choice >= 0 && choice < size) return choice;
        return -1;
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

    // --- 디버그용: 동일 표제어의 모든 행 덤프 ---
    private void debugDumpWordRows(String surface) {
        List<Word> rows = wordRepository.findAllByWordName(surface);
        log.info("[DBG] rows for '{}' = {}", surface, rows.size());
        for (Word w : rows) {
            log.info("[DBG] id={}, tc={}, sense={}, name='{}'",
                    w.getWordId(), w.getTargetCode(), w.getSenseNo(), w.getWordName());
        }
    }

    private List<String> tokenizeUnknownWords(String text) {
        if (text == null || text.isBlank()) return java.util.List.of();
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result saveAllInNewTx(Long userId, List<String> tokens, String context) {
        return saveAll(userId, tokens, context);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importUnknownWordsInNewTx(Long userId, String raw, String context) {
        importUnknownWords(userId, raw, context);
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

    private static String nz(String s) { return s == null ? "" : s; }

    private static class Scored<T> { final T item; final int score; Scored(T i, int s){ item=i; score=s; } }
}

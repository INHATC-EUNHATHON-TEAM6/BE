package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.*;
import com.words_hanjoom.domain.wordbooks.repository.*;
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

    private static Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split("\\s*,\\s*"))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private static String mergeCsv(String existingCsv, List<String> incomingList) {
        LinkedHashSet<String> set = new LinkedHashSet<>(csvToSet(existingCsv));
        if (incomingList != null) {
            for (String s : incomingList) {
                if (s != null && !s.isBlank()) set.add(s.trim());
            }
        }
        return set.isEmpty() ? "" : String.join(", ", set);
    }

    // ★ 단어 1개만 새 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SavedWord> saveOne(Long userId, String raw) {


        // ✅ 수정: API 호출 전에 입력 값이 유효한지 확인
        if (raw == null || raw.isBlank()) {
            log.debug("[WORD] skip blank token (userId={}): '{}'", userId, raw);
            return Optional.empty();
        }

        final String surface = normalizeKo(raw);
        final String plain   = surface.replaceAll("[-·ㆍ‐–—]", "");
        log.debug("[WORD] start saveOne userId={}, surface='{}', plain='{}'", userId, surface, plain);



        // 1) DB 먼저 조회 (입력값과 동일한 표제어가 이미 있을 때, API 스킵)
        Optional<Word> existing = wordRepository.findByWordName(surface);
        if (existing.isEmpty()) {
            existing = wordRepository.findLooselyByName(surface, plain);
        }

        if (existing.isPresent()) {
            Word w = existing.get();
            log.info("[WORD] DB HIT (NO API) surface='{}' → wordId={}, name='{}'", surface, w.getWordId(), w.getWordName());

            WordbookWordId id = new WordbookWordId(
                    wordbookRepository.getOrCreateEntity(userId).getWordbookId(),
                    w.getWordId()
            );
            if (!wordbookWordRepository.existsById(id)) {
                wordbookWordRepository.save(WordbookWord.builder().id(id).build());
                log.info("[WORD] mapping CREATED wordbookId={} wordId={}", id.getWordbookId(), id.getWordId());
            } else {
                log.debug("[WORD] mapping already exists wordbookId={} wordId={}", id.getWordbookId(), id.getWordId());
            }
            return Optional.of(new SavedWord(surface, w.getWordName(), w.getWordId()));
        }


        // 2) 없으면 API 조회
        log.debug("[WORD] DB MISS surface='{}' – calling DICT API", surface);
        DictEntry entry = dictClient.quickLookup(surface).blockOptional().orElse(null);

        if (entry == null) {
            log.debug("[WORD] DICT MISS for surface='{}' – skip", surface);
            return Optional.empty(); // 사전 미히트 → 스킵
        }

        log.info("[WORD] entry lemma='{}' tc={} ex?={} syn={} ant={} senseNo={}",
                entry.getLemma(),
                entry.getTargetCode(),
                entry.getExample() != null && !entry.getExample().isBlank(),
                entry.getSynonyms() == null ? -1 : entry.getSynonyms().size(),
                entry.getAntonyms() == null ? -1 : entry.getAntonyms().size(),
                entry.getSenseNo());

        final String wordName = cut(entry.getLemma(), LEN_WORD_NAME);
        final String definition = cut(entry.getDefinition(), LEN_DEF);
        final String example = cut(entry.getExample(), LEN_EXAMPLE);
        final Long   targetCode = entry.getTargetCode() != null && entry.getTargetCode() > 0 ? entry.getTargetCode() : null;
        final Short  senseNo = entry.getSenseNo();

        log.debug("[SAVE] surface={}, lemma={}, tc={}, senseNo={}",
                surface, entry.getLemma(), entry.getTargetCode(), entry.getSenseNo());

        // 리스트 → 콤마 문자열 (NOT NULL 보장)
        String synCsv = (entry.getSynonyms() == null || entry.getSynonyms().isEmpty())
                ? "" : String.join(",", entry.getSynonyms());
        String antCsv = (entry.getAntonyms() == null || entry.getAntonyms().isEmpty())
                ? "" : String.join(",", entry.getAntonyms());


        log.info("[WORD] save '{}' syn={} ant={} senseNo={}",
                wordName,
                (entry.getSynonyms() == null ? 0 : entry.getSynonyms().size()),
                (entry.getAntonyms() == null ? 0 : entry.getAntonyms().size()),
                entry.getSenseNo());

        Word word = wordRepository.findByWordName(wordName)
                .map(w -> {
                    boolean dirty = false;

                    // target_code 보정
                    if (w.getTargetCode() == null && targetCode != null) {
                        w.setTargetCode(targetCode);
                        dirty = true;
                    }
                    // 부족한 필드 보정(있으면 유지, 없으면 채움)
                    if ((w.getDefinition() == null || w.getDefinition().isBlank()) && definition != null) {
                        w.setDefinition(definition);
                        dirty = true;
                    }
                    if ((w.getExample() == null || w.getExample().isBlank()) && example != null) {
                        w.setExample(example);
                        dirty = true;
                    }
                    if (w.getWordCategory() == null && entry.getFieldType() != null) {
                        w.setWordCategory(entry.getFieldType());
                        dirty = true;
                    }
                    if (w.getShoulderNo() == 0 && entry.getShoulderNo() != 0) {
                        w.setShoulderNo(entry.getShoulderNo());
                        dirty = true;
                    }

                    if ((w.getSenseNo() == null || w.getSenseNo() == 0) && entry.getSenseNo() != null) {
                        w.setSenseNo(entry.getSenseNo());
                        dirty = true;
                    }

                    // ✅ 유의어/반의어 업데이트 보정: 빈 값이면 채우고, 값이 있으면 병합(중복 제거)
                    String mergedSyn = mergeCsv(w.getSynonym(), entry.getSynonyms());
                    if (!Objects.equals((w.getSynonym() == null ? "" : w.getSynonym()), mergedSyn)) {
                        w.setSynonym(mergedSyn); dirty = true;
                    }
                    String mergedAnt = mergeCsv(w.getAntonym(), entry.getAntonyms());
                    if (!Objects.equals((w.getAntonym() == null ? "" : w.getAntonym()), mergedAnt)) {
                        w.setAntonym(mergedAnt); dirty = true;
                    }

                    return dirty ? wordRepository.save(w) : w;
                })
                .orElseGet(() -> {
                    try {
                        return wordRepository.save(Word.builder()
                                .wordName(wordName)
                                .definition(definition == null ? "" : definition)
                                .wordCategory(entry.getFieldType())
                                .shoulderNo(entry.getShoulderNo())
                                .example(example == null ? "" : example)
                                .targetCode(targetCode)
                                .senseNo(entry.getSenseNo())
                                .synonym(joinList(entry.getSynonyms()))  // 신규는 그대로
                                .antonym(joinList(entry.getAntonyms()))
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

    public record SavedWord(String surface, String wordName, Long wordId) {}
    public record Result(Long wordbookId, List<SavedWord> words) {}
}
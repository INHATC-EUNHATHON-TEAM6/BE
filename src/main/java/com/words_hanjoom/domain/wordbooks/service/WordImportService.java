import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.Word;
import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWordId;
import com.words_hanjoom.domain.wordbooks.repository.*;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordImportService {

    private final NiklDictionaryClient dictClient;
    private final ScrapActivityRepository scrapRepo;
    private final WordRepository wordRepo;
    private final WordbookRepository wordbookRepo;
    private final WordbookWordRepository wordbookWordRepo;

    // 사용자 기본 단어장 가져오기(없으면 생성) — 네 프로젝트 규칙에 맞게 수정
    private Wordbook getOrCreateDefaultWordbook(Long userId) {
        return wordbookRepo.findDefaultByUserId(userId)
                .orElseGet(() -> wordbookRepo.save(Wordbook.createDefaultFor(userId)));
    }

    /** UNKNOWN_WORD 스크랩에서 user_answer 토큰을 사전 조회 → Word에 저장 → 단어장 매핑 */
    public Map<String, Object> importFromUnknownScraps(Long userId, int limit) {
        Wordbook wb = getOrCreateDefaultWordbook(userId);

        // 1) 스크랩에서 단어들 모으기 (레포지토리 쿼리는 프로젝트에 맞게)
        List<String> tokens = new ArrayList<>();
        scrapRepo.findUnknownWordAnswers(userId, limit)
                .forEach(sa -> {
                    if (sa.getUserAnswer() != null) {
                        // 공백/쉼표로 토큰화 (원하면 더 정교하게)
                        Stream.of(sa.getUserAnswer().split("[\\s,]+"))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .forEach(tokens::add);
                    }
                });

        int imported = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        // 2) 각 토큰을 조회 → 저장
        for (String tk : tokens) {
            try {
                DictEntry e = dictClient.quickLookup(tk).block(Duration.ofSeconds(8));
                if (e == null) {
                    details.add(detail("dict-miss", tk, null));
                    continue;
                }

                // 2-1) Word upsert (lemma 기준)
                Word word = wordRepo.findByLemma(e.getLemma())
                        .orElseGet(Word::new);
                word.setLemma(e.getLemma());
                word.setDefinition(e.getDefinition());
                word.setFieldType(e.getFieldType()); // 필드 없으면 엔티티에 추가해야 함
                word.setShoulderNo(e.getShoulderNo()); // byte 필드
                word.setExample(e.getExample());       // 예문 필드

                Word saved = wordRepo.save(word);

                // 2-2) 단어장 매핑 upsert
                WordbookWordId id = new WordbookWordId(wb.getId(), saved.getId());
                if (!wordbookWordRepo.existsById(id)) {
                    wordbookWordRepo.save(new WordbookWord(wb, saved));
                }

                imported++;
                details.add(detail("saved", tk, e.getLemma()));

            } catch (Exception ex) {
                log.warn("[IMPORT] {} -> EX: {}", tk, ex.toString());
                details.add(detail("exception", tk, null));
            }
        }

        return Map.of("imported", imported, "details", details);
    }

    private Map<String, Object> detail(String reason, String token, String lemma) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok".equals(reason) || "saved".equals(reason) ? "ok" : "skipped");
        m.put("reason", reason);
        m.put("token", token);
        if (lemma != null) m.put("lemma", lemma);
        return m;
    }
}

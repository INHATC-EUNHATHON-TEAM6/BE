package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.domain.wordbooks.entity.Word;
import com.words_hanjoom.domain.wordbooks.repository.WordRepository;
import com.words_hanjoom.infra.dictionary.NiklDictionaryClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordImportService {

    private final NiklDictionaryClient dictClient;  // 이미 너가 만든 인터페이스
    private final WordRepository wordRepository;

    /**
     * 표면형으로 검색 → 첫 item의 targetCode로 view 호출 → 모든 뜻 저장
     */
    @Transactional
    public int importBySurface(String surface) {
        // 1) exact 표제 검색해서 첫 item 가져오기
        var req = new SearchRequest(
                surface, "json", 1, 10, "n",
                Optional.of(1),          // target 표제어
                Optional.of("exact"),    // 일치
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        SearchResponse sr = dictClient.search(req).block(Duration.ofSeconds(5));
        if (sr == null || sr.getChannel() == null || sr.getChannel().getItem() == null || sr.getChannel().getItem().isEmpty())
            return 0;

        long targetCode = sr.getChannel().getItem().get(0).getTargetCode();

        // 2) view로 뜻 전체 조회
        ViewResponse vr = dictClient.view(targetCode).block(Duration.ofSeconds(5));
        if (vr == null || vr.getChannel() == null || vr.getChannel().getItem() == null || vr.getChannel().getItem().isEmpty())
            return 0;

        var entry = vr.getChannel().getItem().get(0); // 표제 항목
        var senses = Optional.ofNullable(entry.getSense()).orElse(List.of());
        byte shoulderNo = NiklDictionaryClient.parseByteOrZero(entry.getSupNo());

        int saved = 0;
        for (int i = 0; i < senses.size(); i++) {
            var s = senses.get(i);
            short senseNo = (short) (i + 1);

            if (wordRepository.existsByTargetCodeAndSenseNo(targetCode, senseNo)) {
                continue;
            }

            Word w = new Word();
            w.setTargetCode(targetCode);
            w.setSenseNo(senseNo);
            w.setWordName(entry.getWord());
            w.setDefinition(s.getDefinition());
            w.setWordCategory(blankToNull(s.getCat()));
            w.setShoulderNo(shoulderNo);

            // 유의어/반의어/예문: 현재 API 매핑이 없으면 비워두거나 콤마로 채움
            w.setSynonym(nullToTrimmedCsv(List.<String>of())); // 나중에 채울 수 있음
            w.setAntonym(nullToTrimmedCsv(List.<String>of()));
            w.setExample(nullToMultiline(List.<String>of()));

            wordRepository.save(w);
            saved++;
        }
        return saved;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
    private static String nullToTrimmedCsv(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .collect(Collectors.joining(", "));
    }
    private static String nullToMultiline(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join("\n", list);
    }
}
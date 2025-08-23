// com.words_hanjoom.infra.dictionary.AiDictionaryClient.java
package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.ai.SenseCandidate;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseChoiceResult;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.Word;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AiDictionaryClient {
    // 맥락 기반으로 정의를 "생성" (NIKL 실패 시 최후 폴백)
    Mono<DictEntry> defineFromAi(String surface, String context);

    Mono<DictEntry> pickBestFromNiklCandidates(String surface, String context, List<DictEntry> candidates);
    Mono<Integer>   pickBestWordIndexFromDbCandidates(String surface, String context, List<Word> candidates);
    // DB 후보(같은 word_name의 여러 뜻) 중 맥락에 맞는 하나 "선택" → index 반환
    Mono<Boolean> isDbCandidateFit(String surface, String context, Word candidate);

    Mono<SenseChoiceResult> chooseSenseByAI(String context, List<DictEntry> entries);
}
package com.words_hanjoom.infra;

import java.util.List;
import java.util.Optional;

/** 표준국어대사전 클라이언트 – words 테이블 스키마에 맞춘 결과 제공 */
public interface NiklDictionaryClient {

    /** 입력 표면형 → 표제어(lemma) 후보 1개 */
    Optional<String> findLemma(String surface);

    /** 표제어 기준 상세 조회 (words 테이블 컬럼에 맵핑되는 필드 포함) */
    Optional<Lexeme> lookup(String lemma);

    /**
     * words 테이블 맵핑용 DTO
     * - wordName       -> words.word_name
     * - synonyms       -> words.synonym (서비스에서 ", "로 join)
     * - antonyms       -> words.antonym (서비스에서 ", "로 join)
     * - definition     -> words.definition
     * - categories     -> words.word_category (서비스에서 ", "로 join 또는 첫 값)
     * - shoulderNo     -> words.shoulder_no
     * - example        -> words.example
     */
    record Lexeme(
            String wordName,
            List<String> synonyms,
            List<String> antonyms,
            String definition,
            List<String> categories,
            Byte shoulderNo,
            String example
    ) {}
}
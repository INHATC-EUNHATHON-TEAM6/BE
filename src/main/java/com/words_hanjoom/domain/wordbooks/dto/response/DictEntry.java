package com.words_hanjoom.domain.wordbooks.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DictEntry{
        private String lemma;       // 단어명
        private String definition;  // 정의
        private Long   targetCode;  // target_code
        private Byte   shoulderNo;  // 어깨번호
        private String example;     // 예문
        private Integer senseNo;    // 의미번호
        private String categories;

        private java.util.List<String> synonyms;
        private java.util.List<String> antonyms;
}
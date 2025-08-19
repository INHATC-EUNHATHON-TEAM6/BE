package com.words_hanjoom.domain.wordbooks.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DictEntry {
    private String lemma; // 단어명(word)
    private String definition; // 정의(sense.definition)
    private String fieldType; // 분야/유형(sense.type)
    private Long targetCode; // target_code
    private byte shoulderNo; // 어깨번호(sup_no)
    private String example; // 예문
    private Short  senseNo;     // ✅ sense_no (의미 번호)
}
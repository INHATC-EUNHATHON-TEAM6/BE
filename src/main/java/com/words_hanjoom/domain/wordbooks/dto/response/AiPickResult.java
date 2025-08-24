package com.words_hanjoom.domain.wordbooks.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiPickResult {
    private Integer idx;        // LLM이 주는 1-base 인덱스 (optional)
    private Long    targetCode; // optional
    private Integer senseNo;    // optional
    private Double  confidence;

    public AiPickResult(Long targetCode, Integer senseNo, Double confidence) {
        this.targetCode = targetCode;
        this.senseNo = senseNo;
        this.confidence = confidence;
    }
}
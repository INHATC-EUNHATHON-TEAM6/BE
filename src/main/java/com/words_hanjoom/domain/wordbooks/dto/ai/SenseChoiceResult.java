package com.words_hanjoom.domain.wordbooks.dto.ai;

public record SenseChoiceResult(
        int idx,
        Long targetCode,
        Integer senseNo,
        String reason
) {}
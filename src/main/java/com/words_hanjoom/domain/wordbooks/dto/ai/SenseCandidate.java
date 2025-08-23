package com.words_hanjoom.domain.wordbooks.dto.ai;

public record SenseCandidate(
        int idx,
        String lemma,
        Long targetCode,
        Integer senseNo,
        String definition,
        String example,
        String categories
) {}
package com.words_hanjoom.domain.feedback.service;

import java.util.HashSet;
import java.util.Set;

public class SetSimilarityCalculator {
    /**
     * Jaccard 유사도 계산 (가장 일반적)
     * 공식: |A ∩ B| / |A ∪ B|
     * @param setA 첫 번째 단어 집합
     * @param setB 두 번째 단어 집합
     * @return Jaccard 유사도 (0.0 ~ 1.0)
     */
    public static double calculateJaccard(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0; // 둘 다 비어있으면 완전히 같음
        }

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB); // 교집합

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB); // 합집합

        return (double) intersection.size() / union.size();
    }
}

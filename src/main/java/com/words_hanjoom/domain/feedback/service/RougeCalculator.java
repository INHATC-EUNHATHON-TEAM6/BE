package com.words_hanjoom.domain.feedback.service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ROUGE (Recall-Oriented Understudy for Gisting Evaluation) 메트릭 계산 클래스
 * 텍스트 요약의 품질을 평가하는 지표
 */
public class RougeCalculator {

    /**
     * ROUGE 점수 결과를 담는 클래스
     */
    public static class RougeScore {
        private final double precision;
        private final double recall;
        private final double fMeasure;

        public RougeScore(double precision, double recall, double fMeasure) {
            this.precision = precision;
            this.recall = recall;
            this.fMeasure = fMeasure;
        }

        public double getPrecision() { return precision; }
        public double getRecall() { return recall; }
        public double getFMeasure() { return fMeasure; }

        @Override
        public String toString() {
            return String.format("ROUGE Score - Precision: %.4f, Recall: %.4f, F-Measure: %.4f",
                    precision, recall, fMeasure);
        }
    }

    /**
     * ROUGE-1 계산 (단어 단위 겹침)
     * @param reference 참조 텍스트 (정답)
     * @param candidate 후보 텍스트 (생성된 요약)
     * @return ROUGE-1 점수
     */
    public static RougeScore calculateRouge1(String reference, String candidate) {
        List<String> refTokens = tokenize(reference);
        List<String> candTokens = tokenize(candidate);

        return calculateRougeN(refTokens, candTokens, 1);
    }

    /**
     * ROUGE-2 계산 (바이그램 겹침)
     * @param reference 참조 텍스트
     * @param candidate 후보 텍스트
     * @return ROUGE-2 점수
     */
    public static RougeScore calculateRouge2(String reference, String candidate) {
        List<String> refTokens = tokenize(reference);
        List<String> candTokens = tokenize(candidate);

        return calculateRougeN(refTokens, candTokens, 2);
    }

    /**
     * ROUGE-N 계산 (N-gram 겹침)
     * @param refTokens 참조 토큰 리스트
     * @param candTokens 후보 토큰 리스트
     * @param n N-gram 크기
     * @return ROUGE-N 점수
     */
    private static RougeScore calculateRougeN(List<String> refTokens, List<String> candTokens, int n) {
        Set<String> refNgrams = getNgrams(refTokens, n);
        Set<String> candNgrams = getNgrams(candTokens, n);

        // 교집합 계산
        Set<String> intersection = new HashSet<>(refNgrams);
        intersection.retainAll(candNgrams);

        double precision = candNgrams.isEmpty() ? 0.0 : (double) intersection.size() / candNgrams.size();
        double recall = refNgrams.isEmpty() ? 0.0 : (double) intersection.size() / refNgrams.size();
        double fMeasure = (precision + recall == 0) ? 0.0 : (2 * precision * recall) / (precision + recall);

        return new RougeScore(precision, recall, fMeasure);
    }

    /**
     * ROUGE-L 계산 (최장 공통 부분 수열 기반)
     * @param reference 참조 텍스트
     * @param candidate 후보 텍스트
     * @return ROUGE-L 점수
     */
    public static RougeScore calculateRougeL(String reference, String candidate) {
        List<String> refTokens = tokenize(reference);
        List<String> candTokens = tokenize(candidate);

        int lcsLength = longestCommonSubsequence(refTokens, candTokens);

        double precision = candTokens.isEmpty() ? 0.0 : (double) lcsLength / candTokens.size();
        double recall = refTokens.isEmpty() ? 0.0 : (double) lcsLength / refTokens.size();
        double fMeasure = (precision + recall == 0) ? 0.0 : (2 * precision * recall) / (precision + recall);

        return new RougeScore(precision, recall, fMeasure);
    }

    /**
     * 모든 ROUGE 메트릭을 한 번에 계산
     * @param reference 참조 텍스트
     * @param candidate 후보 텍스트
     * @return ROUGE 점수 맵
     */
    public static Map<String, RougeScore> calculateAllRouge(String reference, String candidate) {
        Map<String, RougeScore> scores = new HashMap<>();
        scores.put("ROUGE-1", calculateRouge1(reference, candidate));
        scores.put("ROUGE-2", calculateRouge2(reference, candidate));
        scores.put("ROUGE-L", calculateRougeL(reference, candidate));
        return scores;
    }

    /**
     * 텍스트를 토큰화 (단어 단위)
     * @param text 입력 텍스트
     * @return 토큰 리스트
     */
    private static List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 간단한 토큰화: 공백과 구두점으로 분리
        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-zA-Z0-9가-힣\\s]", " ")  // 구두점 제거
                        .split("\\s+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * N-gram 생성
     * @param tokens 토큰 리스트
     * @param n N-gram 크기
     * @return N-gram 집합
     */
    private static Set<String> getNgrams(List<String> tokens, int n) {
        Set<String> ngrams = new HashSet<>();

        if (tokens.size() < n) {
            return ngrams;
        }

        for (int i = 0; i <= tokens.size() - n; i++) {
            StringBuilder ngram = new StringBuilder();
            for (int j = i; j < i + n; j++) {
                if (j > i) ngram.append(" ");
                ngram.append(tokens.get(j));
            }
            ngrams.add(ngram.toString());
        }

        return ngrams;
    }

    /**
     * 최장 공통 부분 수열 길이 계산 (동적 계획법)
     * @param seq1 첫 번째 시퀀스
     * @param seq2 두 번째 시퀀스
     * @return LCS 길이
     */
    private static int longestCommonSubsequence(List<String> seq1, List<String> seq2) {
        int m = seq1.size();
        int n = seq2.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (seq1.get(i - 1).equals(seq2.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }
}
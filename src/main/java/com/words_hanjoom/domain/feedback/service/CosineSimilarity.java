package com.words_hanjoom.domain.feedback.service;
import java.util.List;

/**
 * 임베딩 벡터 간의 코사인 유사도 계산 유틸리티 클래스
 */
public class CosineSimilarity {

    /**
     * 두 float 배열 간의 코사인 유사도 계산
     * @param vectorA 첫 번째 임베딩 벡터
     * @param vectorB 두 번째 임베딩 벡터
     * @return 코사인 유사도 (-1.0 ~ 1.0, 1.0에 가까울수록 유사)
     * @throws IllegalArgumentException 벡터 차원이 다르거나 null인 경우
     */
    public static double calculate(float[] vectorA, float[] vectorB) {
        validateVectors(vectorA, vectorB);

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        // 내적과 각 벡터의 크기 계산
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            magnitudeA += vectorA[i] * vectorA[i];
            magnitudeB += vectorB[i] * vectorB[i];
        }

        // 크기 계산 (루트)
        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);

        // 영벡터 처리
        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        // 코사인 유사도 = 내적 / (크기A * 크기B)
        return dotProduct / (magnitudeA * magnitudeB);
    }

    /**
     * 두 Double 리스트 간의 코사인 유사도 계산
     * @param vectorA 첫 번째 임베딩 벡터
     * @param vectorB 두 번째 임베딩 벡터
     * @return 코사인 유사도 (-1.0 ~ 1.0)
     */
    public static double calculate(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("벡터는 null일 수 없습니다");
        }
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("벡터의 차원이 다릅니다: " +
                    vectorA.size() + " vs " + vectorB.size());
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            double a = vectorA.get(i);
            double b = vectorB.get(i);

            dotProduct += a * b;
            magnitudeA += a * a;
            magnitudeB += b * b;
        }

        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);

        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        return dotProduct / (magnitudeA * magnitudeB);
    }

    /**
     * 유사도를 백분율로 변환 (0% ~ 100%)
     * @param cosineSimilarity 코사인 유사도 값
     * @return 백분율 유사도
     */
    public static double toPercentage(double cosineSimilarity) {
        // -1~1 범위를 0~100으로 변환
        return (cosineSimilarity + 1.0) * 50.0;
    }

    /**
     * 유사도 레벨 판정
     * @param cosineSimilarity 코사인 유사도 값
     * @return 유사도 레벨 문자열
     */
    public static String getSimilarityLevel(double cosineSimilarity) {
        if (cosineSimilarity >= 0.9) return "매우 유사";
        if (cosineSimilarity >= 0.7) return "유사";
        if (cosineSimilarity >= 0.5) return "보통";
        if (cosineSimilarity >= 0.3) return "약간 유사";
        return "유사하지 않음";
    }

    /**
     * 벡터 유효성 검사
     */
    private static void validateVectors(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            throw new IllegalArgumentException("벡터는 null일 수 없습니다");
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("벡터의 차원이 다릅니다: " +
                    vectorA.length + " vs " + vectorB.length);
        }
        if (vectorA.length == 0) {
            throw new IllegalArgumentException("벡터가 비어있습니다");
        }
    }
}
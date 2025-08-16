package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "scrap_activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnswerComparison {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "comparison_type",
            nullable = false,
            columnDefinition = "ENUM('CATEGORY','TITLE','SUMMARY','KEYWORD','UNKNOWN_WORD','THOUGHT_SUMMARY')"
    )
    private ComparisonType comparisonType;

    @Column(name = "user_answer", columnDefinition = "TEXT", nullable = false)
    private String userAnswer;

    @Column(name = "ai_answer", columnDefinition = "TEXT", nullable = false)
    private String aiAnswer;

    @Column(name = "ai_feedback", columnDefinition = "TEXT", nullable = false)
    private String aiFeedback;

    @Column(name = "evaluation_score", precision = 5, scale = 2)
    private BigDecimal evaluationScore;

    @CreationTimestamp
    @Column(name = "activity_at", nullable = false, updatable = false)
    private LocalDateTime activityAt;

    public enum ComparisonType {
        CATEGORY, TITLE, SUMMARY, KEYWORD, UNKNOWN_WORD, THOUGHT_SUMMARY
    }
}
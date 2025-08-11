package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity @Table(name = "answer_comparisons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnswerComparison {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comparison_id")
    private Long comparisonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", nullable = false, columnDefinition = "ENUM('CATEGORY','TITLE','SUMMARY','KEYWORD','UNKNOWN_WORD','THOUGHT_SUMMARY')")
    private ComparisonType comparisonType;

    @Column(name = "user_content", columnDefinition = "TEXT", nullable = false)
    private String userContent;

    @Column(name = "target_content", columnDefinition = "TEXT", nullable = false)
    private String targetContent;

    @Column(name = "match_score", precision = 5, scale = 2)
    private Double matchScore;

    @Column(name = "activity_at", nullable = false)
    private LocalDateTime activityAt;

    public enum ComparisonType {
        CATEGORY, TITLE, SUMMARY, KEYWORD, UNKNOWN_WORD, THOUGHT_SUMMARY
    }
}
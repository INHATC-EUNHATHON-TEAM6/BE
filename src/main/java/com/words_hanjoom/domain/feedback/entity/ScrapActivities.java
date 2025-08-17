package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer_comparisons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerComparisons {
    private enum ActivityType {
        TITLE,
        SUMMARY,
        CATEGORY,
        KEYWORD
    }
    @Id
    @Column(name = "comparison_id")
    private Long comparisonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('TITLE', 'SUMMARY', 'CATEGORY', 'KEYWORD')", nullable = false)
    private ActivityType comparisonType;

    @Column(name = "user_content", nullable = false)
    private String userContent;

    @Column(name = "target_content", nullable = false)
    private String targetContent;

    @Column(name = "match_score", precision = 5, scale = 2, nullable = true)
    private BigDecimal matchScore;

    @Column(name = "activity_at", nullable = false)
    private Instant activityAt;
}

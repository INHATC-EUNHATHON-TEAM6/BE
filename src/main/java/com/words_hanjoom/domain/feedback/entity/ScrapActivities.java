package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scrap_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrapActivities {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_type", columnDefinition = "ENUM('TITLE', 'SUMMARY', 'CATEGORY', 'KEYWORD')", nullable = false)
    private ActivityType comparisonType;

    @Column(name = "user_answer", nullable = false)
    private String userAnswer;

    @Column(name = "ai_answer", nullable = false)
    private String aiAnswer;

    @Column(name = "ai_feedback", nullable = false)
    private String aiFeedback;

    @Column(name = "evaluation_score", nullable = false)
    private String evaluationScore;

    @Column(name = "activity_at", nullable = false)
    private LocalDateTime activityAt;
}

package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scrap_activities")
@ToString(callSuper=false)
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

    public ScrapActivities() {}

    public ScrapActivities(Long scrapId, Long userId, Long articleId, ActivityType comparisonType, String userAnswer, String aiAnswer, String aiFeedback, String evaluationScore, LocalDateTime activityAt) {
        this.scrapId = scrapId;
        this.userId = userId;
        this.articleId = articleId;
        this.comparisonType = comparisonType;
        this.userAnswer = userAnswer;
        this.aiAnswer = aiAnswer;
        this.aiFeedback = aiFeedback;
        this.evaluationScore = evaluationScore;
        this.activityAt = activityAt;
    }

    public Long getScrapId() {
        return scrapId;
    }

    public void setScrapId(Long scrapId) {
        this.scrapId = scrapId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public ActivityType getComparisonType() {
        return comparisonType;
    }

    public void setComparisonType(ActivityType comparisonType) {
        this.comparisonType = comparisonType;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public String getAiAnswer() {
        return aiAnswer;
    }

    public void setAiAnswer(String aiAnswer) {
        this.aiAnswer = aiAnswer;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public String getEvaluationScore() {
        return evaluationScore;
    }

    public void setEvaluationScore(String evaluationScore) {
        this.evaluationScore = evaluationScore;
    }

    public LocalDateTime getActivityAt() {
        return activityAt;
    }

    public void setActivityAt(LocalDateTime activityAt) {
        this.activityAt = activityAt;
    }
}

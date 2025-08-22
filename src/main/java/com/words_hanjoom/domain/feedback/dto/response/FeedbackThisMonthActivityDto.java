package com.words_hanjoom.domain.feedback.dto.response;

import java.time.LocalDateTime;

public class FeedbackThisMonthActivityDto {
    private long articleId;
    private String category;
    private LocalDateTime activityAt;

    public FeedbackThisMonthActivityDto(long articleId, LocalDateTime activityAt) {
        this.articleId = articleId;
        this.activityAt = activityAt;
    }

    public FeedbackThisMonthActivityDto(long articleId, String category, LocalDateTime activityAt) {
        this.articleId = articleId;
        this.category = category;
        this.activityAt = activityAt;
    }

    public long getArticleId() {
        return articleId;
    }

    public void setArticleId(long articleId) {
        this.articleId = articleId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getActivityAt() {
        return activityAt;
    }

    public void setActivityAt(LocalDateTime activityAt) {
        this.activityAt = activityAt;
    }

    @Override
    public String toString() {
        return "FeedbackThisMonthActivityDto{" +
                "articleId=" + articleId +
                ", category='" + category + '\'' +
                ", activityAt=" + activityAt +
                '}';
    }
}

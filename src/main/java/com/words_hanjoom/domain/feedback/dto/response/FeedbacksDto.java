package com.words_hanjoom.domain.feedback.dto.response;

import java.util.List;

public class FeedbacksDto {
    private String articleBody;
    private String categoryName;
    private List<FeedbackDto> feedbacks;

    public FeedbacksDto() {}

    public FeedbacksDto(String articleBody, String categoryName, List<FeedbackDto> feedbacks) {
        this.articleBody = articleBody;
        this.categoryName = categoryName;
        this.feedbacks = feedbacks;
    }

    public String getArticleBody() {
        return articleBody;
    }

    public void setArticleBody(String articleBody) {
        this.articleBody = articleBody;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<FeedbackDto> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<FeedbackDto> feedbacks) {
        this.feedbacks = feedbacks;
    }
}

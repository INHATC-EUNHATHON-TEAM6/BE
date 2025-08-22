package com.words_hanjoom.domain.feedback.dto.response;

import java.util.List;

public class FeedbacksDto {
    private String articleBody;
    private List<FeedbackDto> feedbacks;

    public FeedbacksDto() {}

    public FeedbacksDto(String articleBody, List<FeedbackDto> feedbacks) {
        this.articleBody = articleBody;
        this.feedbacks = feedbacks;
    }

    public String getArticleBody() {
        return articleBody;
    }

    public void setArticleBody(String articleBody) {
        this.articleBody = articleBody;
    }

    public List<FeedbackDto> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(List<FeedbackDto> feedbacks) {
        this.feedbacks = feedbacks;
    }
}

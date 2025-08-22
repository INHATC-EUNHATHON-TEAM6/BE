package com.words_hanjoom.domain.feedback.dto.response;

import com.words_hanjoom.domain.feedback.entity.ActivityType;

public class FeedbackDto {
    private ActivityType activityType;
    private String userAnswer;
    private String aiAnswer;
    private String aiFeedback;
    private String evaluationScore;

    public FeedbackDto(ActivityType activityType, String userAnswer, String aiAnswer, String aiFeedback, String evaluationScore) {
        this.activityType = activityType;
        this.userAnswer = userAnswer;
        this.aiAnswer = aiAnswer;
        this.aiFeedback = aiFeedback;
        this.evaluationScore = evaluationScore;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
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
}

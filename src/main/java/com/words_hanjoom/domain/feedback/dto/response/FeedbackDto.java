package com.words_hanjoom.domain.feedback.dto.response;

import com.words_hanjoom.domain.feedback.entity.ActivityType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
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
}

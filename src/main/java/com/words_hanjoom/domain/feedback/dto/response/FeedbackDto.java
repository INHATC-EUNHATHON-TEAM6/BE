package com.words_hanjoom.domain.feedback.dto.response;

import java.util.List;
import java.util.Map;

public class FeedbackDto {
    private String predTitle;
    private String targetTitle;
    private String predSummary;
    private String targetSummary;
    private String predCategory;
    private String targetCategory;
    private List<String> predKeywords;
    private List<String> targetKeywords;
    private List<String> vocabularies;
    private String comment;
    private Map<String, Number> score;
    private Map<String, String> feedbacks;

    public FeedbackDto(String predTitle, String targetTitle, String predSummary, String targetSummary, String predCategory, String targetCategory, List<String> predKeywords, List<String> targetKeywords, List<String> vocabularies, String comment, Map<String, Number> score, Map<String, String> feedbacks) {
        this.predTitle = predTitle;
        this.targetTitle = targetTitle;
        this.predSummary = predSummary;
        this.targetSummary = targetSummary;
        this.predCategory = predCategory;
        this.targetCategory = targetCategory;
        this.predKeywords = predKeywords;
        this.targetKeywords = targetKeywords;
        this.vocabularies = vocabularies;
        this.comment = comment;
        this.score = score;
        this.feedbacks = feedbacks;
    }
}

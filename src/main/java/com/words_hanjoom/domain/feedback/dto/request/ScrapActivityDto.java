package com.words_hanjoom.domain.feedback.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ScrapActivityDto {
    private String title;
    private String summary;
    private String category;
    private List<String> keywords;
    private List<String> vocabularies;
    private String comment;

    @JsonCreator
    public ScrapActivityDto(@JsonProperty("title") String title,
                            @JsonProperty("summary") String summary,
                            @JsonProperty("category") String category,
                            @JsonProperty("keywords") List<String> keywords,
                            @JsonProperty("vocabularies") List<String> vocabularies,
                            @JsonProperty("comment") String comment) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.keywords = keywords;
        this.vocabularies = vocabularies;
        this.comment = comment;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getVocabularies() {
        return vocabularies;
    }

    public String getComment() {
        return comment;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public void setVocabularies(List<String> vocabularies) {
        this.vocabularies = vocabularies;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

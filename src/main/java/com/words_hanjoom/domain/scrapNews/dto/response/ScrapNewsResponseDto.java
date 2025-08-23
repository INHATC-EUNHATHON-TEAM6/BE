package com.words_hanjoom.domain.scrapNews.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ScrapNewsResponseDto {
    // 어떤 정보를 사용할지 모르니 일단 다 보냄
    private Long articleId;
    private Long categoryId;
    private String title;
    private String content;
    private String publishedAt;
    private String reporterName;
    private String publisher;
    private String articleUrl;
    private String createdAt;

    public ScrapNewsResponseDto() {}

    @Builder
    public ScrapNewsResponseDto(Long articleId, Long categoryId, String title, String content,
                                String publishedAt, String reporterName, String publisher,
                                String articleUrl, String createdAt) {
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.reporterName = reporterName;
        this.publisher = publisher;
        this.articleUrl = articleUrl;
        this.createdAt = createdAt;
    }
}

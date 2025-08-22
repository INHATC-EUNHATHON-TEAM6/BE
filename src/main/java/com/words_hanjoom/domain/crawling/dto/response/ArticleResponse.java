package com.words_hanjoom.domain.crawling.dto.response;

import com.words_hanjoom.domain.crawling.entity.Article;

import java.time.LocalDateTime;

public record ArticleResponse(
        Long article_id,
        Long category_id,
        String title,
        String body,
        String publishedAt,
        String publisher,
        String reporter,
        String url
) {
    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getArticleId(),
                article.getCategoryId(),
                article.getTitle(),
                article.getContent(),
                article.getPublishedAt(),
                article.getPublisher(),
                article.getReporterName(),
                article.getArticleUrl()
        );
    }
}

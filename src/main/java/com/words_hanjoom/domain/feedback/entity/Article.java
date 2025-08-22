package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "articles")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;       // PK
    @Column(name = "category_id", nullable = false)
    private Long categoryId;   // FK -> 카테고리 테이블(ID 참조)

    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "content", nullable = false)
    private String content;       // 본문
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;   // 작성일자(문자열/ISO; DB 타입은 추후 LocalDateTime 권장)
    @Column(name = "reporter_name", nullable = false)
    private String reporterName;  // 기자명
    @Column(name = "publisher", nullable = false)
    private String publisher;     // 신문사
    @Column(name = "article_url", nullable = false)
    private String articleUrl;    // 기사링크

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;    // 생성시각
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;    // 삭제시각(소프트 삭제용) - null이면 유효

    public Article() {}

    public Article(Long categoryId, String title, String content,
                   LocalDateTime publishedAt, String reporterName, String publisher,
                   String articleUrl, LocalDateTime createAt) {
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.reporterName = reporterName;
        this.publisher = publisher;
        this.articleUrl = articleUrl;
        this.createdAt = createAt;
    }

    public Article(Long articleId, Long categoryId, String title, String content,
                   LocalDateTime publishedAt, String reporterName, String publisher,
                   String articleUrl, LocalDateTime createAt) {
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.reporterName = reporterName;
        this.publisher = publisher;
        this.articleUrl = articleUrl;
        this.createdAt = createAt;
    }

    @Override public String toString() {
        return "Article{" +
                "articleId=" + articleId +
                ", categoryId=" + categoryId +
                ", title='" + title + '\'' +
                ", publishedAt='" + publishedAt + '\'' +
                ", reporterName='" + reporterName + '\'' +
                ", publisher='" + publisher + '\'' +
                ", articleUrl='" + articleUrl + '\'' +
                '}';
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getArticleUrl() {
        return articleUrl;
    }

    public void setArticleUrl(String articleUrl) {
        this.articleUrl = articleUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

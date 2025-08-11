package com.words_hanjoom.domain.crawling.entity;


import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/**
 * DB 스키마에 맞춘 엔티티
 * article_id(PK), category_id(FK), title, content, published_at, reporter_name,
 * publisher, article_url, created_at, deleted_at
 */
@Entity
@Table(name = "articles")
@Getter
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // InMemory DB에서 자동 증가
    @Column(name = "article_id")
    private Long articleId;       // PK (임시: InMemory에서 자동 증가)
    @Column(name = "category_id")
    private Integer categoryId;   // FK -> 카테고리 테이블(ID 참조)

    @Column(name = "title")
    private String title;
    @Column(name = "contetn")
    private String content;       // 본문
    @Column(name = "published_at")
    private String publishedAt;   // 작성일자(문자열/ISO; DB 타입은 추후 LocalDateTime 권장)
    @Column(name = "reporter_name")
    private String reporterName;  // 기자명
    @Column(name = "publisher")
    private String publisher;     // 신문사
    @Column(name = "article_url")
    private String articleUrl;    // 기사링크

    @Column(name = "created_at")
    private Instant createdAt;    // 생성시각
    @Column(name = "deleted_at")
    private Instant deletedAt;    // 삭제시각(소프트 삭제용) - null이면 유효

    public Article() {
    }

    public Article(Long articleId, Integer categoryId, String title, String content,
                   String publishedAt, String reporterName, String publisher,
                   String articleUrl, Instant createdAt, Instant deletedAt) {
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt;
        this.reporterName = reporterName;
        this.publisher = publisher;
        this.articleUrl = articleUrl;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    // setters
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setArticleUrl(String articleUrl) { this.articleUrl = articleUrl; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

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
}

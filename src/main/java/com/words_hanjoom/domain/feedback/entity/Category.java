package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name="categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="category_id")
    private Long categoryId;

    @Column(name="category_name", nullable = false)
    private String categoryName;

    @Column(name="description", nullable = true)
    private String description;

    @CreationTimestamp
    @Column(name="created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name="deleted_at", nullable = true)
    private String deletedAt;

    public Category() {}

    public Category(Long id) {
        this.categoryId = id;
        switch (id.intValue()) {
            case 1 -> this.categoryName = "기초•응용과학";
            case 2 -> this.categoryName = "신소재•신기술";
            case 3 -> this.categoryName = "생명과학•의학";
            case 4 -> this.categoryName = "항공•우주";
            case 5 -> this.categoryName = "환경•에너지";
            case 6 -> this.categoryName = "경제";
            case 7 -> this.categoryName = "고용복지";
            case 8 -> this.categoryName = "복지";
            case 9 -> this.categoryName = "산업";
            case 10 -> this.categoryName = "사회";
            case 11 -> this.categoryName = "문화";
        }
        switch (id.intValue()) {
            case 1 -> this.description = "기초과학 및 응용과학 분야";
            case 2 -> this.description = "신소재 개발과 첨단 신기술 분야";
            case 3 -> this.description = "생명과학 연구 및 의학 관련 분야";
            case 4 -> this.description = "항공공학과 우주과학 분야";
            case 5 -> this.description = "환경보호와 에너지 기술 분야";
            case 6 -> this.description = "경제 및 금융 시장 연구 분야";
            case 7 -> this.description = "고용정책과 복지 제도 분야";
            case 8 -> this.description = "금융산업 및 투자 분야";
            case 9 -> this.description = "산업 기술과 제조업 분야";
            case 10 -> this.description = "사회학, 인문사회 연구 분야";
            case 11 -> this.description = "문화예술 및 콘텐츠 분야";
        }
        this.createdAt = LocalDateTime.now();
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }
}

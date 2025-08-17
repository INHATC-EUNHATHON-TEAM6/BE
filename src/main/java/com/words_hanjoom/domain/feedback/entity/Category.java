package com.words_hanjoom.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="category_id")
    private Long categoryId;
    @Column(name="category_name", nullable = false)
    private String categoryName;

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
    }
}

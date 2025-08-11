package com.words_hanjoom.domain.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserCategory {
    @EmbeddedId
    private UserCategoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public static UserCategory of(User u, Category c) {
        return UserCategory.builder()
                .id(new UserCategoryId(u.getUserId(), c.getCategoryId()))
                .user(u)
                .category(c)
                .build();
    }
}
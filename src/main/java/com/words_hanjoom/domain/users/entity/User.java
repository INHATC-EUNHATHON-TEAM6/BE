package com.words_hanjoom.domain.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 255, nullable = false, unique = true) // ✅ 로그인 아이디(이메일) 컬럼
    private String loginId;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 20, nullable = false)
    private String name;

    @Column(length = 20, nullable = false, unique = true)
    private String nickname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "career_goal", nullable = false)
    private String careerGoal;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @Column(length = 20, nullable = false)
    private String status = "ACTIVE"; // 기본값 설정

    // 연관관계(읽기 전용 컬렉션)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserCategory> userCategories = new HashSet<>();

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    // 편의 메서드
    public void addCategory(Category category) {
        UserCategory uc = UserCategory.of(this, category);
        this.userCategories.add(uc);
    }
}
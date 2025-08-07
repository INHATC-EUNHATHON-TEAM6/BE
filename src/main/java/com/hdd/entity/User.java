package com.hdd.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "login_id", length = 30, nullable = false, unique = true)
    private String loginId;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 20, nullable = false)
    private String name;

    @Column(length = 20, nullable = false, unique = true)
    private String nickname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(length = 20, nullable = false)
    private String status = "ACTIVE"; // 기본값 설정

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
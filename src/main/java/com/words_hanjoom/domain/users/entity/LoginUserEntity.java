package com.hdd.words_hanjoom.domain.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="users")
// 로그인 시 사용되는 객체
public class LoginUserEntity {
    // 로그인 객체 순서로 저장되는 고유값 userId
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    // 로그인 아이디와 비밀번호
    @Column(unique = true)
    private String loginId;

    private String password;
}

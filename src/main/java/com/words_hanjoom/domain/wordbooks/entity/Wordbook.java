package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// com.words_hanjoom.domain.wordbooks.entity.Wordbook
@Entity
@Table(name = "wordbooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // ← 클래스 레벨에 붙이면 모든 필드가 builder에 포함됨
public class Wordbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wordbook_id")
    private Long wordbookId;

    @Column(name = "user_id", nullable = false, unique = true) // 유저당 1개라면 unique=true
    private Long userId;

    public Long getUserId() { return userId; }

}

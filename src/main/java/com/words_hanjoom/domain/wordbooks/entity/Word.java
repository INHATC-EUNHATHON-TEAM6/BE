package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "words",
        uniqueConstraints = @UniqueConstraint(name = "uq_words_target_sense",
                columnNames = { "target_code", "sense_no" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_id")
    private Long wordId;

    @Column(name = "target_code", nullable = false)
    private Long targetCode;     // NIKL item.target_code

    @Column(name = "sense_no", nullable = false)
    private Short senseNo;       // view.item.sense[n] 의 n(1..N)

    @Column(name = "word_name", length = 100, nullable = false)
    private String wordName;

    @Column(name = "synonym", length = 100, nullable = false)
    private String synonym;

    @Column(name = "antonym", length = 100, nullable = false)
    private String antonym;

    @Lob
    @Column(name = "definition", columnDefinition = "TEXT", nullable = false)
    private String definition;

    @Column(name = "word_category", length = 30, nullable = false)
    private String wordCategory;

    @Column(name = "shoulder_no", nullable = false)
    private Byte shoulderNo;

    @Lob
    @Column(name = "example", columnDefinition = "TEXT", nullable = false)
    private String example;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
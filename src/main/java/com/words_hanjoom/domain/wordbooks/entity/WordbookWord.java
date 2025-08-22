package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity @Table(name = "wordbook_words")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WordbookWord {

    @EmbeddedId
    private WordbookWordId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

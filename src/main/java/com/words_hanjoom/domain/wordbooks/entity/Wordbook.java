// src/main/java/com/words_hanjoom/domain/wordbook/Wordbook.java
package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wordbooks", indexes = {@Index(name="ix_wordbooks_user", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wordbook {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wordbook_id")
    private Long wordbookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}

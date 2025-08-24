package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class WordbookWordId implements Serializable {
    @Column(name = "wordbook_id", nullable = false)
    private Long wordbookId;

    @Column(name = "word_id", nullable = false)
    private Long wordId;
}
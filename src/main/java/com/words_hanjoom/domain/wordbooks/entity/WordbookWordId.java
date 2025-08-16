package com.words_hanjoom.domain.wordbooks.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WordbookWordId implements Serializable {
    private Long wordbookId;
    private Long wordId;
}
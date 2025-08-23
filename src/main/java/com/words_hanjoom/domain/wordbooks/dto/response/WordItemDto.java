package com.words_hanjoom.domain.wordbooks.dto.response;

import java.time.LocalDateTime;
import lombok.*;

@Getter @AllArgsConstructor
public class WordItemDto {
    private Long wordId;
    private String wordName;
    private String definition;
    private String example;
    private String wordCategory;
    private Long targetCode;
    private Integer senseNo;
    private LocalDateTime savedAt; // 단어장에 담긴 시각(WordbookWord.createdAt)
}
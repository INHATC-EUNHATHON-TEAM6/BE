package com.words_hanjoom.domain.wordbooks.dto.response;

import java.time.LocalDateTime;
import lombok.*;

@Getter @AllArgsConstructor
public class WordItemDto {
    private Long wordId; // 단어Id
    private String wordName; // 단어명
    private String definition; // 정의
    private String example; // 예문
    private String wordCategory; // 분야
    private String synonym; // 유의어
    private String antonym; // 반의어
    private Byte shoulderNo; // 어깨번호
}


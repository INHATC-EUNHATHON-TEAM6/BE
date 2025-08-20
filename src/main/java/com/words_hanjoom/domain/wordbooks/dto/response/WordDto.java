package com.words_hanjoom.domain.wordbooks.dto.response;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import java.util.regex.Pattern;

public record WordDto(
        Long   wordId,
        String wordName,     // 원문: 예) "간담-회"
        String displayName,  // 화면용: 예) "간담회"
        String definition,
        String example,
        String wordCategory,
        Byte   shoulderNo,
        Long   targetCode,
        Short  senseNo
) {
    private static final Pattern STD_MARKS = Pattern.compile("[-·ㆍ‐–—]");

    private static String toDisplay(String s) {
        return (s == null) ? "" : STD_MARKS.matcher(s).replaceAll("");
    }

    public static WordDto from(Word e) {
        return new WordDto(
                e.getWordId(),
                e.getWordName(),
                toDisplay(e.getWordName()),
                e.getDefinition(),
                e.getExample(),
                e.getWordCategory(),
                e.getShoulderNo(),
                e.getTargetCode(),
                e.getSenseNo()
        );
    }
}
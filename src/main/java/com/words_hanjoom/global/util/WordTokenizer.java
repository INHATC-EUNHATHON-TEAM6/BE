package com.words_hanjoom.global.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class WordTokenizer {
    private WordTokenizer() {}

    // 구분자: , ; / ／ · |
    private static final Pattern SEP = Pattern.compile("[,;／/·|]+");
    // 괄호 안 내용 제거: 배(과일) -> 배
    private static final Pattern PAREN = Pattern.compile("\\(.*?\\)");

    public static List<String> tokenizeUnknownWords(String raw) {
        if (raw == null) return List.of();
        raw = PAREN.matcher(raw).replaceAll("");
        return Arrays.stream(SEP.split(raw))
                .map(s -> s.replaceAll("\\s+", "").trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }
}
package com.words_hanjoom.domain.wordbooks.dto.request;

import java.util.Optional;

public record SearchRequest(
        String q,
        String reqType,
        Integer start,
        Integer num,
        String advanced,
        Optional<Integer> target,
        Optional<String> method,
        Optional<String> type1,
        Optional<String> type2,
        Optional<String> pos,
        Optional<String> cat,
        Optional<String> multimedia,
        Optional<Integer> letterS,
        Optional<Integer> letterE,
        Optional<Integer> updateS,
        Optional<Integer> updateE
) {
    public static SearchRequest basic(String q) {
        return new SearchRequest(
                q, "json", 1, 10, "n",
                Optional.empty(),  // target
                Optional.empty(),  // method
                Optional.empty(),  // type1
                Optional.empty(),  // type2
                Optional.empty(),  // pos
                Optional.empty(),  // cat
                Optional.empty(),  // multimedia
                Optional.empty(),  // letterS
                Optional.empty(),  // letterE
                Optional.empty(),  // updateS
                Optional.empty()   // updateE
        );
    }
}
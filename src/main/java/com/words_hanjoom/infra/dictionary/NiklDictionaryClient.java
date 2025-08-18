package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface NiklDictionaryClient {

    // reactive
    Mono<SearchResponse> search(SearchRequest req);
    Mono<ViewResponse> view(long targetCode);

    // blocking facade (기존 서비스 호환용)
    Optional<String> findLemma(String surface);
    Optional<Lexeme> lookup(String lemma);

    // UnknownWordService에서 쓰는 DTO (List 기반으로 교정)
    final class Lexeme {
        private final String word;
        private final List<String> synonyms;
        private final List<String> antonyms;
        private final String definition;
        private final List<String> categories;
        private final List<String> example;
        private final byte shoulderNo;

        public Lexeme(String word,
                      List<String> synonyms,
                      List<String> antonyms,
                      String definition,
                      List<String> categories,
                      List<String> example,
                      byte shoulderNo) {
            this.word = word;
            this.synonyms = synonyms;
            this.antonyms = antonyms;
            this.definition = definition;
            this.categories = categories;
            this.example = example;
            this.shoulderNo = shoulderNo;
        }

        public String word() { return word; }
        public List<String> synonyms() { return synonyms; }
        public List<String> antonyms() { return antonyms; }
        public String definition() { return definition; }
        public List<String> categories() { return categories; }
        public List<String> example() { return example; }
        public byte shoulderNo() { return shoulderNo; }
    }

    static byte parseByteOrZero(String s) {
        try { return (byte) Integer.parseInt(Objects.toString(s, "0")); }
        catch (Exception ignore) { return 0; }
    }
}

